package io.nekohasekai.sfa.xposed.hooks.hidevpn

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkInfo
import android.os.Binder
import de.robv.android.xposed.XposedHelpers
import io.nekohasekai.sfa.xposed.VpnSanitizer
import io.nekohasekai.sfa.xposed.hooks.SafeMethodHook

class HookConnectivityManagerGetNetworkInfo(private val helper: ConnectivityServiceHookHelper) {
    private companion object {
        private const val SOURCE = "HookConnectivityManagerGetNetworkInfo"
    }

    fun install() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "getNetworkInfo",
            Int::class.javaPrimitiveType,
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    val type = param.args[0] as Int
                    if (type != ConnectivityManager.TYPE_VPN) return
                    val uid = Binder.getCallingUid()
                    if (!helper.shouldHide(param.thisObject, uid)) return
                    param.result = null
                }
            },
        )

        XposedHelpers.findAndHookMethod(
            helper.cls,
            "getNetworkInfoForUid",
            Network::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    val uid = param.args[1] as Int
                    if (!helper.shouldHide(param.thisObject, uid)) return
                    val info = param.result as? NetworkInfo ?: return
                    if (info.type != ConnectivityManager.TYPE_VPN) return
                    val replacement = helper.getUnderlyingNetworkInfo(param.thisObject, uid)
                    param.result = if (replacement != null) {
                        VpnSanitizer.cloneNetworkInfo(replacement)
                    } else {
                        null
                    }
                }
            },
        )
    }
}
