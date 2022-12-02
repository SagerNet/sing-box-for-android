package io.nekohasekai.sfa

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.RemoteCallbackList
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.MutableLiveData
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.Service
import io.nekohasekai.libbox.TunInterface
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.sfa.aidl.IVPNService
import io.nekohasekai.sfa.aidl.IVPNServiceCallback
import io.nekohasekai.sfa.constant.Action
import io.nekohasekai.sfa.constant.Alert
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.db.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class VPNService : VpnService(), PlatformInterface {

    companion object {
        const val notificationId = 1
        const val notificationChannel = "service"
    }

    private var status = MutableLiveData(Status.Stopped)
    private val binder = Binder()

    init {
        status.observeForever {
            Log.d("VPNService", "status changed to $it")
            binder.broadcast { callback ->
                callback.onStatusChanged(it.ordinal)
            }
        }
    }

    private var receiverRegistered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Action.SERVICE_CLOSE -> {
                    stopService()
                }
            }
        }
    }

    private val notification by lazy {
        NotificationCompat.Builder(this, notificationChannel).setWhen(0).setContentTitle("sing-box")
            .setContentText("service started").setOnlyAlertOnce(true)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW).apply {
                addAction(
                    NotificationCompat.Action.Builder(
                        0, getText(R.string.stop), PendingIntent.getBroadcast(
                            this@VPNService,
                            0,
                            Intent(Action.SERVICE_CLOSE).setPackage(this@VPNService.packageName),
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                        )
                    ).setShowsUserInterface(false).build()
                )
            }
    }

    private var boxService: Service? = null
    private suspend fun startService() {
        var configContent: String
        withContext(Dispatchers.IO) {
            configContent = Settings.configurationContent
        }
        if (configContent.isBlank()) {
            stopAndAlert(Alert.EmptyConfiguration)
            return
        }
        val newService = try {
            Libbox.newService(configContent, this@VPNService)
        } catch (e: Exception) {
            stopAndAlert(Alert.CreateService, e.message)
            return
        }
        withContext(Dispatchers.IO) {
            runCatching {
                newService.start()
                boxService = newService
                status.postValue(Status.Started)
            }.onFailure {
                launch(Dispatchers.Main) {
                    stopAndAlert(Alert.StartService, it.message)
                }
            }
        }
    }

    fun stopService() {
        if (status.value != Status.Started) return
        status.value = Status.Stopping
        if (receiverRegistered) {
            unregisterReceiver(receiver)
            receiverRegistered = false
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        boxService?.close()
        boxService = null
        status.value = Status.Stopped
        stopSelf()
    }

    private fun stopAndAlert(type: Alert, message: String? = null) {
        binder.broadcast { callback ->
            callback.alert(type.ordinal, message)
        }
        status.value = Status.Stopped
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (status.value != Status.Stopped) return START_NOT_STICKY
        status.value = Status.Starting

        if (!receiverRegistered) {
            registerReceiver(receiver, IntentFilter().apply {
                addAction(Action.SERVICE_CLOSE)
            })
            receiverRegistered = true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Application.notification.createNotificationChannel(
                NotificationChannel(
                    notificationChannel, "sing-box service", NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        startForeground(notificationId, notification.build())
        GlobalScope.launch(Dispatchers.Main) {
            startService()
        }
        return START_NOT_STICKY
    }

    private inner class Binder : IVPNService.Stub() {
        private val callbacks = RemoteCallbackList<IVPNServiceCallback>()
        private val broadcastLock = Mutex()

        fun broadcast(work: (IVPNServiceCallback) -> Unit) {
            GlobalScope.launch(Dispatchers.Main) {
                broadcastLock.withLock {
                    val count = callbacks.beginBroadcast()
                    try {
                        repeat(count) {
                            try {
                                work(callbacks.getBroadcastItem(it))
                            } catch (_: Exception) {
                            }
                        }
                    } finally {
                        callbacks.finishBroadcast()
                    }
                }
            }
        }

        override fun getStatus(): Int {
            return (this@VPNService.status.value ?: Status.Stopped).ordinal
        }

        override fun registerCallback(callback: IVPNServiceCallback?) {
            callbacks.register(callback)
        }

        override fun unregisterCallback(callback: IVPNServiceCallback?) {
            callbacks.unregister(callback)
        }

        fun close() {
            callbacks.kill()
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onRevoke() {
        stopService()
    }

    override fun onDestroy() {
        binder.close()
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        if (!vpnStarted) {
            return
        }
        if (!protect(fd)) {
            error("android: vpn service protect failed")
        }
    }

    var vpnStarted = false

    override fun openTun(options: TunOptions): TunInterface {
        if (prepare(this) != null) error("android: missing vpn permission")

        val builder = Builder().setSession("sing-box").setMtu(options.mtu)

        val inet4Address = options.inet4Address
        if (inet4Address.hasNext()) {
            while (inet4Address.hasNext()) {
                val address = inet4Address.next()
                builder.addAddress(address.address, address.prefix)
            }
        }

        val inet6Address = options.inet6Address
        if (inet6Address.hasNext()) {
            while (inet6Address.hasNext()) {
                val address = inet6Address.next()
                builder.addAddress(address.address, address.prefix)
            }
        }

        if (options.autoRoute) {
            val inet4RouteAddress = options.inet4RouteAddress
            if (inet4RouteAddress.hasNext()) {
                while (inet4RouteAddress.hasNext()) {
                    val address = inet4RouteAddress.next()
                    builder.addRoute(address.address, address.prefix)
                }
            } else {
                builder.addRoute("0.0.0.0", 0)
            }

            val inet6RouteAddress = options.inet6RouteAddress
            if (inet6RouteAddress.hasNext()) {
                while (inet6RouteAddress.hasNext()) {
                    val address = inet6RouteAddress.next()
                    builder.addRoute(address.address, address.prefix)
                }
            } else {
                builder.addRoute("::", 0)
            }

            val includePackage = options.includePackage
            if (includePackage.hasNext()) {
                while (includePackage.hasNext()) {
                    builder.addAllowedApplication(includePackage.next())
                }
            }

            val excludePackage = options.excludePackage
            if (excludePackage.hasNext()) {
                while (excludePackage.hasNext()) {
                    builder.addDisallowedApplication(excludePackage.next())
                }
            }
        }

        val pfd =
            builder.establish() ?: error("android: the application is not prepared or is revoked")

        vpnStarted = true
        return object : TunInterface {
            override fun fileDescriptor(): Int {
                return pfd.fd
            }

            override fun close() {
                pfd.close()
                vpnStarted = false
            }
        }
    }

    override fun writeLog(message: String) {
        Log.i("sing-box", message)
        binder.broadcast { callback ->
            callback.writeLog(message)
        }
    }

}