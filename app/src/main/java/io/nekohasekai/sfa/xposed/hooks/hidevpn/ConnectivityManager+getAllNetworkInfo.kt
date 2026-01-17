package io.nekohasekai.sfa.xposed.hooks.hidevpn

import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Binder
import de.robv.android.xposed.XposedHelpers
import io.nekohasekai.sfa.xposed.hooks.SafeMethodHook

class HookConnectivityManagerGetAllNetworkInfo(private val helper: ConnectivityServiceHookHelper) {
    private companion object {
        private const val SOURCE = "HookConnectivityManagerGetAllNetworkInfo"
    }

    fun install() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "getAllNetworkInfo",
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!helper.shouldHide(param.thisObject, uid)) return
                    @Suppress("UNCHECKED_CAST")
                    val infos = param.result as? Array<NetworkInfo> ?: return
                    val filtered = infos.filter { it.type != ConnectivityManager.TYPE_VPN }
                    param.result = filtered.toTypedArray()
                }
            },
        )
    }
}
