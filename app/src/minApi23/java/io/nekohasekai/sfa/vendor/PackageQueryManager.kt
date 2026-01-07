package io.nekohasekai.sfa.vendor

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.bg.RootClient
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.utils.HookStatusClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object PackageQueryManager {

    val strategy: PackageQueryStrategy
        get() = when {
            HookStatusClient.status.value?.active == true -> PackageQueryStrategy.ForcedRoot
            BuildConfig.FLAVOR == "play" -> PackageQueryStrategy.UserSelected(queryMode.value)
            else -> PackageQueryStrategy.Direct
        }

    val showModeSelector: Boolean
        get() = strategy is PackageQueryStrategy.UserSelected

    private val _queryMode = MutableStateFlow(Settings.perAppProxyPackageQueryMode)
    val queryMode: StateFlow<String> = _queryMode

    val shizukuInstalled: StateFlow<Boolean> get() = ShizukuPackageManager.shizukuInstalled
    val shizukuBinderReady: StateFlow<Boolean> get() = ShizukuPackageManager.binderReady
    val shizukuPermissionGranted: StateFlow<Boolean> get() = ShizukuPackageManager.permissionGranted
    val rootAvailable: StateFlow<Boolean?> get() = RootClient.rootAvailable
    val rootServiceConnected: StateFlow<Boolean> get() = RootClient.serviceConnected

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

    fun refreshShizukuState() {
        ShizukuPackageManager.refresh()
    }

    suspend fun checkRootAvailable(): Boolean {
        return RootClient.checkRootAvailable()
    }

    fun setQueryMode(mode: String) {
        _queryMode.value = mode
    }

    suspend fun getInstalledPackages(flags: Int): List<PackageInfo> {
        return when (val s = strategy) {
            is PackageQueryStrategy.ForcedRoot -> RootClient.getInstalledPackages(flags)
            is PackageQueryStrategy.UserSelected -> when (s.mode) {
                Settings.PACKAGE_QUERY_MODE_ROOT -> RootClient.getInstalledPackages(flags)
                else -> ShizukuPackageManager.getInstalledPackages(flags)
            }
            is PackageQueryStrategy.Direct -> getPackagesViaPackageManager(flags)
        }
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
