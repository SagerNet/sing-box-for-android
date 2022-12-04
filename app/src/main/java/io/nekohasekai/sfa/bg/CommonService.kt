package io.nekohasekai.sfa.bg

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.lifecycle.MutableLiveData
import go.Seq
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.sfa.constant.Action
import io.nekohasekai.sfa.constant.Alert
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CommonService(
    private val service: Service,
    private val platformInterface: PlatformInterface
) {

    private val status = MutableLiveData(Status.Stopped)
    private val binder = ServiceBinder(status)
    private val notification = ServiceNotification(service)
    private var boxService: BoxService? = null
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
        val configContent = Settings.configurationContent
        if (configContent.isBlank()) {
            stopAndAlert(Alert.EmptyConfiguration)
            return
        }

        withContext(Dispatchers.Main) {
            binder.broadcast {
                it.resetLogs(listOf())
            }
        }

        val newService = try {
            Libbox.newService(configContent, platformInterface)
        } catch (e: Exception) {
            stopAndAlert(Alert.CreateService, e.message)
            return
        }

        try {
            newService.start()
        } catch (e: Exception) {
            stopAndAlert(Alert.StartService, e.message)
        }

        boxService = newService
        status.postValue(Status.Started)
    }

    private fun stopService() {
        if (status.value != Status.Started) return
        status.value = Status.Stopping
        if (receiverRegistered) {
            service.unregisterReceiver(receiver)
            receiverRegistered = false
        }
        notification.close()
        boxService?.apply {
            close()
            Seq.destroyRef(refnum)
        }
        boxService = null
        status.value = Status.Stopped
        service.stopSelf()
    }

    private suspend fun stopAndAlert(type: Alert, message: String? = null) {
        withContext(Dispatchers.Main) {
            if (receiverRegistered) {
                service.unregisterReceiver(receiver)
                receiverRegistered = false
            }
            notification.close()
            binder.broadcast { callback ->
                callback.alert(type.ordinal, message)
            }
            status.value = Status.Stopped
        }
    }

    fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (status.value != Status.Stopped) return Service.START_NOT_STICKY
        status.value = Status.Starting

        if (!receiverRegistered) {
            service.registerReceiver(receiver, IntentFilter().apply {
                addAction(Action.SERVICE_CLOSE)
            })
            receiverRegistered = true
        }

        notification.show()
        GlobalScope.launch(Dispatchers.IO) {
            startService()
        }
        return Service.START_NOT_STICKY
    }

    fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun onDestroy() {
        binder.close()
    }

    fun onRevoke() {
        stopService()
    }

    fun writeLog(message: String?) {
        binder.broadcast {
            it.writeLog(message)
        }
    }

}