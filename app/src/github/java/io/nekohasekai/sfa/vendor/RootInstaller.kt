package io.nekohasekai.sfa.vendor

import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.topjohnwu.superuser.ipc.RootService
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.bg.IRootService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object RootInstaller {

    suspend fun checkAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su -c echo test")
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    suspend fun install(apkFile: File) {
        withContext(Dispatchers.IO) {
            bindRootService().use { handle ->
                ParcelFileDescriptor.open(apkFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    handle.service.installPackage(
                        pfd,
                        apkFile.length(),
                        android.os.Process.myUserHandle().hashCode()
                    )
                }
            }
        }
    }

    private suspend fun bindRootService(): RootServiceHandle {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: android.content.ComponentName?, binder: IBinder?) {
                        val svc = if (binder != null && binder.pingBinder()) {
                            IRootService.Stub.asInterface(binder)
                        } else {
                            null
                        }
                        if (svc == null) {
                            continuation.resumeWithException(IllegalStateException("Invalid root service binder"))
                            return
                        }
                        continuation.resume(RootServiceHandle(this, svc))
                    }

                    override fun onServiceDisconnected(name: android.content.ComponentName?) {
                        // Ignored
                    }
                }

                try {
                    val intent = Intent(Application.application, Class.forName("io.nekohasekai.sfa.bg.RootServer"))
                    RootService.bind(intent, conn)
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                    return@suspendCancellableCoroutine
                }

                continuation.invokeOnCancellation {
                    RootService.unbind(conn)
                }
            }
        }
    }

    private data class RootServiceHandle(
        val connection: ServiceConnection,
        val service: IRootService
    ) : java.io.Closeable {
        override fun close() {
            RootService.unbind(connection)
        }
    }
}
