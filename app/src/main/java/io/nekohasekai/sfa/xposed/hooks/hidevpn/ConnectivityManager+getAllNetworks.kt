package io.nekohasekai.sfa.xposed.hooks.hidevpn

import android.net.Network
import android.os.Binder
import de.robv.android.xposed.XposedHelpers
import io.nekohasekai.sfa.xposed.hooks.SafeMethodHook

class HookConnectivityManagerGetAllNetworks(private val helper: ConnectivityServiceHookHelper) {
    private companion object {
        private const val SOURCE = "HookConnectivityManagerGetAllNetworks"
    }

    fun install() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "getAllNetworks",
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!helper.shouldHide(param.thisObject, uid)) return
                    @Suppress("UNCHECKED_CAST")
                    val networks = param.result as? Array<Network> ?: return
                    val filtered = networks.filter { !helper.isVpnNetwork(param.thisObject, it) }
                    param.result = filtered.toTypedArray()
                }
            },
        )
    }
}
