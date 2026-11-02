package dev.weakbridge.integration

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import dev.weakbridge.R
import dev.weakbridge.ancs.AncsEvent
import dev.weakbridge.service.BridgeForegroundService

class NotificationBridge(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val active = linkedSetOf<Long>()
    private var session = java.util.UUID.randomUUID().toString()

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                "iphone",
                "iPhone notifications",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        manager.createNotificationChannel(
            NotificationChannel("bridge", "Bridge connection", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel("media", "iPhone media", NotificationManager.IMPORTANCE_LOW)
        )
        // Remove stale mirrors after a process death without retaining private content on disk.
        manager.activeNotifications
            .filter { it.tag == "iphone" }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    fun publish(event: AncsEvent, attributes: Map<Int, String>, name: String?) {
        if (
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        )
            return
        if (active.size >= 100 && event.uid !in active) remove(active.first())
        val update = !active.add(event.uid) || event.type == 1 || event.preExisting
        val b =
            NotificationCompat.Builder(context, "iphone")
                .setSmallIcon(R.drawable.ic_bridge)
                .setContentTitle(
                    attributes[1].orEmpty().ifBlank {
                        name?.ifBlank { null } ?: attributes[0].orEmpty()
                    }
                )
                .setSubText(name?.ifBlank { null } ?: attributes[0])
                .setContentText(attributes[3])
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(
                            listOfNotNull(attributes[2], attributes[3])
                                .filter { it.isNotEmpty() }
                                .joinToString("\n")
                        )
                )
                .setOnlyAlertOnce(true)
                .setSilent(update || event.flags and 1 != 0)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setCategory(
                    when (event.category) {
                        1,
                        2 -> NotificationCompat.CATEGORY_CALL
                        6 -> NotificationCompat.CATEGORY_EMAIL
                        5 -> NotificationCompat.CATEGORY_EVENT
                        else -> NotificationCompat.CATEGORY_MESSAGE
                    }
                )
        for (positive in listOf(true, false)) if (event.supports(positive)) {
            val intent =
                Intent(context, NotificationActionReceiver::class.java).apply {
                    action = "dev.weakbridge.ANCS_ACTION"
                    data = Uri.parse("weakbridge://action/$session/${event.uid}/$positive")
                    putExtra("uid", event.uid)
                    putExtra("positive", positive)
                    putExtra("session", session)
                }
            val pi =
                PendingIntent.getBroadcast(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            b.addAction(
                0,
                attributes[if (positive) 6 else 7]?.ifBlank { null }
                    ?: if (positive) "Positive action" else "Negative action",
                pi,
            )
        }
        manager.notify("iphone", event.uid.toInt(), b.build())
    }

    fun accepts(token: String?) = token == session

    fun remove(uid: Long) {
        active.remove(uid)
        manager.cancel("iphone", uid.toInt())
    }

    fun clear() {
        active.toList().forEach(::remove)
        session = java.util.UUID.randomUUID().toString()
    }
}

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Actions never restart a dead bridge or replay into a different session.
        BridgeForegroundService.active?.notificationAction(
            intent.getStringExtra("session"),
            intent.getLongExtra("uid", -1),
            intent.getBooleanExtra("positive", false),
        )
    }
}
