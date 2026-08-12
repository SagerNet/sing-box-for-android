package io.nekohasekai.sfa.xposed.hooks.hidevpn

import android.net.Network
import android.net.NetworkCapabilities
import android.os.Binder
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.nekohasekai.sfa.xposed.HookErrorStore
import io.nekohasekai.sfa.xposed.VpnSanitizer
import io.nekohasekai.sfa.xposed.hooks.SafeMethodHook

class HookConnectivityManagerGetNetworkCapabilities(private val helper: ConnectivityServiceHookHelper) {
    private companion object {
        private const val SOURCE = "HookGetNetworkCapabilities"
    }

    fun install() {
        // Hook networkCapabilitiesRestrictedForCallerPermissions (API 28+)
        if (helper.sdkInt >= 28) {
            try {
                hookNetworkCapabilitiesRestricted()
            } catch (e: Throwable) {
                HookErrorStore.w(SOURCE, "hookNetworkCapabilitiesRestricted failed: ${e.message}", e)
            }
        }

        // Hook getNetworkCapabilities based on API level
        when {
            helper.sdkInt >= 31 -> {
                try {
                    hookGetNetworkCapabilitiesV12()
                } catch (e: Throwable) {
                    HookErrorStore.w(SOURCE, "hookGetNetworkCapabilitiesV12 failed: ${e.message}", e)
                    try {
                        hookGetNetworkCapabilitiesV11()
                    } catch (e2: Throwable) {
                        HookErrorStore.e(SOURCE, "hookGetNetworkCapabilitiesV11 failed: ${e2.message}", e2)
                    }
                }
            }
            helper.sdkInt >= 30 -> {
                try {
                    hookGetNetworkCapabilitiesV11()
                } catch (e: Throwable) {
                    HookErrorStore.w(SOURCE, "hookGetNetworkCapabilitiesV11 failed: ${e.message}", e)
                    try {
                        hookGetNetworkCapabilitiesV8()
                    } catch (e2: Throwable) {
                        HookErrorStore.e(SOURCE, "hookGetNetworkCapabilitiesV8 failed: ${e2.message}", e2)
                    }
                }
            }
            else -> {
                try {
                    hookGetNetworkCapabilitiesV8()
                } catch (e: Throwable) {
                    HookErrorStore.e(SOURCE, "hookGetNetworkCapabilitiesV8 failed: ${e.message}", e)
                }
            }
        }

        // Hook createWithSensitiveInfoSanitizedIfNecessaryWhenParceled (API 31+)
        if (helper.sdkInt >= 31) {
            try {
                hookCreateWithSanitizedInfo()
            } catch (e: Throwable) {
                HookErrorStore.w(SOURCE, "hookCreateWithSanitizedInfo failed: ${e.message}", e)
            }
        }
    }

    private fun hookNetworkCapabilitiesRestricted() {
        // Merged into createWithSensitiveInfoSanitizedIfNecessaryWhenParceled since the Android 16
        // QPR2 connectivity module.
        val method = XposedHelpers.findMethodExactIfExists(
            helper.cls,
            "networkCapabilitiesRestrictedForCallerPermissions",
            NetworkCapabilities::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        ) ?: return
        XposedBridge.hookMethod(
            method,
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    val callerUid = param.args[2] as Int
                    val nc = param.result as? NetworkCapabilities ?: return
                    if (!nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
                    if (!VpnSanitizer.shouldHide(callerUid)) return
                    param.result = VpnSanitizer.sanitizeNetworkCapabilities(nc)
                }
            },
        )
    }

    private fun hookGetNetworkCapabilitiesV8() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "getNetworkCapabilities",
            Network::class.java,
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    sanitizeNetworkCapabilitiesResult(param)
                }
            },
        )
    }

    private fun hookGetNetworkCapabilitiesV11() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "getNetworkCapabilities",
            Network::class.java,
            String::class.java,
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    sanitizeNetworkCapabilitiesResult(param)
                }
            },
        )
    }

    private fun hookGetNetworkCapabilitiesV12() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "getNetworkCapabilities",
            Network::class.java,
            String::class.java,
            String::class.java,
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    sanitizeNetworkCapabilitiesResult(param)
                }
            },
        )
    }

    private fun sanitizeNetworkCapabilitiesResult(param: de.robv.android.xposed.XC_MethodHook.MethodHookParam) {
        val uid = Binder.getCallingUid()
        if (!helper.shouldHide(param.thisObject, uid)) return
        val nc = param.result as? NetworkCapabilities ?: return
        if (!nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
        param.result = VpnSanitizer.sanitizeNetworkCapabilities(nc)
    }

    private fun hookCreateWithSanitizedInfo() {
        // Renamed from createWithLocationInfoSanitizedIfNecessaryWhenParceled in the Android 16
        // QPR2 connectivity module.
        val method = XposedHelpers.findMethodExactIfExists(
            helper.cls,
            "createWithSensitiveInfoSanitizedIfNecessaryWhenParceled",
            NetworkCapabilities::class.java,
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java,
            String::class.java,
        ) ?: XposedHelpers.findMethodExact(
            helper.cls,
            "createWithLocationInfoSanitizedIfNecessaryWhenParceled",
            NetworkCapabilities::class.java,
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java,
            String::class.java,
        )
        XposedBridge.hookMethod(
            method,
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    val callerUid = param.args[3] as Int
                    if (!helper.shouldHide(param.thisObject, callerUid)) return
                    val nc = param.result as? NetworkCapabilities ?: return
                    if (!nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
                    param.result = VpnSanitizer.sanitizeNetworkCapabilities(nc)
                }
            },
        )
    }
}
