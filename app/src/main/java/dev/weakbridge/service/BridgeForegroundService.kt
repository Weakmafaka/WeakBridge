package dev.weakbridge.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.weakbridge.*
import dev.weakbridge.ams.*
import dev.weakbridge.ancs.*
import dev.weakbridge.ble.*
import dev.weakbridge.diagnostics.BridgeDiagnostics
import dev.weakbridge.integration.*
import dev.weakbridge.ui.MainActivity
import java.util.UUID
import kotlinx.coroutines.*

@SuppressLint("MissingPermission")
class BridgeForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app
        get() = application as BridgeApplication

    private lateinit var devices: BleDeviceRepository
    private lateinit var connection: BleConnectionManager
    private lateinit var diagnostics: BridgeDiagnostics
    private lateinit var notifications: NotificationBridge
    private lateinit var media: MediaSessionBridge
    private var ancs: AncsClient? = null
    private var ams: AmsClient? = null
    private var mediaCollector: Job? = null
    private var retry: Job? = null
    private var stable: Job? = null
    private var selection: Job? = null
    private var bond: CompletableDeferred<Unit>? = null
    private var bondingAddress: String? = null
    private val machine = ConnectionStateMachine()
    private var attempts = 0
    private var passive = false
    private var receiverRegistered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        diagnostics = BridgeDiagnostics(app.settings)
        devices = BleDeviceRepository(this, scope, diagnostics)
        notifications = NotificationBridge(this)
        media = MediaSessionBridge(this) { ams?.command(it) }
        connection =
            BleConnectionManager(
                this,
                scope,
                diagnostics,
                ::initialize,
                ::onValue,
                ::lost,
                ::servicesChanged,
            )
        active = this
        registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            },
            Context.RECEIVER_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun foreground() {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        startForeground(
            1,
            NotificationCompat.Builder(this, "bridge")
                .setSmallIcon(R.drawable.ic_bridge)
                .setContentTitle("WeakBridge")
                .setContentText("iPhone Bluetooth bridge")
                .setContentIntent(open)
                .setOngoing(true)
                .setSilent(true)
                .build(),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            status(ConnectionState.ERROR, "Nearby devices permission required")
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            foreground()
        } catch (e: RuntimeException) {
            status(ConnectionState.ERROR, "Open the app to start the bridge")
            stopSelf()
            return START_NOT_STICKY
        }
        diagnostics.event("service_starts")
        try {
            when (intent?.action) {
                DISCONNECT,
                FORGET -> {
                    app.settings.enabled = false
                    if (intent.action == FORGET) app.settings.address = null
                    stopAll()
                    status(ConnectionState.DISCONNECTED, "Disconnected")
                    stopSelf()
                    return START_NOT_STICKY
                }
                SCAN -> {
                    app.settings.enabled = false
                    stopAll()
                    status(ConnectionState.SCANNING, "Scanning for ANCS, 20 seconds")
                    devices.scan(
                        onEnd = {
                            status(
                                ConnectionState.DISCONNECTED,
                                "Поиск ANCS завершён. Если iPhone не найден, используйте «Подключить iPhone».",
                            )
                        },
                        onError = { status(ConnectionState.ERROR, it) },
                    )
                }
                PAIR -> {
                    app.settings.enabled = false
                    stopAll()
                    status(ConnectionState.BONDING, "Запускается BLE-реклама для сопряжения…")
                    devices.advertise(
                        onStatus = { status(ConnectionState.BONDING, it) },
                        onEnd = {
                            status(
                                ConnectionState.DISCONNECTED,
                                "Окно сопряжения закрыто. Если сопряжение прошло, выберите iPhone в списке сохранённых устройств.",
                            )
                        },
                        onError = { status(ConnectionState.ERROR, it) },
                    )
                }
                BONDS -> devices.bonds()
                CONNECT -> {
                    val address = intent.getStringExtra("address") ?: app.settings.address
                    if (address != null) select(address)
                    else
                        status(
                            ConnectionState.ERROR,
                            "Сначала подключите iPhone и выберите его в списке сохранённых устройств",
                        )
                }
                CONFIG -> if (app.settings.enabled) reconnect()
                else ->
                    if (app.settings.enabled && app.settings.autoReconnect) reconnect()
                    else {
                        status(ConnectionState.DISCONNECTED, "Tap Connect to start")
                        stopSelf()
                        return START_NOT_STICKY
                    }
            }
        } catch (e: SecurityException) {
            stopAll()
            status(ConnectionState.ERROR, "Nearby devices permission required")
        } catch (e: Exception) {
            stopAll()
            status(
                ConnectionState.ERROR,
                "Bluetooth operation unavailable; enable Bluetooth and retry",
            )
        }
        return if (app.settings.enabled && app.settings.autoReconnect) START_STICKY
        else START_NOT_STICKY
    }

    private fun status(
        state: ConnectionState,
        detail: String,
        n: Boolean = false,
        m: Boolean = false,
    ) {
        machine.transition(state)
        app.status.value = BridgeStatus(state, detail, n, m)
    }

    private fun select(address: String) {
        stopAll()
        attempts = 0
        passive = false
        selection =
            scope.launch {
                try {
                    check(devices.adapter?.isEnabled == true)
                    val device = devices.device(address) ?: error("Device missing")
                    if (device.bondState != BluetoothDevice.BOND_BONDED) {
                        status(ConnectionState.BONDING, "Confirm pairing on both devices")
                        bondingAddress = address
                        val pending = CompletableDeferred<Unit>()
                        bond = pending
                        if (device.bondState != BluetoothDevice.BOND_BONDING)
                            check(device.createBond())
                        withTimeout(90_000) { pending.await() }
                    }
                    devices.remember(device)
                    app.settings.enabled = true
                    status(ConnectionState.CONNECTING, device.name ?: "iPhone")
                    connection.connect(device, false)
                } catch (e: CancellationException) {
                    if (e is TimeoutCancellationException)
                        status(
                            ConnectionState.ERROR,
                            "Pairing timed out; retry from iPhone Bluetooth settings",
                        )
                    else throw e
                } catch (e: Exception) {
                    status(ConnectionState.ERROR, "Pairing failed; check permissions and Bluetooth")
                } finally {
                    bond = null
                    bondingAddress = null
                }
            }
    }

    private fun reconnect() {
        val selected = devices.selected()
        if (selected == null) {
            status(ConnectionState.ERROR, "Saved bond unavailable; select your iPhone again")
            return
        }
        select(selected.address)
    }

    private suspend fun initialize(transport: GattTransport) {
        status(ConnectionState.DISCOVERING_SERVICES, "Discovering iPhone services")
        connection.discover()
        if (transport.has(BleConnectionManager.GATT, BleConnectionManager.CHANGED))
            transport.subscribe(
                BleConnectionManager.GATT,
                BleConnectionManager.CHANGED,
                indication = true,
            )
        status(ConnectionState.AUTHORIZING, "Allow Share System Notifications on iPhone")
        val hasAncs =
            listOf(Ancs.SOURCE, Ancs.CONTROL, Ancs.DATA).all { transport.has(Ancs.SERVICE, it) }
        val hasAms =
            app.settings.media &&
                listOf(Ams.COMMAND, Ams.UPDATE).all { transport.has(Ams.SERVICE, it) }
        status(ConnectionState.SUBSCRIBING, "Enabling event subscriptions")
        if (hasAncs) {
            ancs =
                AncsClient(
                    scope,
                    transport,
                    app.settings::category,
                    notifications::publish,
                    notifications::remove,
                ) {
                    lost(-5)
                }
            ancs!!.start()
        }
        if (hasAms) {
            ams = AmsClient(scope, transport, { lost(-6) })
            mediaCollector = scope.launch { ams?.state?.collect { media.update(it) } }
            ams!!.start()
        }
        connection.idle(app.settings.lowPower)
        status(
            ConnectionState.CONNECTED,
            if (hasAncs || hasAms) "Connected"
            else "Apple services absent; waiting for Service Changed",
            hasAncs,
            hasAms,
        )
        stable =
            scope.launch {
                delay(120_000)
                attempts = 0
                passive = false
            }
    }

    private fun onValue(uuid: UUID, bytes: ByteArray) {
        when (uuid) {
            Ancs.SOURCE -> {
                diagnostics.event("ancs_events")
                ancs?.source(bytes)
            }
            Ancs.DATA -> {
                diagnostics.event("ancs_fragments")
                ancs?.data(bytes)
            }
            Ams.UPDATE -> {
                diagnostics.event("ams_events")
                ams?.update(bytes)
            }
            Ams.COMMAND -> ams?.supported(bytes)
            BleConnectionManager.CHANGED -> servicesChanged()
        }
    }

    private fun servicesChanged() {
        // New generation rebuilds every subscription; no hidden BluetoothGatt.refresh API.
        if (app.status.value.state == ConnectionState.RECONNECTING) return
        lost(-7)
    }

    private fun closeProtocols() {
        stable?.cancel()
        stable = null
        mediaCollector?.cancel()
        mediaCollector = null
        ancs?.close()
        ancs = null
        ams?.close()
        ams = null
        notifications.clear()
        media.update(MediaState())
    }

    private fun lost(code: Int) {
        connection.close()
        closeProtocols()
        retry?.cancel()
        retry = null
        diagnostics.event("connection_drops")
        if (!app.settings.enabled) {
            status(ConnectionState.DISCONNECTED, "Disconnected")
            return
        }
        if (
            code in setOf(1005, 1008, 1015)
        ) { // Authentication, encryption, authorization: user intervention, no retry storm.
            status(
                ConnectionState.ERROR,
                "iPhone authorization failed ($code); check pairing and Share System Notifications",
            )
            return
        }
        if (!app.settings.autoReconnect) {
            status(ConnectionState.DISCONNECTED, "Connection lost ($code); tap Reconnect")
            return
        }
        if (runCatching { devices.adapter?.isEnabled }.getOrNull() != true) {
            status(ConnectionState.DISCONNECTED, "Waiting for Bluetooth")
            return
        }
        if (passive) {
            status(ConnectionState.ERROR, "Passive restore failed ($code); tap Reconnect")
            return
        }
        val delayMs = BleReconnectPolicy().delayMs(attempts++)
        status(
            ConnectionState.RECONNECTING,
            if (delayMs == null) "Waiting for iPhone (passive BLE)"
            else "Connection lost ($code); retry in ${delayMs / 1000}s",
        )
        retry =
            scope.launch {
                try {
                    if (delayMs != null) delay(delayMs)
                    val selected =
                        devices.selected()
                            ?: run {
                                status(
                                    ConnectionState.ERROR,
                                    "Bond unavailable; select iPhone again",
                                )
                                return@launch
                            }
                    diagnostics.event("reconnect_attempts")
                    passive = delayMs == null
                    connection.connect(selected, passive)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    status(ConnectionState.ERROR, "Reconnect unavailable; open app and retry")
                }
            }
    }

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                try {
                    when (intent.action) {
                        BluetoothAdapter.ACTION_STATE_CHANGED ->
                            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                                BluetoothAdapter.STATE_OFF,
                                BluetoothAdapter.STATE_TURNING_OFF -> {
                                    stopAll()
                                    status(ConnectionState.DISCONNECTED, "Waiting for Bluetooth")
                                }
                                BluetoothAdapter.STATE_ON ->
                                    if (app.settings.enabled && app.settings.autoReconnect)
                                        reconnect()
                            }
                        BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                            devices.bonds()
                            val device =
                                intent.getParcelableExtra(
                                    BluetoothDevice.EXTRA_DEVICE,
                                    BluetoothDevice::class.java,
                                ) ?: return
                            if (device.address == bondingAddress)
                                when (device.bondState) {
                                    BluetoothDevice.BOND_BONDED -> bond?.complete(Unit)
                                    BluetoothDevice.BOND_NONE ->
                                        bond?.completeExceptionally(
                                            IllegalStateException("Bond failed")
                                        )
                                }
                            if (
                                device.address == app.settings.address &&
                                    device.bondState == BluetoothDevice.BOND_NONE
                            ) {
                                stopAll()
                                status(
                                    ConnectionState.ERROR,
                                    "iPhone bond removed; select device again",
                                )
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    stopAll()
                    status(ConnectionState.ERROR, "Bluetooth permission revoked")
                }
            }
        }

    fun notificationAction(token: String?, uid: Long, positive: Boolean) {
        if (!notifications.accepts(token)) return
        scope.launch {
            try {
                if (notifications.accepts(token)) ancs?.action(uid, positive)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                diagnostics.event("action_failures")
            }
        }
    }

    fun mediaAction(token: String?, command: Int) {
        media.fromNotification(token, command)
    }

    fun diagnosticText() = diagnostics.snapshot(this)

    private fun stopAll() {
        selection?.cancel()
        selection = null
        retry?.cancel()
        retry = null
        devices.stopDiscovery()
        connection.close()
        closeProtocols()
    }

    override fun onDestroy() {
        if (active === this) active = null
        stopAll()
        media.close()
        if (receiverRegistered) unregisterReceiver(receiver)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        var active: BridgeForegroundService? = null
            private set

        const val CONNECT = "connect"
        const val DISCONNECT = "disconnect"
        const val FORGET = "forget"
        const val SCAN = "scan"
        const val PAIR = "pair"
        const val BONDS = "bonds"
        const val CONFIG = "config"
    }
}
