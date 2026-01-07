package io.nekohasekai.sfa.xposed.hooks.hidevpn

import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Binder
import de.robv.android.xposed.XposedHelpers
import io.nekohasekai.sfa.xposed.hooks.SafeMethodHook

class HookConnectivityManagerGetActiveNetworkInfo(private val helper: ConnectivityServiceHookHelper) {
    private companion object {
        private const val SOURCE = "HookConnectivityManagerGetActiveNetworkInfo"
    }

    fun install() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "getActiveNetworkInfo",
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!helper.shouldHide(param.thisObject, uid)) return
                    val info = param.result as? NetworkInfo ?: return
                    if (info.type != ConnectivityManager.TYPE_VPN) return
                    val replacement = helper.getUnderlyingNetworkInfo(param.thisObject, uid)
                    if (replacement != null) {
                        param.result = replacement
                    }
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            helper.cls,
            "getActiveNetworkInfoForUid",
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    val uid = param.args[0] as Int
                    if (!helper.shouldHide(param.thisObject, uid)) return
                    val info = param.result as? NetworkInfo ?: return
                    if (info.type != ConnectivityManager.TYPE_VPN) return
                    val replacement = helper.getUnderlyingNetworkInfo(param.thisObject, uid)
                    if (replacement != null) {
                        param.result = replacement
                    }
                }
            }
        )
    }
}
