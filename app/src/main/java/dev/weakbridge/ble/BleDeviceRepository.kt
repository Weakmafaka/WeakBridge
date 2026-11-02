package dev.weakbridge.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import dev.weakbridge.BridgeApplication
import dev.weakbridge.ancs.Ancs
import dev.weakbridge.diagnostics.BridgeDiagnostics
import kotlinx.coroutines.*

/** Persist only an OS bond reference. Android resolves bonded peer privacy addresses. */
@SuppressLint("MissingPermission")
class BleDeviceRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val diagnostics: BridgeDiagnostics,
) {
    private val app = context.applicationContext as BridgeApplication
    private val manager = context.getSystemService(BluetoothManager::class.java)
    val adapter: BluetoothAdapter?
        get() = manager.adapter

    private var scan: ScanCallback? = null
    private var expiry: Job? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertising: AdvertiseCallback? = null
    private var server: BluetoothGattServer? = null
    private var discoveryEpoch = 0L
    private val found = linkedMapOf<String, String>()

    fun bonds() {
        app.deviceListTitle.value = "Сохранённые Bluetooth-устройства (не поиск)"
        found.clear()
        adapter?.bondedDevices?.forEach { found[it.address] = it.name ?: "Bonded device" }
        publish()
    }

    fun selected(): BluetoothDevice? =
        adapter?.bondedDevices?.firstOrNull { it.address == app.settings.address }

    fun device(address: String): BluetoothDevice? = adapter?.getRemoteDevice(address)

    fun remember(device: BluetoothDevice) {
        require(device.bondState == BluetoothDevice.BOND_BONDED)
        app.settings.address = device.address
    }

    private fun publish() {
        app.devices.value = found.map { it.key to it.value }
    }

    fun scan(onEnd: () -> Unit, onError: (String) -> Unit) {
        stopDiscovery()
        found.clear()
        publish()
        app.deviceListTitle.value = "Устройства, обнаруженные по ANCS"
        val epoch = discoveryEpoch
        val scanner = adapter?.bluetoothLeScanner ?: error("Enable Bluetooth first")
        // Callback identity is checked against the active scan to reject delayed results.
        val actual =
            object : ScanCallback() {
                override fun onScanResult(type: Int, result: ScanResult) {
                    val owner = this
                    scope.launch {
                        if (scan !== owner) return@launch
                        found[result.device.address] = result.device.name ?: "ANCS device"
                        publish()
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    scope.launch {
                        if (epoch == discoveryEpoch) {
                            stopDiscovery()
                            onError("Ошибка BLE-поиска: $errorCode")
                        }
                    }
                }
            }
        scan = actual
        diagnostics.scan(true)
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(Ancs.SERVICE)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(),
            actual,
        )
        expiry =
            scope.launch {
                delay(20_000)
                stopDiscovery()
                onEnd()
            }
    }

    /**
     * iOS commonly discovers an ANCS consumer by service solicitation, not by advertising ANCS
     * itself.
     */
    fun advertise(onStatus: (String) -> Unit, onEnd: () -> Unit, onError: (String) -> Unit) {
        stopDiscovery()
        val epoch = discoveryEpoch
        val bluetooth = adapter ?: error("Bluetooth отсутствует")
        if (!bluetooth.isEnabled) {
            onError("Включите Bluetooth на часах")
            return
        }
        val name = bluetooth.name.orEmpty()
        val layout = PairingAdvertising.layout(name)
        if (layout == PairingAdvertising.Layout.NAME_TOO_LONG) {
            onError(
                "Имя часов слишком длинное для BLE. Задайте в настройках часов имя до 26 байт UTF-8 и повторите."
            )
            return
        }
        val adv =
            bluetooth.bluetoothLeAdvertiser
                ?: run {
                    onError("BLE-реклама недоступна на часах")
                    return
                }
        server =
            manager.openGattServer(
                context,
                object : BluetoothGattServerCallback() {
                    override fun onConnectionStateChange(
                        device: BluetoothDevice,
                        status: Int,
                        newState: Int,
                    ) {
                        scope.launch {
                            if (epoch != discoveryEpoch || advertising == null) return@launch
                            if (status != 0) {
                                onStatus("Ошибка входящего BLE-соединения: $status")
                                return@launch
                            }
                            if (newState == BluetoothProfile.STATE_CONNECTED) {
                                onStatus(
                                    "Устройство подключилось. Подтвердите сопряжение на обоих устройствах."
                                )
                                try {
                                    if (
                                        device.bondState == BluetoothDevice.BOND_NONE &&
                                            !device.createBond()
                                    )
                                        onStatus(
                                            "Не удалось начать сопряжение. Повторите подключение с iPhone."
                                        )
                                } catch (_: SecurityException) {
                                    onError("Нужно разрешение «Устройства поблизости»")
                                }
                            }
                        }
                    }
                },
            )
                ?: run {
                    onError("Не удалось открыть BLE-сервер сопряжения")
                    return
                }
        val cb =
            object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    scope.launch {
                        if (epoch == discoveryEpoch)
                            onStatus(
                                "BLE-реклама запущена: «$name». Откройте Настройки → Bluetooth на iPhone. Окно: 2 минуты."
                            )
                    }
                }

                override fun onStartFailure(errorCode: Int) {
                    scope.launch {
                        if (epoch == discoveryEpoch) {
                            stopDiscovery()
                            onError(PairingAdvertising.error(errorCode))
                        }
                    }
                }
            }
        advertiser = adv
        advertising = cb
        // Keep the unchanged system name in the primary packet so discovery UIs can see it.
        // Legacy packets have 31 bytes. Longer names move solicitation to the scan response.
        val primary = AdvertiseData.Builder().setIncludeDeviceName(true)
        val response = AdvertiseData.Builder()
        if (layout == PairingAdvertising.Layout.COMBINED)
            primary.addServiceSolicitationUuid(ParcelUuid(Ancs.SERVICE))
        else response.addServiceSolicitationUuid(ParcelUuid(Ancs.SERVICE))
        adv.startAdvertising(
            AdvertiseSettings.Builder()
                .setConnectable(true)
                .setTimeout(120_000)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .build(),
            primary.build(),
            response.build(),
            cb,
        )
        expiry =
            scope.launch {
                delay(120_000)
                stopDiscovery()
                bonds()
                onEnd()
            }
    }

    fun stopDiscovery() {
        discoveryEpoch++
        expiry?.cancel()
        expiry = null
        scan?.let { runCatching { adapter?.bluetoothLeScanner?.stopScan(it) } }
        scan = null
        diagnostics.scan(false)
        advertising?.let { runCatching { advertiser?.stopAdvertising(it) } }
        advertising = null
        advertiser = null
        runCatching { server?.close() }
        server = null
    }
}
