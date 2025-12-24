package io.nekohasekai.sfa.vendor

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import io.nekohasekai.sfa.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object PackageQueryManager {

    val needsPrivilegedQuery: Boolean = false

    private val _queryMode = MutableStateFlow("")
    val queryMode: StateFlow<String> = _queryMode

    val shizukuInstalled: StateFlow<Boolean> = MutableStateFlow(false)
    val shizukuBinderReady: StateFlow<Boolean> = MutableStateFlow(false)
    val shizukuPermissionGranted: StateFlow<Boolean> = MutableStateFlow(false)
    val rootAvailable: StateFlow<Boolean?> = MutableStateFlow(null)
    val rootServiceConnected: StateFlow<Boolean> = MutableStateFlow(false)

    fun isShizukuAvailable(): Boolean = false

    fun registerListeners() {}

    fun unregisterListeners() {}

    fun requestShizukuPermission() {}

    suspend fun checkRootAvailable(): Boolean = false

    fun setQueryMode(mode: String) {
        _queryMode.value = mode
    }

    suspend fun getInstalledPackages(flags: Int): List<PackageInfo> {
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
