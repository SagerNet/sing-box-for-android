package io.nekohasekai.sfa.vendor

import android.Manifest
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PrivilegedAccessRequiredException(message: String) : Exception(message)

object PackageQueryManager {

    private const val TAG = "PackageQueryManager"

    val needsPrivilegedQuery: Boolean by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Check if QUERY_ALL_PACKAGES is declared in manifest
            val packageInfo = Application.packageManager.getPackageInfo(
                Application.application.packageName,
                PackageManager.GET_PERMISSIONS
            )
            val hasPermission = packageInfo.requestedPermissions?.contains(
                Manifest.permission.QUERY_ALL_PACKAGES
            ) == true
            !hasPermission
        } else {
            false
        }
    }

    private val _queryMode = MutableStateFlow(Settings.perAppProxyPackageQueryMode)
    val queryMode: StateFlow<String> = _queryMode

    val shizukuInstalled: StateFlow<Boolean> get() = ShizukuPackageManager.shizukuInstalled
    val shizukuBinderReady: StateFlow<Boolean> get() = ShizukuPackageManager.binderReady
    val shizukuPermissionGranted: StateFlow<Boolean> get() = ShizukuPackageManager.permissionGranted
    val rootAvailable: StateFlow<Boolean?> get() = RootPackageManager.rootAvailable
    val rootServiceConnected: StateFlow<Boolean> get() = RootPackageManager.serviceConnected

    fun isShizukuAvailable(): Boolean =
        ShizukuPackageManager.isAvailable() && ShizukuPackageManager.checkPermission()

    fun registerListeners() {
        ShizukuPackageManager.registerListeners()
        _queryMode.value = Settings.perAppProxyPackageQueryMode
    }

    fun unregisterListeners() {
        ShizukuPackageManager.unregisterListeners()
    }

    fun requestShizukuPermission() {
        ShizukuPackageManager.requestPermission()
    }

    suspend fun checkRootAvailable(): Boolean {
        return RootPackageManager.checkRootAvailable()
    }

    fun setQueryMode(mode: String) {
        _queryMode.value = mode
    }

    suspend fun getInstalledPackages(flags: Int): List<PackageInfo> {
        if (!needsPrivilegedQuery) {
            return getPackagesViaPackageManager(flags)
        }

        val mode = _queryMode.value

        if (mode == Settings.PACKAGE_QUERY_MODE_ROOT) {
            if (rootAvailable.value != true) {
                val isAvailable = RootPackageManager.checkRootAvailable()
                if (!isAvailable) {
                    throw PrivilegedAccessRequiredException("ROOT access required")
                }
            }
            return RootPackageManager.getInstalledPackages(flags)
        }

        if (!isShizukuAvailable()) {
            throw PrivilegedAccessRequiredException("Shizuku access required")
        }
        return ShizukuPackageManager.getInstalledPackages(flags)
    }

    private fun getPackagesViaPackageManager(flags: Int): List<PackageInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Application.packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            Application.packageManager.getInstalledPackages(flags)
        }
    }
}
