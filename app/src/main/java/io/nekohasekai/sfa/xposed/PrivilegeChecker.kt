package io.nekohasekai.sfa.xposed

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object PrivilegeChecker {
    private const val PER_USER_RANGE = 100000
    private val privilegedPermissions = arrayOf(
        "android.permission.NETWORK_STACK",
        "android.permission.MAINLINE_NETWORK_STACK",
        "android.permission.NETWORK_SETTINGS",
        "android.permission.CONNECTIVITY_INTERNAL",
        "android.permission.CONTROL_VPN",
        "android.permission.CONTROL_ALWAYS_ON_VPN",
    )
    private val exemptPackages = emptySet<String>()
    private val exemptCache = ConcurrentHashMap<Int, Boolean>()
    private val privilegedCache = ConcurrentHashMap<Int, Boolean>()

    private val appGlobalsClass by lazy { Class.forName("android.app.AppGlobals") }
    private val getPackageManagerMethod by lazy { appGlobalsClass.getMethod("getPackageManager") }
    private var getPackagesForUidMethod: Method? = null
    private var checkUidPermissionMethod: Method? = null
    private var getApplicationInfoMethodLong: Method? = null
    private var getApplicationInfoMethodInt: Method? = null

    fun isPrivilegedUid(uid: Int): Boolean {
        if (uid < Process.FIRST_APPLICATION_UID) {
            return true
        }
        val cached = privilegedCache[uid]
        if (cached != null) {
            return cached
        }
        if (isExemptUid(uid)) {
            privilegedCache[uid] = true
            return true
        }
        val packages = getPackagesForUid(uid)
        val pm = getPackageManager()
        if (pm != null && packages.isNotEmpty()) {
            val userId = uid / PER_USER_RANGE
            for (pkg in packages) {
                val appInfo = getApplicationInfo(pm, pkg, userId)
                if (appInfo != null && isSystemApp(appInfo)) {
                    privilegedCache[uid] = true
                    return true
                }
            }
            val checkMethod = checkUidPermissionMethod ?: run {
                pm.javaClass.getMethod(
                    "checkUidPermission",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                ).also { checkUidPermissionMethod = it }
            }
            for (permission in privilegedPermissions) {
                val result = try {
                    checkMethod.invoke(pm, permission, uid) as? Int
                } catch (_: Throwable) {
                    null
                }
                if (result == PackageManager.PERMISSION_GRANTED) {
                    privilegedCache[uid] = true
                    return true
                }
            }
        }
        privilegedCache[uid] = false
        return false
    }

    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        val flags = appInfo.flags
        return flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
            flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
    }

    private fun isExemptUid(uid: Int): Boolean {
        if (exemptPackages.isEmpty()) {
            return false
        }
        val cached = exemptCache[uid]
        if (cached != null) {
            return cached
        }
        val packages = getPackagesForUid(uid)
        val isExempt = packages.any { it in exemptPackages }
        exemptCache[uid] = isExempt
        return isExempt
    }

    private fun getPackagesForUid(uid: Int): List<String> {
        val pm = getPackageManager() ?: return emptyList()
        return try {
            val method = getPackagesForUidMethod ?: run {
                pm.javaClass.getMethod("getPackagesForUid", Int::class.javaPrimitiveType).also {
                    getPackagesForUidMethod = it
                }
            }
            val result = method.invoke(pm, uid)
            when (result) {
                is Array<*> -> result.filterIsInstance<String>()
                is List<*> -> result.filterIsInstance<String>()
                else -> emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun getPackageManager(): Any? = try {
        getPackageManagerMethod.invoke(null)
    } catch (_: Throwable) {
        null
    }

    private fun getApplicationInfo(pm: Any, pkg: String, userId: Int): ApplicationInfo? = try {
        val method = getApplicationInfoMethodInt ?: run {
            pm.javaClass.getMethod(
                "getApplicationInfo",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).also { getApplicationInfoMethodInt = it }
        }
        method.invoke(pm, pkg, 0, userId) as? ApplicationInfo
    } catch (_: Throwable) {
        try {
            val method = getApplicationInfoMethodLong ?: run {
                pm.javaClass.getMethod(
                    "getApplicationInfo",
                    String::class.java,
                    Long::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                ).also { getApplicationInfoMethodLong = it }
            }
            method.invoke(pm, pkg, 0L, userId) as? ApplicationInfo
        } catch (_: Throwable) {
            null
        }
    }
}
