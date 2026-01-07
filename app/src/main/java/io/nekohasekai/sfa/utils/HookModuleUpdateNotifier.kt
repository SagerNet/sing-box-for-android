package io.nekohasekai.sfa.utils

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
import io.nekohasekai.sfa.xposed.HookModuleVersion

object HookModuleUpdateNotifier {
    private const val CHANNEL_ID = "lsposed_module_update"
    private const val NOTIFICATION_ID = 0x5F10

    fun needsRestart(status: HookStatusClient.Status?): Boolean {
        return isDowngrade(status) || isUpgrade(status)
    }

    fun isDowngrade(status: HookStatusClient.Status?): Boolean {
        return status != null && status.version > HookModuleVersion.CURRENT
    }

    fun isUpgrade(status: HookStatusClient.Status?): Boolean {
        return status != null && status.version < HookModuleVersion.CURRENT
    }

    fun sync(context: Context) {
        HookStatusClient.refresh()
        maybeNotify(context, HookStatusClient.status.value)
    }

    fun maybeNotify(context: Context, status: HookStatusClient.Status?) {
        if (!needsRestart(status)) {
            cancel(context)
            return
        }
        ensureChannel(context)
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                addCategory("de.robv.android.xposed.category.MODULE_SETTINGS")
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or ServiceNotification.flags,
            )
        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_menu)
                .setContentTitle(context.getString(R.string.privilege_module_restart_notification_title))
                .setContentText(context.getString(R.string.privilege_module_restart_notification_message))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    private fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.privilege_module_restart_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
    }
}
