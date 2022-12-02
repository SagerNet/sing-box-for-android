package io.nekohasekai.sfa.bg

import android.os.RemoteCallbackList
import androidx.lifecycle.MutableLiveData
import io.nekohasekai.sfa.aidl.IVPNService
import io.nekohasekai.sfa.aidl.IVPNServiceCallback
import io.nekohasekai.sfa.constant.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ServiceBinder(private val status: MutableLiveData<Status>) : IVPNService.Stub() {
    private val callbacks = RemoteCallbackList<IVPNServiceCallback>()
    private val broadcastLock = Mutex()

    init {
        status.observeForever {
            broadcast { callback ->
                callback.onStatusChanged(it.ordinal)
            }
        }
    }

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
        return (status.value ?: Status.Stopped).ordinal
    }

    override fun registerCallback(callback: IVPNServiceCallback) {
        callbacks.register(callback)
        callback.onStatusChanged(getStatus())
    }

    override fun unregisterCallback(callback: IVPNServiceCallback?) {
        callbacks.unregister(callback)
    }

    fun close() {
        callbacks.kill()
    }
}