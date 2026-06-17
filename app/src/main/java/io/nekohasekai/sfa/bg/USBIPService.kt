package io.nekohasekai.sfa.bg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.usbip.USBIPManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class USBIPService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectJob: Job? = null
    private var receiverRegistered = false

    private val detachReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
                val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java) ?: return
                USBIPManager.detachByDevice(device)
            }
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            USBIPManager.shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(USBIPManager.state.value.devices.size),
            foregroundServiceType(),
        )
        registerDetachReceiver()
        if (collectJob == null) {
            collectJob =
                scope.launch {
                    USBIPManager.state.collect { state ->
                        if (state.devices.isEmpty()) {
                            stopSelf()
                        } else {
                            Application.notificationManager.notify(NOTIFICATION_ID, buildNotification(state.devices.size))
                        }
                    }
                }
        }
        return START_STICKY
    }

    private fun registerDetachReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            detachReceiver,
            IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        Application.notification.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL, "USB/IP", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun buildNotification(count: Int) = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
        .setShowWhen(false)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSmallIcon(R.drawable.ic_menu)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentTitle(getString(R.string.usbip_notification_title))
        .setContentText(resources.getQuantityString(R.plurals.usbip_notification_text, count, count))
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
                getText(R.string.stop),
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, USBIPService::class.java).setAction(ACTION_STOP),
                    pendingIntentFlags(),
                ),
            ).build(),
        )
        .build()

    override fun onDestroy() {
        super.onDestroy()
        collectJob?.cancel()
        collectJob = null
        if (receiverRegistered) {
            unregisterReceiver(detachReceiver)
            receiverRegistered = false
        }
        scope.cancel()
        USBIPManager.onServiceDestroyed()
    }

    companion object {
        const val ACTION_STOP = "io.nekohasekai.sfa.action.USBIP_STOP"
        private const val NOTIFICATION_ID = 2
        private const val NOTIFICATION_CHANNEL = "usbip"

        private fun pendingIntentFlags() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        private fun foregroundServiceType() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
    }
}
