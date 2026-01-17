package io.nekohasekai.sfa.xposed.hooks.hidevpnapp

import android.content.pm.ResolveInfo
import android.os.Binder
import android.os.Build
import android.os.Process
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.xposed.HookErrorStore
import io.nekohasekai.sfa.xposed.PrivilegeSettingsStore
import io.nekohasekai.sfa.xposed.VpnAppStore
import io.nekohasekai.sfa.xposed.hooks.SafeMethodHook
import io.nekohasekai.sfa.xposed.hooks.XHook
import java.lang.reflect.Method

class HookPackageManagerGetInstalledPackages(private val classLoader: ClassLoader) : XHook {
    private companion object {
        private const val SOURCE = "HookPMGetInstalledPackages"
        private const val PER_USER_RANGE = 100000
    }

    @Volatile
    private var lastPackageNameClass: Class<*>? = null
    private var getPackageNameMethod: Method? = null

    override fun injectHook() {
        val hooked = ArrayList<String>()
        val sdk = Build.VERSION.SDK_INT
        when {
            // VANILLA_ICE_CREAM
            sdk >= 35 -> {
                hookAppsFilter33Plus(hooked)
                hookArchivedPackageInternal(hooked)
            }
            sdk >= Build.VERSION_CODES.TIRAMISU -> {
                hookAppsFilter33Plus(hooked)
            }
            sdk >= Build.VERSION_CODES.R -> {
                hookAppsFilter30(hooked)
            }
            else -> {
                hookPmsLegacy(hooked)
            }
        }
        if (hooked.isNotEmpty()) {
            HookErrorStore.i(SOURCE, "Hooked hide applist: ${hooked.joinToString()}")
        } else {
            HookErrorStore.w(SOURCE, "Hide applist hook not applied")
        }
    }

    private fun hookAppsFilter33Plus(hooked: MutableList<String>) {
        val cls = XposedHelpers.findClassIfExists("com.android.server.pm.AppsFilterImpl", classLoader)
        if (cls == null) {
            HookErrorStore.e(SOURCE, "Class com.android.server.pm.AppsFilterImpl not found")
            return
        }
        val unhooks = try {
            XposedBridge.hookAllMethods(
                cls,
                "shouldFilterApplication",
                object : SafeMethodHook(SOURCE) {
                    override fun beforeHook(param: MethodHookParam) {
                        val callingUid = param.args[1] as Int
                        val callerPackages = getCallerPackages(callingUid) ?: return
                        val target = param.args[3]!!
                        val targetPackage = extractPackageName(target) ?: return
                        if (shouldHidePackage(callingUid, callerPackages, targetPackage)) {
                            param.result = true
                        }
                    }
                },
            )
        } catch (e: Throwable) {
            HookErrorStore.w(SOURCE, "Skip AppsFilterImpl.shouldFilterApplication: ${e.message}", e)
            emptySet<XC_MethodHook.Unhook>()
        }
        if (unhooks.isNotEmpty()) {
            hooked.add("AppsFilterImpl.shouldFilterApplication")
        }
    }

    private fun hookAppsFilter30(hooked: MutableList<String>) {
        val cls = XposedHelpers.findClassIfExists("com.android.server.pm.AppsFilter", classLoader)
        if (cls == null) {
            HookErrorStore.e(SOURCE, "Class com.android.server.pm.AppsFilter not found")
            return
        }
        val unhooks = try {
            XposedBridge.hookAllMethods(
                cls,
                "shouldFilterApplication",
                object : SafeMethodHook(SOURCE) {
                    override fun beforeHook(param: MethodHookParam) {
                        val callingUid = param.args[0] as Int
                        val callerPackages = getCallerPackages(callingUid) ?: return
                        val target = param.args[2]!!
                        val targetPackage = extractPackageName(target) ?: return
                        if (shouldHidePackage(callingUid, callerPackages, targetPackage)) {
                            param.result = true
                        }
                    }
                },
            )
        } catch (e: Throwable) {
            HookErrorStore.w(SOURCE, "Skip AppsFilter.shouldFilterApplication: ${e.message}", e)
            emptySet<XC_MethodHook.Unhook>()
        }
        if (unhooks.isNotEmpty()) {
            hooked.add("AppsFilter.shouldFilterApplication")
        }
    }

    private fun hookArchivedPackageInternal(hooked: MutableList<String>) {
        val cls = XposedHelpers.findClassIfExists("com.android.server.pm.PackageManagerService", classLoader)
        if (cls == null) {
            HookErrorStore.e(SOURCE, "Class com.android.server.pm.PackageManagerService not found")
            return
        }
        val unhooks = try {
            XposedBridge.hookAllMethods(
                cls,
                "getArchivedPackageInternal",
                object : SafeMethodHook(SOURCE) {
                    override fun beforeHook(param: MethodHookParam) {
                        val callingUid = Binder.getCallingUid()
                        val callerPackages = getCallerPackages(callingUid) ?: return
                        val targetPackage = param.args[0]!!.toString()
                        if (shouldHidePackage(callingUid, callerPackages, targetPackage)) {
                            param.result = null
                        }
                    }
                },
            )
        } catch (e: Throwable) {
            HookErrorStore.w(SOURCE, "Skip PackageManagerService.getArchivedPackageInternal: ${e.message}", e)
            emptySet<XC_MethodHook.Unhook>()
        }
        if (unhooks.isNotEmpty()) {
            hooked.add("PackageManagerService.getArchivedPackageInternal")
        }
    }

    private fun hookPmsLegacy(hooked: MutableList<String>) {
        val cls = XposedHelpers.findClassIfExists("com.android.server.pm.PackageManagerService", classLoader)
        if (cls == null) {
            HookErrorStore.e(SOURCE, "Class com.android.server.pm.PackageManagerService not found")
            return
        }
        val filterHooks = try {
            XposedBridge.hookAllMethods(
                cls,
                "filterAppAccessLPr",
                object : SafeMethodHook(SOURCE) {
                    override fun beforeHook(param: MethodHookParam) {
                        val callingUid = param.args[1] as Int
                        val callerPackages = getCallerPackages(callingUid) ?: return
                        val target = param.args[0]!!
                        val targetPackage = extractPackageName(target) ?: return
                        if (shouldHidePackage(callingUid, callerPackages, targetPackage)) {
                            param.result = true
                        }
                    }
                },
            )
        } catch (e: Throwable) {
            HookErrorStore.w(SOURCE, "Skip PackageManagerService.filterAppAccessLPr: ${e.message}", e)
            emptySet<XC_MethodHook.Unhook>()
        }
        if (filterHooks.isNotEmpty()) {
            hooked.add("PackageManagerService.filterAppAccessLPr")
        }

        val resolutionHooks = try {
            XposedBridge.hookAllMethods(
                cls,
                "applyPostResolutionFilter",
                object : SafeMethodHook(SOURCE) {
                    override fun afterHook(param: MethodHookParam) {
                        val callingUid = param.args[3] as Int
                        val callerPackages = getCallerPackages(callingUid) ?: return
                        val rawResult = param.result ?: return
                        when (rawResult) {
                            is MutableCollection<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                val result = rawResult as MutableCollection<ResolveInfo>
                                val iterator = result.iterator()
                                while (iterator.hasNext()) {
                                    val info = iterator.next()
                                    val targetPackage = with(info) {
                                        activityInfo?.packageName
                                            ?: serviceInfo?.packageName
                                            ?: providerInfo?.packageName
                                            ?: resolvePackageName
                                    }
                                    if (targetPackage != null &&
                                        shouldHidePackage(callingUid, callerPackages, targetPackage)
                                    ) {
                                        iterator.remove()
                                    }
                                }
                            }
                            is List<*> -> {
                                val filtered = rawResult.filterNot { item ->
                                    val info = item as? ResolveInfo ?: return@filterNot false
                                    val targetPackage = with(info) {
                                        activityInfo?.packageName
                                            ?: serviceInfo?.packageName
                                            ?: providerInfo?.packageName
                                            ?: resolvePackageName
                                    }
                                    targetPackage != null &&
                                        shouldHidePackage(callingUid, callerPackages, targetPackage)
                                }
                                param.result = filtered
                            }
                        }
                    }
                },
            )
        } catch (e: Throwable) {
            HookErrorStore.w(SOURCE, "Skip PackageManagerService.applyPostResolutionFilter: ${e.message}", e)
            emptySet<XC_MethodHook.Unhook>()
        }
        if (resolutionHooks.isNotEmpty()) {
            hooked.add("PackageManagerService.applyPostResolutionFilter")
        }
    }

    private fun getCallerPackages(callingUid: Int): List<String>? {
        if (callingUid < Process.FIRST_APPLICATION_UID) {
            return null
        }
        if (!PrivilegeSettingsStore.shouldHideUid(callingUid)) {
            return null
        }
        val packages = VpnAppStore.getPackagesForUid(callingUid)
        if (packages.isEmpty()) {
            return null
        }
        if (packages.contains(BuildConfig.APPLICATION_ID)) {
            return null
        }
        return packages
    }

    private fun shouldHidePackage(
        callingUid: Int,
        callerPackages: List<String>,
        targetPackage: String,
    ): Boolean {
        if (callerPackages.contains(targetPackage)) {
            return false
        }
        val userId = callingUid / PER_USER_RANGE
        if (!VpnAppStore.isVpnPackage(targetPackage, userId)) {
            return false
        }
        return true
    }

    private fun extractPackageName(arg: Any?): String? {
        if (arg == null) return null
        try {
            val argClass = arg.javaClass
            val method = if (lastPackageNameClass == argClass && getPackageNameMethod != null) {
                getPackageNameMethod!!
            } else {
                argClass.getMethod("getPackageName").also {
                    lastPackageNameClass = argClass
                    getPackageNameMethod = it
                }
            }
            val result = method.invoke(arg) as String?
            if (!result.isNullOrEmpty()) {
                return result
            }
        } catch (_: NoSuchMethodException) {
        } catch (e: Throwable) {
            HookErrorStore.w(SOURCE, "extractPackageName via getPackageName() failed for ${arg.javaClass.name}", e)
        }
        val fields = arrayOf("packageName", "mPackageName", "name", "mName")
        for (name in fields) {
            val field = XposedHelpers.findFieldIfExists(arg.javaClass, name) ?: continue
            try {
                val result = field.get(arg) as String?
                if (!result.isNullOrEmpty()) {
                    return result
                }
            } catch (e: Throwable) {
                HookErrorStore.w(SOURCE, "extractPackageName via field $name failed for ${arg.javaClass.name}", e)
            }
        }
        HookErrorStore.w(SOURCE, "extractPackageName failed for ${arg.javaClass.name}")
        return null
    }
}
