package io.nekohasekai.sfa.xposed.hooks

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Binder
import android.os.Parcel
import de.robv.android.xposed.XposedHelpers
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.bg.PackageEntry
import io.nekohasekai.sfa.bg.ParceledListSlice
import io.nekohasekai.sfa.xposed.HookErrorStore
import io.nekohasekai.sfa.xposed.HookStatusKeys
import io.nekohasekai.sfa.xposed.HookStatusStore
import io.nekohasekai.sfa.xposed.PrivilegeSettingsStore

class HookIConnectivityManagerOnTransact(private val classLoader: ClassLoader, private val context: Context?) : XHook {
    private companion object {
        private const val SOURCE = "HookIConnectivityManagerOnTransact"
    }

    override fun injectHook() {
        val stub = XposedHelpers.findClass("android.net.IConnectivityManager\$Stub", classLoader)
        val descriptor = XposedHelpers.getStaticObjectField(stub, "DESCRIPTOR") as String
        XposedHelpers.findAndHookMethod(
            stub,
            "onTransact",
            Int::class.javaPrimitiveType,
            Parcel::class.java,
            Parcel::class.java,
            Int::class.javaPrimitiveType,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val code = param.args[0] as Int
                    if (code != HookStatusKeys.TRANSACTION_STATUS &&
                        code != HookStatusKeys.TRANSACTION_UPDATE_PRIVILEGE_SETTINGS &&
                        code != HookStatusKeys.TRANSACTION_GET_ERRORS &&
                        code != HookStatusKeys.TRANSACTION_GET_INSTALLED_PACKAGES
                    ) {
                        return
                    }
                    val data = param.args[1] as Parcel
                    val reply = param.args[2] as Parcel?
                    try {
                        data.enforceInterface(descriptor)
                    } catch (e: Throwable) {
                        HookErrorStore.e(SOURCE, "IConnectivityManager transact bad interface", e)
                        reply?.writeException(SecurityException("bad interface"))
                        param.result = true
                        return
                    }
                    if (!isCallerAllowed()) {
                        reply!!.writeException(SecurityException("unauthorized"))
                        param.result = true
                        return
                    }
                    if (code == HookStatusKeys.TRANSACTION_STATUS) {
                        val status = HookStatusStore.snapshot()
                        reply!!.writeNoException()
                        reply.writeInt(if (status.active) 1 else 0)
                        reply.writeLong(status.lastPatchedAt)
                        reply.writeInt(status.version)
                        reply.writeInt(status.systemPid)
                        param.result = true
                        return
                    }
                    if (code == HookStatusKeys.TRANSACTION_GET_ERRORS) {
                        val hasWarnings = HookErrorStore.hasWarnings()
                        val entries = HookErrorStore.snapshot()
                        reply!!.writeNoException()
                        reply.writeInt(if (hasWarnings) 1 else 0)
                        ParceledListSlice(entries).writeToParcel(reply, 0)
                        param.result = true
                        return
                    }
                    if (code == HookStatusKeys.TRANSACTION_GET_INSTALLED_PACKAGES) {
                        val flags = data.readLong()
                        val userId = data.readInt()
                        val packages = getInstalledPackages(flags, userId)
                        reply!!.writeNoException()
                        ParceledListSlice(packages).writeToParcel(reply, 0)
                        param.result = true
                        return
                    }
                    val enabled = data.readInt() != 0
                    val slice = ParceledListSlice.CREATOR.createFromParcel(data, PackageEntry::class.java.classLoader)
                    val packages = HashSet<String>()
                    for (entry in slice.list) {
                        if (entry is PackageEntry) {
                            packages.add(entry.packageName)
                        }
                    }
                    var renameEnabled = false
                    var prefix = "en"
                    if (data.dataAvail() >= 4) {
                        renameEnabled = data.readInt() != 0
                        if (data.dataAvail() > 0) {
                            prefix = data.readString() ?: "en"
                        }
                    }
                    PrivilegeSettingsStore.update(enabled, packages, renameEnabled, prefix)
                    reply!!.writeNoException()
                    param.result = true
                }
            },
        )
        HookErrorStore.i(SOURCE, "Hooked IConnectivityManager.onTransact")
    }

    private fun isCallerAllowed(): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == 0) return true
        val pm = context?.packageManager
        if (pm == null) {
            HookErrorStore.e(SOURCE, "isCallerAllowed: context or packageManager is null, uid=$uid")
            return false
        }
        return try {
            val packages = pm.getPackagesForUid(uid)
            if (packages == null) {
                HookErrorStore.w(SOURCE, "isCallerAllowed: getPackagesForUid returned null for uid=$uid")
                return false
            }
            packages.any { it == BuildConfig.APPLICATION_ID }
        } catch (e: Throwable) {
            HookErrorStore.e(SOURCE, "isCallerAllowed failed for uid=$uid", e)
            false
        }
    }

    private fun getInstalledPackages(flags: Long, userId: Int): List<PackageInfo> {
        return binderLocalScope {
            val pm = getPackageManager() ?: return@binderLocalScope emptyList()
            getInstalledPackagesCompat(pm, flags, userId)
        }
    }

    private inline fun <T> binderLocalScope(block: () -> T): T {
        val token = Binder.clearCallingIdentity()
        return try {
            block()
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private fun getPackageManager(): Any? = try {
        val appGlobals = Class.forName("android.app.AppGlobals")
        val method = appGlobals.getMethod("getPackageManager")
        method.invoke(null)
    } catch (e: Throwable) {
        HookErrorStore.e(SOURCE, "getPackageManager failed", e)
        null
    }

    private fun getInstalledPackagesCompat(pm: Any, flags: Long, userId: Int): List<PackageInfo> {
        val result = try {
            val method = pm.javaClass.getMethod(
                "getInstalledPackages",
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            method.invoke(pm, flags, userId)
        } catch (_: Throwable) {
            try {
                val method = pm.javaClass.getMethod(
                    "getInstalledPackages",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                method.invoke(pm, flags.toInt(), userId)
            } catch (e: Throwable) {
                HookErrorStore.e(SOURCE, "getInstalledPackages failed", e)
                return emptyList()
            }
        }
        return unwrapParceledListSlice(result)
    }

    private fun unwrapParceledListSlice(raw: Any?): List<PackageInfo> {
        if (raw == null) return emptyList()
        if (raw is List<*>) {
            return raw.filterIsInstance<PackageInfo>()
        }
        return try {
            val method = raw.javaClass.getMethod("getList")
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
