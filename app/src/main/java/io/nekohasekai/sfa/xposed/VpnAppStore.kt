package io.nekohasekai.sfa.xposed

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Binder
import android.os.SystemClock
import io.nekohasekai.sfa.BuildConfig
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object VpnAppStore {
    private const val PER_USER_RANGE = 100000
    private const val REFRESH_INTERVAL_MS = 60_000L
    private const val UID_CACHE_MS = 5_000L

    private data class CacheEntry<T>(val atMs: Long, val value: T)

    private val vpnPackagesByUser = ConcurrentHashMap<Int, CacheEntry<Set<String>>>()
    private val uidVpnCache = ConcurrentHashMap<Int, CacheEntry<Boolean>>()
    private val uidPackagesCache = ConcurrentHashMap<Int, CacheEntry<List<String>>>()

    private val appGlobalsClass by lazy { Class.forName("android.app.AppGlobals") }
    private val getPackageManagerMethod by lazy { appGlobalsClass.getMethod("getPackageManager") }

    @Volatile
    private var pmClass: Class<*>? = null
    private var getPackagesForUidMethod: Method? = null
    private var getInstalledPackagesMethodLong: Method? = null
    private var getInstalledPackagesMethodInt: Method? = null
    private var getListMethod: Method? = null

    fun isVpnUid(uid: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        val cached = uidVpnCache[uid]
        if (cached != null && now - cached.atMs < UID_CACHE_MS) {
            return cached.value
        }
        val callerPackages = getPackagesForUid(uid)
        val userId = uid / PER_USER_RANGE
        val vpnSet = getVpnPackages(userId)
        val result = callerPackages.any { vpnSet.contains(it) }
        uidVpnCache[uid] = CacheEntry(now, result)
        return result
    }

    fun isVpnPackage(packageName: String, userId: Int): Boolean = getVpnPackages(userId).contains(packageName)

    fun isVpnUidExcludeSelf(uid: Int): Boolean {
        val packages = getPackagesForUid(uid)
        if (packages.contains(BuildConfig.APPLICATION_ID)) {
            return false
        }
        val userId = uid / PER_USER_RANGE
        val vpnSet = getVpnPackages(userId)
        return packages.any { vpnSet.contains(it) }
    }

    fun getPackagesForUid(uid: Int): List<String> {
        val now = SystemClock.uptimeMillis()
        val cached = uidPackagesCache[uid]
        if (cached != null && now - cached.atMs < UID_CACHE_MS) {
            return cached.value
        }
        val result = binderLocalScope {
            val pm = getPackageManager() ?: return@binderLocalScope emptyList<String>()
            try {
                val method = getPackagesForUidMethod ?: run {
                    pm.javaClass.getMethod("getPackagesForUid", Int::class.javaPrimitiveType).also {
                        getPackagesForUidMethod = it
                    }
                }
                when (val raw = method.invoke(pm, uid)) {
                    is Array<*> -> raw.filterIsInstance<String>()
                    is List<*> -> raw.filterIsInstance<String>()
                    else -> emptyList()
                }
            } catch (e: Throwable) {
                HookErrorStore.e("VpnAppStore", "getPackagesForUid failed for uid=$uid", e)
                emptyList()
            }
        }
        uidPackagesCache[uid] = CacheEntry(now, result)
        return result
    }

    private fun getVpnPackages(userId: Int): Set<String> {
        val now = SystemClock.uptimeMillis()
        val cached = vpnPackagesByUser[userId]
        if (cached != null && now - cached.atMs < REFRESH_INTERVAL_MS) {
            return cached.value
        }
        val refreshed = scanVpnPackages(userId)
        vpnPackagesByUser[userId] = CacheEntry(now, refreshed)
        uidVpnCache.clear()
        return refreshed
    }

    private fun scanVpnPackages(userId: Int): Set<String> {
        return binderLocalScope {
            val pm = getPackageManager() ?: return@binderLocalScope emptySet()
            val flags = PackageManager.MATCH_DISABLED_COMPONENTS or
                PackageManager.MATCH_DIRECT_BOOT_AWARE or
                PackageManager.MATCH_DIRECT_BOOT_UNAWARE or
                PackageManager.GET_SERVICES
            val packages = getInstalledPackagesCompat(pm, flags.toLong(), userId)
            val result = HashSet<String>()
            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                if (isSystemApp(appInfo)) continue
                val services = pkg.services ?: continue
                if (services.any { it.permission == Manifest.permission.BIND_VPN_SERVICE }) {
                    result.add(pkg.packageName)
                }
            }
            HookErrorStore.d("VpnAppStore", "VPN apps refreshed user=$userId count=${result.size}")
            result
        }
    }

    private fun getInstalledPackagesCompat(pm: Any, flags: Long, userId: Int): List<PackageInfo> {
        val result = try {
            val method = getInstalledPackagesMethodLong ?: run {
                pm.javaClass.getMethod(
                    "getInstalledPackages",
                    Long::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                ).also { getInstalledPackagesMethodLong = it }
            }
            method.invoke(pm, flags, userId)
        } catch (_: Throwable) {
            try {
                val method = getInstalledPackagesMethodInt ?: run {
                    pm.javaClass.getMethod(
                        "getInstalledPackages",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                    ).also { getInstalledPackagesMethodInt = it }
                }
                method.invoke(pm, flags.toInt(), userId)
            } catch (e: Throwable) {
                HookErrorStore.e("VpnAppStore", "getInstalledPackages failed", e)
                return emptyList()
            }
        }
        return unwrapParceledListSlice(result)
    }

    private fun isSystemApp(info: ApplicationInfo): Boolean = info.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
        info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0

    private fun getPackageManager(): Any? = try {
        getPackageManagerMethod.invoke(null)
    } catch (e: Throwable) {
        HookErrorStore.e("VpnAppStore", "getPackageManager failed", e)
        null
    }

    private inline fun <T> binderLocalScope(block: () -> T): T {
        val token = Binder.clearCallingIdentity()
        return try {
            block()
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private fun unwrapParceledListSlice(raw: Any?): List<PackageInfo> {
        if (raw == null) return emptyList()
        if (raw is List<*>) {
            return raw.filterIsInstance<PackageInfo>()
        }
        return try {
            val method = getListMethod ?: run {
                raw.javaClass.getMethod("getList").also { getListMethod = it }
            }
            val list = method.invoke(raw)
            if (list is List<*>) {
                list.filterIsInstance<PackageInfo>()
            } else {
                emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
