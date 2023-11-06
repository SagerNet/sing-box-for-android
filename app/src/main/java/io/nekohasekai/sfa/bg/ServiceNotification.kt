package io.nekohasekai.sfa.bg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.constant.Action
import io.nekohasekai.sfa.ui.MainActivity

class ServiceNotification(private val service: Service) {
    companion object {
        private const val notificationId = 1
        private const val notificationChannel = "service"
        private val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        fun checkPermission(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return true
            }
            return Application.notification.areNotificationsEnabled()
        }
    }

    private val notificationManager by lazy {
        ContextCompat.getSystemService(
            service,
            NotificationManager::class.java
        )!!
    }

    private var displayed = false


    private val notificationBuilder by lazy {
        NotificationCompat.Builder(service, notificationChannel)
            .setShowWhen(false)
            .setOngoing(true)
            .setContentTitle("sing-box")
            .setOnlyAlertOnce(true)
            .setSmallIcon(R.drawable.ic_menu)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(
                PendingIntent.getActivity(
                    service,
                    0,
                    Intent(
                        service,
                        MainActivity::class.java
                    ).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                    flags
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW).apply {
                addAction(
                    NotificationCompat.Action.Builder(
                        0, service.getText(R.string.stop), PendingIntent.getBroadcast(
                            service,
                            0,
                            Intent(Action.SERVICE_CLOSE).setPackage(service.packageName),
                            flags
                        )
                    ).build()
                )
            }
    }

    fun show() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Application.notification.createNotificationChannel(
                NotificationChannel(
                    notificationChannel, "sing-box service", NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        service.startForeground(notificationId, notificationBuilder
            .setContentText("service started")
            .build())
        displayed = true
    }

    fun updateTitle(title: String) = notificationBuilder.setContentTitle(title)

    fun updateContent(content: String, subContent: String? = null) {
        if (displayed) {
            notificationManager.notify(
                notificationId,
                notificationBuilder.setContentText(content)
                    .apply {
                        if (!subContent.isNullOrBlank()){
                            setSubText(subContent)
                        }
                    }.build()
            )
        }
    }

    fun close() {
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
        displayed = false
    }
}