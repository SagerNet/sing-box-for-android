package io.nekohasekai.sfa.xposed.hooks.hidevpn

import android.net.Network
import android.net.ProxyInfo
import android.os.Binder
import de.robv.android.xposed.XposedHelpers
import io.nekohasekai.sfa.xposed.VpnSanitizer
import io.nekohasekai.sfa.xposed.hooks.SafeMethodHook

class HookConnectivityManagerGetDefaultProxy(private val helper: ConnectivityServiceHookHelper) {
    private companion object {
        private const val SOURCE = "HookConnectivityManagerGetDefaultProxy"
    }

    fun install() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "getProxyForNetwork",
            Network::class.java,
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!VpnSanitizer.shouldHide(uid)) return
                    param.result as? ProxyInfo ?: return
                    param.result = null
                }
            },
        )

        XposedHelpers.findAndHookMethod(
            helper.cls,
            "getGlobalProxy",
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!VpnSanitizer.shouldHide(uid)) return
                    param.result as? ProxyInfo ?: return
                    param.result = null
                }
            },
        )
    }
}
