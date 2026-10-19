package com.noxxx.guard.vision

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import com.noxxx.guard.policy.Sensitivity
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * [top] is the tile with the highest explicit score (the one that flagged, if any), at [tile] of
 * [tiles]. [secureCheck] says how a blank/secure screen was detected, for the diagnostics log.
 */
data class ScanResult(
    val flagged: Boolean,
    val blank: Boolean,
    val top: Scores? = null,
    val tile: Int = 0,
    val tiles: Int = 0,
    val secureCheck: String? = null,
)

/**
 * Takes a screenshot through the accessibility service (Android 11+), shrinks it, and classifies
 * overlapping square tiles so tall phone screens aren't distorted. Everything stays on-device.
 */
@RequiresApi(Build.VERSION_CODES.R)
class ScreenScanner(private val service: AccessibilityService) {
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private var classifier: ImageClassifier? = null

    fun scan(sensitivity: Sensitivity, onResult: (ScanResult) -> Unit) {
        if (!busy.compareAndSet(false, true)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            appWindowId()?.let { return scanWindow(it, sensitivity, onResult) }
        }
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : AccessibilityService.TakeScreenshotCallback {
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
            service.takeScreenshotOfWindow(windowId, executor, object : AccessibilityService.TakeScreenshotCallback {
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
        val model = classifier ?: ImageClassifier(service).also {
            classifier = it
            Log.i(TAG, "image model loaded (${it.inputWidth}x${it.inputHeight})")
        }
        val side = model.inputWidth
        try {
            // Scale so the short edge matches the model input, then walk tiles along the long edge.
            val scale = side.toFloat() / minOf(software.width, software.height)
            val w = maxOf(side, (software.width * scale).roundToInt())
            val h = maxOf(side, (software.height * scale).roundToInt())
            val small = Bitmap.createScaledBitmap(software, w, h, true)
            try {
                if (guessBlank && isBlank(small)) {
                    return ScanResult(flagged = false, blank = true, secureCheck = "Screen was almost all black (pre-Android 14 check)")
                }
                val pixels = IntArray(side * side)
                val origins = tileOrigins(w, h, side)
                var top: Scores? = null
                var topTile = 0
                for ((i, origin) in origins.withIndex()) {
                    small.getPixels(pixels, 0, side, origin.first, origin.second, side, side)
                    val s = model.classify(pixels)
                    if (top == null || s.explicit > top.explicit) {
                        top = s
                        topTile = i
                    }
                    if (s.isFlagged(sensitivity)) return ScanResult(true, false, s, i + 1, origins.size)
                }
                return ScanResult(false, false, top, topTile + 1, origins.size)
            } finally {
                if (small !== software) small.recycle()
            }
        } finally {
            software.recycle()
        }
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
                x += 8
            }
            y += 8
        }
        return total > 0 && dark >= total * 0.98
    }

    fun close() {
        executor.execute { classifier?.close(); classifier = null }
        executor.shutdown()
    }

    private companion object {
        const val TAG = "ScreenScanner"

        fun tileOrigins(w: Int, h: Int, side: Int): List<Pair<Int, Int>> {
            val vertical = h >= w
            val long = if (vertical) h else w
            val count = ceil(long.toFloat() / side).toInt().coerceAtLeast(1)
            val step = if (count == 1) 0 else (long - side) / (count - 1)
            return (0 until count).map { i -> if (vertical) 0 to i * step else i * step to 0 }
        }
    }
}
