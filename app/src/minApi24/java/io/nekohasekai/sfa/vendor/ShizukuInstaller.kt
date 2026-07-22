package io.nekohasekai.sfa.vendor

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File

object ShizukuInstaller {

    private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) {
        false
    }

    fun checkPermission(): Boolean = try {
        if (Shizuku.isPreV11()) {
            false
        } else {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    } catch (e: Exception) {
        false
    }

    fun requestPermission() {
        if (!Shizuku.isPreV11()) {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        }
    }

    private fun isRunningAsRoot(): Boolean = try {
        Shizuku.getUid() == 0
    } catch (e: Exception) {
        false
    }

    suspend fun install(apkFile: File) = withContext(Dispatchers.IO) {
        val service = ShizukuPrivilegedServiceClient.getService()
        val userId = if (isRunningAsRoot()) Process.myUserHandle().hashCode() else 0
        ParcelFileDescriptor.open(apkFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            service.installPackage(pfd, apkFile.length(), userId)
        }
    }
}
