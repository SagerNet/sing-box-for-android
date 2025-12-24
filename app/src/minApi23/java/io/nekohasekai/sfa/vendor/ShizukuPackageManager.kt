package io.nekohasekai.sfa.vendor

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import io.nekohasekai.sfa.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

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
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        _permissionGranted.value = grantResult == PackageManager.PERMISSION_GRANTED
    }

    fun registerListeners() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        _shizukuInstalled.value = isShizukuInstalled()
        _binderReady.value = isAvailable()
        _permissionGranted.value = checkPermission()
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

    fun getInstalledPackages(flags: Int): List<PackageInfo> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }

        val packageManagerBinder = SystemServiceHelper.getSystemService("package")
        val wrappedBinder = ShizukuBinderWrapper(packageManagerBinder)

        val iPackageManagerClass = Class.forName("android.content.pm.IPackageManager")
        val stubClass = Class.forName("android.content.pm.IPackageManager\$Stub")
        val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
        val iPackageManager = asInterfaceMethod.invoke(null, wrappedBinder)

        val userId = android.os.Process.myUserHandle().hashCode()

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val method = iPackageManagerClass.getMethod(
                "getInstalledPackages",
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.invoke(iPackageManager, flags.toLong(), userId)
        } else {
            val method = iPackageManagerClass.getMethod(
                "getInstalledPackages",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.invoke(iPackageManager, flags, userId)
        }

        return extractPackageList(result)
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractPackageList(parceledListSlice: Any?): List<PackageInfo> {
        if (parceledListSlice == null) return emptyList()

        val getListMethod = parceledListSlice.javaClass.getMethod("getList")
        val list = getListMethod.invoke(parceledListSlice) as? List<*>
        return list?.filterIsInstance<PackageInfo>() ?: emptyList()
    }
}
