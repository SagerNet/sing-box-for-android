package io.nekohasekai.sfa.bg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.compose.screen.tools.TaildropSendManager
import io.nekohasekai.sfa.compose.screen.tools.TaildropSendState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TaildropSendService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            TaildropSendManager.cancelAll()
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(activeSessions()),
            foregroundServiceType(),
        )
        if (collectJob == null) {
            collectJob =
                scope.launch {
                    TaildropSendManager.sessions.collect {
                        val active = activeSessions()
                        if (active.isEmpty()) {
                            stopSelf()
                        } else {
                            Application.notificationManager.notify(NOTIFICATION_ID, buildNotification(active))
                        }
                    }
                }
        }
        return START_NOT_STICKY
    }

    private fun activeSessions(): List<TaildropSendState> = TaildropSendManager.sessions.value.filter { !it.finished }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        Application.notification.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL, getString(R.string.taildrop), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun buildNotification(sessions: List<TaildropSendState>) = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
        .setShowWhen(false)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSmallIcon(R.drawable.ic_menu)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentTitle(getString(R.string.taildrop_notification_title))
        .setContentText(notificationText(sessions))
        .setProgress(100, sentPercentage(sessions), false)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                pendingIntentFlags(),
            ),
        )
        .addAction(
            NotificationCompat.Action.Builder(
                0,
                getText(android.R.string.cancel),
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, TaildropSendService::class.java).setAction(ACTION_STOP),
                    pendingIntentFlags(),
                ),
            ).build(),
        )
        .build()

    private fun notificationText(sessions: List<TaildropSendState>): String {
        val first = sessions.firstOrNull() ?: return ""
        return getString(
            R.string.taildrop_to,
            if (sessions.size == 1) first.peerName else "${first.peerName} +${sessions.size - 1}",
        )
    }

    private fun sentPercentage(sessions: List<TaildropSendState>): Int {
        var total = 0L
        var sent = 0L
        for (session in sessions) {
            for (file in session.files) {
                if (file.size > 0) {
                    total += file.size
                    sent += file.sentBytes
                }
            }
        }
        if (total <= 0) return 0
        return (sent * 100 / total).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        collectJob?.cancel()
        collectJob = null
        scope.cancel()
    }

    companion object {
        const val ACTION_STOP = "io.nekohasekai.sfa.action.TAILDROP_SEND_STOP"
        private const val NOTIFICATION_ID = 3
        private const val NOTIFICATION_CHANNEL = "taildrop"

        private fun pendingIntentFlags() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        private fun foregroundServiceType() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
    }
}
