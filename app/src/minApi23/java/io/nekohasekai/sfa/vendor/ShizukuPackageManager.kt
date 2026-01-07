package io.nekohasekai.sfa.vendor

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Process
import io.nekohasekai.sfa.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

object ShizukuPackageManager {

    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    private val _shizukuInstalled = MutableStateFlow(false)
    val shizukuInstalled: StateFlow<Boolean> = _shizukuInstalled

    private val _binderReady = MutableStateFlow(false)
    val binderReady: StateFlow<Boolean> = _binderReady

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        _binderReady.value = true
        _permissionGranted.value = checkPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _binderReady.value = false
        _permissionGranted.value = false
        ShizukuPrivilegedServiceClient.reset()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        _permissionGranted.value = grantResult == PackageManager.PERMISSION_GRANTED
        _binderReady.value = isAvailable()
    }

    fun registerListeners() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        refresh()
    }

    fun isShizukuInstalled(): Boolean {
        return try {
            Application.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun unregisterListeners() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    fun isAvailable(): Boolean = ShizukuInstaller.isAvailable()

    fun checkPermission(): Boolean = ShizukuInstaller.checkPermission()

    fun requestPermission() = ShizukuInstaller.requestPermission()

    fun refresh() {
        _shizukuInstalled.value = isShizukuInstalled()
        _binderReady.value = isAvailable()
        _permissionGranted.value = checkPermission()
    }

    suspend fun getInstalledPackages(flags: Int): List<PackageInfo> = withContext(Dispatchers.IO) {
        val service = ShizukuPrivilegedServiceClient.getService()
        val userId = Process.myUserHandle().hashCode()
        val slice = service.getInstalledPackages(flags, userId)
        @Suppress("UNCHECKED_CAST")
        slice.list as List<PackageInfo>
    }
}
