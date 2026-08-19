package io.nekohasekai.sfa.terminal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.ServiceNotification
import io.nekohasekai.sfa.compose.MainActivity
import java.util.concurrent.atomic.AtomicInteger

/** Desktop notifications (OSC 9 / OSC 777) from terminal sessions. */
object TerminalNotifier {
    private const val CHANNEL_ID = "terminal_notifications"
    private const val NOTIFICATION_ID_BASE = 0x5F20
    private const val NOTIFICATION_ID_COUNT = 8

    private val nextNotificationId = AtomicInteger(0)

    fun notify(context: Context, sessionTitle: String?, title: String, body: String) {
        if (title.isEmpty() && body.isEmpty()) return
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or ServiceNotification.flags,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_menu)
            .setContentTitle(title.ifEmpty { sessionTitle ?: "" })
            .setContentText(body.ifEmpty { title })
            .setSubText(sessionTitle)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        val id = NOTIFICATION_ID_BASE +
            nextNotificationId.getAndIncrement() % NOTIFICATION_ID_COUNT
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted.
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.terminal_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
}
