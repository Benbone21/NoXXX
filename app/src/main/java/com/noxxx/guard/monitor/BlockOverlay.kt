package com.noxxx.guard.monitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.noxxx.guard.data.BlockKind
import com.noxxx.guard.ui.BlockScreen
import com.noxxx.guard.ui.MainActivity
import com.noxxx.guard.ui.NoXXXTheme

/** Full-screen Material 3 cover drawn by the accessibility service (no overlay permission needed). */
class BlockOverlay(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null

    val isShowing get() = view != null

    fun show(kind: BlockKind, detail: String, streakDays: Long, onClosed: () -> Unit) {
        if (view != null) return
        val lifecycle = OverlayOwner().also { it.start() }
        val offerApp = kind == BlockKind.DNS_OFF || kind == BlockKind.SETTINGS
        val compose = ComposeView(service).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            setContent {
                NoXXXTheme {
                    BlockScreen(
                        kind = kind,
                        detail = detail,
                        streakDays = streakDays,
                        onOpenApp = if (offerApp) ({ dismiss(); openApp() }) else null,
                        onClose = { dismiss(); onClosed() },
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE,
        )
        runCatching { wm.addView(compose, params) }
            .onSuccess { view = compose; owner = lifecycle }
            .onFailure { lifecycle.stop() }
    }

    fun dismiss() {
        view?.let { runCatching { wm.removeView(it) } }
        owner?.stop()
        view = null
        owner = null
    }

    private fun openApp() {
        service.startActivity(
            Intent(service, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    /** Compose needs a lifecycle and saved-state owner; a service window has neither, so provide one. */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        fun start() {
            savedState.performAttach()
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
