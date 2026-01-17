package io.nekohasekai.sfa.vendor

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.bg.IShizukuService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ShizukuPrivilegedServiceClient {

    private val serviceMutex = Mutex()
    private var service: IShizukuService? = null
    private var connection: ServiceConnection? = null

    private val args = Shizuku.UserServiceArgs(
        ComponentName(Application.application, ShizukuPrivilegedService::class.java),
    )
        .tag("sfa-privileged")
        .processNameSuffix("privileged")
        .version(BuildConfig.VERSION_CODE)
        .debuggable(BuildConfig.DEBUG)

    suspend fun getService(): IShizukuService = serviceMutex.withLock {
        service?.let { return it }
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        val svc = if (binder != null && binder.pingBinder()) {
                            IShizukuService.Stub.asInterface(binder)
                        } else {
                            null
                        }
                        if (svc == null) {
                            continuation.resumeWithException(IllegalStateException("Invalid Shizuku service binder"))
                            return
                        }
                        service = svc
                        connection = this
                        continuation.resume(svc)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        service = null
                        connection = null
                    }
                }

                try {
                    Shizuku.bindUserService(args, conn)
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                    return@suspendCancellableCoroutine
                }

                continuation.invokeOnCancellation {
                    try {
                        Shizuku.unbindUserService(args, conn, false)
                    } catch (_: Throwable) {
                        // Ignore
                    }
                }
            }
        }
    }

    fun reset() {
        val conn = connection ?: return
        service = null
        connection = null
        try {
            Shizuku.unbindUserService(args, conn, false)
        } catch (_: Throwable) {
            // Ignore
        }
    }
}
