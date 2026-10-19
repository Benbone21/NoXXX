package com.noxxx.guard

import android.app.Application
import android.os.SystemClock
import com.noxxx.guard.data.BlockKind
import com.noxxx.guard.data.BlockLog
import com.noxxx.guard.data.DiagnosticsLog
import com.noxxx.guard.data.GuardStore
import com.noxxx.guard.data.NetworkTime
import com.noxxx.guard.monitor.GuardAccessibilityService
import com.noxxx.guard.monitor.TamperNotifier
import com.noxxx.guard.web.DomainBlocklist
import com.noxxx.guard.web.KeywordIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NoXXXApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var store: GuardStore
        private set
    lateinit var blockLog: BlockLog
    lateinit var diagnostics: DiagnosticsLog
        private set
    val blocklist = DomainBlocklist()
    val keywords = KeywordIndex()

    private var clockJob: Job? = null
    private var serviceWasRunning = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = GuardStore(this)
        diagnostics = DiagnosticsLog(this)
        blockLog = BlockLog(this, diagnostics)
        TamperNotifier.ensureChannel(this)
        appScope.launch(Dispatchers.IO) {
            blocklist.loadBundled(this@NoXXXApp)
            keywords.loadBundled(this@NoXXXApp)
        }
        startClock()
    }

    /** Keeps the cool-off timers ticking while the process is alive (the accessibility service keeps it alive). */
    fun startClock() {
        if (clockJob?.isActive == true) return
        clockJob = appScope.launch(Dispatchers.IO) {
            var lastNetworkFix = 0L
            while (isActive) {
                store.tick()
                checkServiceHealth()
                val now = SystemClock.elapsedRealtime()
                val clock = store.state.value.clock
                val due = if (clock.needsNetworkTime) 5 * 60_000L else 6 * 60 * 60_000L
                if (lastNetworkFix == 0L || now - lastNetworkFix > due) {
                    NetworkTime.fetch()?.let { (net, at) ->
                        store.onNetworkTime(net, at)
                        lastNetworkFix = now
                    }
                }
                // Tick faster while an unlock is pending/open so the edit window closes on time.
                delay(if (store.state.value.unlock != null) 15_000 else 60_000)
            }
        }
    }

    private fun checkServiceHealth() {
        val running = GuardAccessibilityService.isRunning
        val state = store.state.value
        if (serviceWasRunning && !running && state.committed) {
            store.recordTamper("Guard service was disabled")
            blockLog.add(BlockKind.GUARD_DISABLED, "Accessibility service turned off",
                reason = "The guard service stopped running while protection was on")
            TamperNotifier.showGuardDown(this)
        }
        if (running && !serviceWasRunning && state.tamperEvents.isNotEmpty()) {
            TamperNotifier.dismiss(this)
        }
        serviceWasRunning = running
    }

    companion object {
        lateinit var instance: NoXXXApp
            private set
    }
}
