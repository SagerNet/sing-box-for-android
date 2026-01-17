package io.nekohasekai.sfa.bg

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.os.IBinder
import android.os.RemoteException
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object RootClient {
    init {
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10),
        )
    }

    private val _rootAvailable = MutableStateFlow<Boolean?>(null)
    val rootAvailable: StateFlow<Boolean?> = _rootAvailable

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected

    private var service: IRootService? = null
    private var connection: ServiceConnection? = null
    private val connectionMutex = Mutex()

    suspend fun checkRootAvailable(): Boolean {
        Shell.getCachedShell()?.close()
        return suspendCancellableCoroutine { continuation ->
            Shell.getShell { shell ->
                val available = shell.isRoot
                _rootAvailable.value = available
                continuation.resume(available)
            }
        }
    }

    suspend fun bindService(): IRootService = connectionMutex.withLock {
        service?.let { return it }

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        val svc = IRootService.Stub.asInterface(binder)
                        service = svc
                        connection = this
                        _serviceConnected.value = true
                        continuation.resume(svc)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        service = null
                        connection = null
                        _serviceConnected.value = false
                    }
                }

                val intent = Intent(Application.application, RootServer::class.java)
                RootService.bind(intent, conn)

                continuation.invokeOnCancellation {
                    RootService.unbind(conn)
                }
            }
        }
    }

    fun unbindService() {
        connection?.let {
            RootService.unbind(it)
            connection = null
            service = null
            _serviceConnected.value = false
        }
    }

    suspend fun getInstalledPackages(flags: Int): List<PackageInfo> {
        val userId = android.os.Process.myUserHandle().hashCode()
        val svc = bindService()
        return try {
            val slice = svc.getInstalledPackages(flags, userId)

            @Suppress("UNCHECKED_CAST")
            val list = slice.list as List<PackageInfo>
            list
        } catch (e: RemoteException) {
            throw e.rethrowFromSystemServer()
        }
    }
}
