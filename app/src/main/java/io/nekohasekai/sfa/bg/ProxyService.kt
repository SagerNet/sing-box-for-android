package io.nekohasekai.sfa.bg

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.lifecycle.MutableLiveData
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.TunInterface
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.sfa.constant.Action
import io.nekohasekai.sfa.constant.Alert
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.db.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProxyService : Service(), PlatformInterface {

    private val status = MutableLiveData(Status.Stopped)
    private val binder = ServiceBinder(status)
    private val notification = ServiceNotification(this)
    private var boxService: io.nekohasekai.libbox.Service? = null
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
            Libbox.newService(configContent, this)
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
        notification.close()
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

        notification.show()
        GlobalScope.launch(Dispatchers.Main) {
            startService()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        binder.close()
    }

    override fun autoDetectInterfaceControl(fd: Int) {
    }

    override fun openTun(options: TunOptions?): TunInterface {
        error("bad state: create tun in normal service")
    }

    override fun writeLog(message: String?) {
        binder.broadcast {
            it.writeLog(message)
        }
    }
}