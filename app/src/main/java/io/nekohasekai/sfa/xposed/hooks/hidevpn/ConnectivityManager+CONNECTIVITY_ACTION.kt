package io.nekohasekai.sfa.xposed.hooks.hidevpn

import android.net.ConnectivityManager
import android.net.NetworkInfo
import de.robv.android.xposed.XposedHelpers
import io.nekohasekai.sfa.xposed.VpnSanitizer
import io.nekohasekai.sfa.xposed.hooks.SafeMethodHook

class HookConnectivityManagerConnectivityAction(private val helper: ConnectivityServiceHookHelper) {
    private companion object {
        private const val SOURCE = "HookConnectivityManagerConnectivityAction"
    }

    fun install() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "sendGeneralBroadcast",
            NetworkInfo::class.java,
            String::class.java,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val info = param.args[0] as? NetworkInfo ?: return
                    if (info.type != ConnectivityManager.TYPE_VPN) return
                    val defaultNai = XposedHelpers.callMethod(param.thisObject, "getDefaultNetwork")
                        ?: return
                    if (helper.isVpnNai(defaultNai)) {
                        return
                    }
                    val replacement = XposedHelpers.getObjectField(defaultNai, "networkInfo") as? NetworkInfo
                        ?: return
                    param.args[0] = VpnSanitizer.cloneNetworkInfo(replacement)
                }
            }
        )
    }
}
