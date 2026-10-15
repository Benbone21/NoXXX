package com.noxxx.guard.monitor

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.noxxx.guard.R

/**
 * Opaque cover placed over a locked system screen the instant it is detected, so nothing on it can
 * be tapped while the guard backs out underneath. Plain views, not Compose, so it is drawn on the
 * very next frame. Not focusable, so the system Back gesture still reaches the screen below.
 */
class TamperShield(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var view: View? = null

    val isShowing get() = view != null

    fun show() {
        if (view != null) return
        val pad = (32 * service.resources.displayMetrics.density).toInt()
        val message = TextView(service).apply {
            setText(R.string.settings_locked_toast)
            setTextColor(service.getColor(R.color.shield_text))
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }
        val cover = FrameLayout(service).apply {
            setBackgroundColor(service.getColor(R.color.window_bg))
            isClickable = true
            addView(message, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER,
            ))
        }
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        runCatching { wm.addView(cover, params) }.onSuccess { view = cover }
    }

    fun hide() {
        view?.let { runCatching { wm.removeViewImmediate(it) } }
        view = null
    }
}
