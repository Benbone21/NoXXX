package com.noxxx.guard.vision

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import com.noxxx.guard.BuildConfig
import com.noxxx.guard.data.DetectionArchive
import com.noxxx.guard.policy.Sensitivity
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [top] is what the detector found on the screen (whether or not it flagged). [secureCheck] says how a
 * blank/secure screen was detected, for the diagnostics log. [topPixels] (dev builds only, and only for
 * scans worth keeping) is the screen exactly as the model saw it, ARGB at [side] x [side], for
 * [DetectionArchive].
 */
data class ScanResult(
    val flagged: Boolean,
    val blank: Boolean,
    val top: Scores? = null,
    val secureCheck: String? = null,
    val topPixels: IntArray? = null,
    val side: Int = 0,
)

/**
 * Takes a screenshot through the accessibility service (Android 11+), shrinks the whole screen into the
 * detector's square input (letterboxed, so nothing is distorted or cut in half) and runs it once.
 * Everything stays on-device.
 */
@RequiresApi(Build.VERSION_CODES.R)
class ScreenScanner(private val service: AccessibilityService) {
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Where Android delivers screenshot results. A screenshot can still be in flight when the service
     * stops; delivering it to the shut-down [executor] would throw on the main thread and crash the app,
     * so late results are dropped instead.
     */
    private val callbacks = Executor { task -> if (!executor.isShutdown) runCatching { executor.execute(task) } }
    private val busy = AtomicBoolean(false)
    private var detector: NudeDetector? = null

    fun scan(sensitivity: Sensitivity, onResult: (ScanResult) -> Unit) {
        if (!busy.compareAndSet(false, true)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            appWindowId()?.let { return scanWindow(it, sensitivity, onResult) }
        }
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, callbacks, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    try {
                        analyze(result, sensitivity, guessBlank = true)?.let(onResult)
                    } catch (e: Throwable) {
                        Log.w(TAG, "analysis failed", e)
                    } finally {
                        busy.set(false)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    busy.set(false)
                }
            })
        } catch (e: Throwable) {
            busy.set(false)
        }
    }

    private fun appWindowId(): Int? = runCatching {
        service.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }?.id
    }.getOrNull()

    /**
     * Android 14+: screenshots only the app's own window. Android refuses only when that window really
     * is secure (FLAG_SECURE), so dark apps like a black audiobook player are no longer mistaken for one.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun scanWindow(windowId: Int, sensitivity: Sensitivity, onResult: (ScanResult) -> Unit) {
        try {
            service.takeScreenshotOfWindow(windowId, callbacks, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    try {
                        analyze(result, sensitivity, guessBlank = false)?.let(onResult)
                    } catch (e: Throwable) {
                        Log.w(TAG, "analysis failed", e)
                    } finally {
                        busy.set(false)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) {
                        onResult(ScanResult(flagged = false, blank = true, secureCheck = "Android reported a secure window"))
                    }
                    busy.set(false)
                }
            })
        } catch (e: Throwable) {
            busy.set(false)
        }
    }

    /** [guessBlank]: treat an almost all-black screen as secure (before Android 14 there's no direct check). */
    private fun analyze(result: AccessibilityService.ScreenshotResult, sensitivity: Sensitivity, guessBlank: Boolean): ScanResult? {
        val buffer = result.hardwareBuffer
        val software = try {
            val hw = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: return null
            hw.copy(Bitmap.Config.ARGB_8888, false).also { hw.recycle() }
        } finally {
            buffer.close()
        }
        val model = detector ?: NudeDetector(service).also {
            detector = it
            Log.i(TAG, "image model loaded (${it.side}x${it.side})")
        }
        val side = model.side
        try {
            // One pass over the whole screen: a detector finds small parts anywhere in its input, so
            // tiles would only cost more inferences and split bodies across tile edges.
            val (w, h) = Letterbox.fit(software.width, software.height, side)
            val small = shrink(software, w, h)
            try {
                if (guessBlank && isBlank(small)) {
                    return ScanResult(flagged = false, blank = true, secureCheck = "Screen was almost all black (pre-Android 14 check)")
                }
                // Top-left on black, like NudeNet's own preprocessing; the rest of the square stays 0 (black).
                val pixels = IntArray(side * side)
                small.getPixels(pixels, 0, side, 0, 0, w, h)
                val s = model.detect(pixels)
                // Only dev builds hand the pixels on; release builds compile this away.
                val keep = BuildConfig.DEBUG && DetectionArchive.worthKeeping(s, sensitivity)
                return ScanResult(s.isFlagged(sensitivity), false, s, topPixels = if (keep) pixels else null, side = side)
            } finally {
                if (small !== software) small.recycle()
            }
        } finally {
            software.recycle()
        }
    }

    /**
     * Halves the screenshot until it's within 2x of the target, then scales the rest of the way. A single
     * bilinear step from a 2400 px screen down to 320 skips most pixels and turns fine detail into noise.
     */
    private fun shrink(src: Bitmap, w: Int, h: Int): Bitmap {
        var cur = src
        while (cur.width > w * 2 && cur.height > h * 2) {
            val next = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            if (cur !== src) cur.recycle()
            cur = next
        }
        if (cur.width == w && cur.height == h) return cur
        return Bitmap.createScaledBitmap(cur, w, h, true).also { if (cur !== src) cur.recycle() }
    }

    /** Secure windows (e.g. private tabs) come back solid black. Ignores the top/bottom bars. */
    private fun isBlank(bmp: Bitmap): Boolean {
        val top = bmp.height / 10
        val bottom = bmp.height - bmp.height / 10
        var dark = 0
        var total = 0
        var y = top
        while (y < bottom) {
            var x = 0
            while (x < bmp.width) {
                val px = bmp.getPixel(x, y)
                if (((px shr 16) and 0xFF) < 10 && ((px shr 8) and 0xFF) < 10 && (px and 0xFF) < 10) dark++
                total++
                x += 4
            }
            y += 4
        }
        return total > 0 && dark >= total * 0.98
    }

    fun close() {
        executor.execute { detector?.close(); detector = null }
        executor.shutdown()
    }

    private companion object {
        const val TAG = "ScreenScanner"
    }
}
