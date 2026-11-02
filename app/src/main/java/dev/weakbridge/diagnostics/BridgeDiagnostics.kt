package dev.weakbridge.diagnostics

import android.content.Context
import android.os.BatteryManager
import android.os.SystemClock
import dev.weakbridge.BridgeSettings

/** Counters only, no packet bytes, addresses, app names or message contents. No timers. */
class BridgeDiagnostics(private val settings: BridgeSettings) {
    private val counts = linkedMapOf<String, Long>()
    private var connectedAt: Long? = null
    private var connectionMs = 0L
    private var scanAt: Long? = null
    private var scanMs = 0L

    fun event(name: String) {
        if (settings.diagnostics) counts[name] = (counts[name] ?: 0) + 1
    }

    fun connected() {
        if (settings.diagnostics) connectedAt = SystemClock.elapsedRealtime()
    }

    fun disconnected() {
        connectedAt?.let { connectionMs += SystemClock.elapsedRealtime() - it }
        connectedAt = null
    }

    fun scan(start: Boolean) {
        if (start && settings.diagnostics) {
            scanAt = SystemClock.elapsedRealtime()
            event("scan_sessions")
        } else if (!start) {
            scanAt?.let { scanMs += SystemClock.elapsedRealtime() - it }
            scanAt = null
        }
    }

    fun snapshot(context: Context): String {
        if (!settings.diagnostics) return "Diagnostics disabled"
        val now = SystemClock.elapsedRealtime()
        val bm = context.getSystemService(BatteryManager::class.java)
        return buildString {
            appendLine("Connected ms: ${connectionMs + (connectedAt?.let { now - it } ?: 0)}")
            appendLine("Scan ms: ${scanMs + (scanAt?.let { now - it } ?: 0)}")
            counts.forEach { (key, count) -> appendLine("$key: $count") }
            appendLine("Battery %: ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}")
            appendLine(
                "Charge µAh (MIN_VALUE = unavailable): ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)}"
            )
            append("Callbacks are an activity proxy, not measured CPU wakeups.")
        }
    }
}
