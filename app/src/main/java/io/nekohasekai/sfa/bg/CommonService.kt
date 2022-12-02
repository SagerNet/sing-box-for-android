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
import io.nekohasekai.sfa.db.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class CommonService(
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
            Libbox.newService(configContent, platformInterface)
        } catch (e: Exception) {
            stopAndAlert(Alert.CreateService, e.message)
            return
        }
        withContext(Dispatchers.IO) {
            runCatching {
                newService.start()
            }.onFailure {
                launch(Dispatchers.Main) {
                    stopAndAlert(Alert.StartService, it.message)
                }
            }
            boxService = newService
            status.postValue(Status.Started)
        }
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

    private fun stopAndAlert(type: Alert, message: String? = null) {
        service.unregisterReceiver(receiver)
        notification.close()
        binder.broadcast { callback ->
            callback.alert(type.ordinal, message)
        }
        status.value = Status.Stopped
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
        GlobalScope.launch(Dispatchers.Main) {
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