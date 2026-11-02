package dev.weakbridge.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.*
import dev.weakbridge.*
import dev.weakbridge.ancs.Ancs
import dev.weakbridge.service.BridgeForegroundService as Bridge

class MainActivity : ComponentActivity() {
    private val app
        get() = application as BridgeApplication

    private val permissions =
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    private var permissionRevision by mutableIntStateOf(0)
    private var pendingAction: Pair<String, String?>? = null
    private val request =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionRevision++
            val pending = pendingAction
            pendingAction = null
            if (
                pending != null &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                        PackageManager.PERMISSION_GRANTED
            ) {
                val required =
                    if (pending.first == Bridge.PAIR) Manifest.permission.BLUETOOTH_ADVERTISE
                    else if (pending.first == Bridge.SCAN) Manifest.permission.BLUETOOTH_SCAN
                    else Manifest.permission.BLUETOOTH_CONNECT
                if (checkSelfPermission(required) == PackageManager.PERMISSION_GRANTED)
                    send(pending.first, pending.second)
            }
        }
    private val discoverableRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // Android returns the approved duration; cancellation must never silently start
            // pairing.
            if (result.resultCode > 0) startBridgeService(Bridge.PAIR)
            else
                app.status.value =
                    app.status.value.copy(
                        detail =
                            "Системная видимость не включена. Для сопряжения подтвердите запрос на часах."
                    )
        }

    override fun onResume() {
        super.onResume()
        permissionRevision++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val status by app.status.collectAsStateWithLifecycle()
                val devices by app.devices.collectAsStateWithLifecycle()
                val deviceListTitle by app.deviceListTitle.collectAsStateWithLifecycle()
                var settings by remember { mutableStateOf(false) }
                var confirm by remember { mutableStateOf<String?>(null) }
                var revision by remember { mutableIntStateOf(0) }
                var diagnostic by remember { mutableStateOf("") }
                val bluetoothGranted =
                    remember(permissionRevision) {
                        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                            PackageManager.PERMISSION_GRANTED
                    }
                val notificationGranted =
                    remember(permissionRevision) {
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                    }
                LazyColumn(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 32.dp),
                ) {
                    item {
                        Text(
                            if (settings) "Настройки" else "WeakBridge",
                            style = MaterialTheme.typography.title2,
                        )
                    }
                    if (confirm != null) {
                        item {
                            Text(
                                if (confirm == Bridge.FORGET)
                                    "Забыть выбор? Bluetooth-сопряжение сохранится."
                                else "Отключить мост и автоматический запуск?"
                            )
                        }
                        item {
                            Action("Подтвердить") {
                                send(confirm!!)
                                confirm = null
                            }
                        }
                        item { Action("Отмена") { confirm = null } }
                    } else if (settings) {
                        item {
                            Setting("Автоподключение", app.settings.autoReconnect, revision) {
                                app.settings.autoReconnect = it
                                revision++
                                send(Bridge.CONFIG)
                            }
                        }
                        item {
                            Setting("Управление музыкой", app.settings.media, revision) {
                                app.settings.media = it
                                revision++
                                send(Bridge.CONFIG)
                            }
                        }
                        item {
                            Setting("Экономия энергии", app.settings.lowPower, revision) {
                                app.settings.lowPower = it
                                revision++
                                send(Bridge.CONFIG)
                            }
                        }
                        item {
                            Setting("Диагностика", app.settings.diagnostics, revision) {
                                app.settings.diagnostics = it
                                revision++
                            }
                        }
                        item { Text("Категории уведомлений") }
                        Ancs.categories.forEachIndexed { id, label ->
                            item {
                                Setting(label, app.settings.category(id), revision) {
                                    app.settings.setCategory(id, it)
                                    revision++
                                }
                            }
                        }
                        item {
                            Action("Показать диагностику") {
                                diagnostic = Bridge.active?.diagnosticText() ?: "Служба остановлена"
                            }
                        }
                        if (diagnostic.isNotEmpty())
                            item { Text(diagnostic, style = MaterialTheme.typography.caption2) }
                        item { Action("Забыть выбор устройства") { confirm = Bridge.FORGET } }
                        item { Action("Назад") { settings = false } }
                    } else {
                        item {
                            Text(
                                when (status.state) {
                                    dev.weakbridge.ble.ConnectionState.DISCONNECTED ->
                                        "Не подключено"
                                    dev.weakbridge.ble.ConnectionState.CONNECTED -> "Подключено"
                                    dev.weakbridge.ble.ConnectionState.BONDING -> "Сопряжение"
                                    dev.weakbridge.ble.ConnectionState.ERROR -> "Ошибка"
                                    else -> "Подключение…"
                                },
                                style = MaterialTheme.typography.title3,
                            )
                        }
                        item { Text(status.detail, style = MaterialTheme.typography.caption1) }
                        item {
                            Text(
                                "Уведомления: ${serviceStatusText(status, status.notifications, permission = notificationGranted && bluetoothGranted)}\nМузыка: ${serviceStatusText(status, status.media, enabled = app.settings.media, permission = bluetoothGranted)}",
                                style = MaterialTheme.typography.caption2,
                            )
                        }
                        app.settings.address?.let { address ->
                            item {
                                Text(
                                    "Selected: ${devices.firstOrNull { it.first == address }?.second ?: "saved bonded device"}",
                                    style = MaterialTheme.typography.caption2,
                                )
                            }
                        }
                        item {
                            Text(
                                "Bluetooth: ${if (bluetoothGranted) "разрешён" else "нет разрешения"}",
                                style = MaterialTheme.typography.caption2,
                            )
                        }
                        item { Action("Разрешения") { request.launch(permissions) } }
                        item { Action("Подключить iPhone") { send(Bridge.PAIR) } }
                        item { Action("Переподключить") { send(Bridge.CONNECT) } }
                        item { Action("Сохранённые устройства") { send(Bridge.BONDS) } }
                        item { Text(deviceListTitle, style = MaterialTheme.typography.caption2) }
                        devices.forEach { (address, name) ->
                            item {
                                Action("$name · ${address.takeLast(5)}") {
                                    send(Bridge.CONNECT, address)
                                }
                            }
                        }
                        item {
                            Text(
                                "Выберите только свой iPhone. Если его нет, используйте «Подключить iPhone».",
                                style = MaterialTheme.typography.caption2,
                            )
                        }
                        item { Action("Поиск ANCS (20 с)") { send(Bridge.SCAN) } }
                        item { Action("Настройки") { settings = true } }
                        item { Action("Отключить") { confirm = Bridge.DISCONNECT } }
                    }
                }
            }
        }
    }

    private fun send(action: String, address: String? = null) {
        val needed =
            when (action) {
                Bridge.PAIR ->
                    listOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                    )
                Bridge.SCAN ->
                    listOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                    )
                else -> listOf(Manifest.permission.BLUETOOTH_CONNECT)
            }
        if (needed.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            pendingAction = action to address
            request.launch(permissions)
            return
        }
        if (action == Bridge.PAIR) {
            try {
                discoverableRequest.launch(
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                        .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                )
            } catch (_: ActivityNotFoundException) {
                app.status.value =
                    app.status.value.copy(
                        detail =
                            "Системный запрос видимости недоступен. Откройте Bluetooth в настройках часов."
                    )
            } catch (_: SecurityException) {
                app.status.value =
                    app.status.value.copy(
                        detail = "Для видимости нужно разрешение «Устройства поблизости»."
                    )
            }
        } else startBridgeService(action, address)
    }

    private fun startBridgeService(action: String, address: String? = null) {
        startForegroundService(
            Intent(this, Bridge::class.java).setAction(action).putExtra("address", address)
        )
    }
}

@Composable
private fun Action(label: String, click: () -> Unit) {
    Chip(onClick = click, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Setting(label: String, value: Boolean, revision: Int, change: (Boolean) -> Unit) {
    ToggleChip(
        checked = value,
        onCheckedChange = change,
        label = { Text(label) },
        toggleControl = { Switch(checked = value) },
        modifier = Modifier.fillMaxWidth(),
    )
}
