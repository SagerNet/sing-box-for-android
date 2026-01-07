package io.nekohasekai.sfa.xposed.hooks.hidevpn

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Binder
import de.robv.android.xposed.XposedHelpers
import io.nekohasekai.sfa.xposed.HookErrorStore
import io.nekohasekai.sfa.xposed.VpnSanitizer
import io.nekohasekai.sfa.xposed.hooks.SafeMethodHook

class HookConnectivityManagerGetLinkProperties(private val helper: ConnectivityServiceHookHelper) {
    private companion object {
        private const val SOURCE = "HookGetLinkProperties"
    }

    fun install() {
        if (helper.sdkInt >= 30) {
            try {
                hookLinkPropertiesRestricted()
            } catch (e: Throwable) {
                HookErrorStore.w(SOURCE, "hookLinkPropertiesRestricted failed: ${e.message}", e)
            }
        }

        try {
            hookGetLinkProperties()
        } catch (e: Throwable) {
            HookErrorStore.w(SOURCE, "hookGetLinkProperties failed: ${e.message}", e)
        }

        try {
            hookGetLinkPropertiesForType()
        } catch (e: Throwable) {
            HookErrorStore.w(SOURCE, "hookGetLinkPropertiesForType failed: ${e.message}", e)
        }
    }

    private fun hookLinkPropertiesRestricted() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "linkPropertiesRestrictedForCallerPermissions",
            LinkProperties::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    val callerUid = param.args[2] as Int
                    val lp = param.result as? LinkProperties ?: return
                    if (!VpnSanitizer.hasVpnInterface(lp)) return
                    if (!VpnSanitizer.shouldHide(callerUid)) return
                    val underlying = helper.getUnderlyingLinkProperties(param.thisObject, callerUid)
                    param.result = underlying ?: VpnSanitizer.sanitizeLinkProperties(lp)
                }
            }
        )
    }

    private fun hookGetLinkProperties() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "getLinkProperties",
            Network::class.java,
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!helper.shouldHide(param.thisObject, uid)) return
                    val lp = param.result as? LinkProperties ?: return
                    if (!VpnSanitizer.hasVpnInterface(lp)) return
                    val underlying = helper.getUnderlyingLinkProperties(param.thisObject, uid)
                    param.result = underlying ?: VpnSanitizer.sanitizeLinkProperties(lp)
                }
            }
        )
    }

    private fun hookGetLinkPropertiesForType() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "getLinkPropertiesForType",
            Int::class.javaPrimitiveType,
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!helper.shouldHide(param.thisObject, uid)) return
                    val networkType = param.args[0] as Int
                    if (networkType == ConnectivityManager.TYPE_VPN) {
                        param.result = null
                    }
                }
            }
        )
    }
}
