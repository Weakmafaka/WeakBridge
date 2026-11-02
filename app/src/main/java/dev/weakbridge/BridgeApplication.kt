package dev.weakbridge

import android.app.Application
import android.content.Context
import dev.weakbridge.ble.BridgeStatus
import kotlinx.coroutines.flow.MutableStateFlow

class BridgeApplication : Application() {
    val status = MutableStateFlow(BridgeStatus())
    val deviceListTitle = MutableStateFlow("Список устройств ещё не открыт")
    val devices = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    lateinit var settings: BridgeSettings

    override fun onCreate() {
        super.onCreate()
        settings = BridgeSettings(this)
    }
}

class BridgeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("bridge", Context.MODE_PRIVATE)
    var address: String?
        get() = prefs.getString("address", null)
        set(value) {
            prefs.edit().putString("address", value).apply()
        }

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) {
            prefs.edit().putBoolean("enabled", value).apply()
        }

    var autoReconnect: Boolean
        get() = prefs.getBoolean("auto", true)
        set(value) {
            prefs.edit().putBoolean("auto", value).apply()
        }

    var media: Boolean
        get() = prefs.getBoolean("media", true)
        set(value) {
            prefs.edit().putBoolean("media", value).apply()
        }

    var diagnostics: Boolean
        get() = prefs.getBoolean("diagnostics", false)
        set(value) {
            prefs.edit().putBoolean("diagnostics", value).apply()
        }

    var lowPower: Boolean
        get() = prefs.getBoolean("lowPower", true)
        set(value) {
            prefs.edit().putBoolean("lowPower", value).apply()
        }

    fun category(id: Int) = prefs.getBoolean("category$id", true)

    fun setCategory(id: Int, value: Boolean) {
        prefs.edit().putBoolean("category$id", value).apply()
    }
}
