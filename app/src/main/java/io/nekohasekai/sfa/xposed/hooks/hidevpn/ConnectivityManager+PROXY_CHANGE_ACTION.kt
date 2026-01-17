package io.nekohasekai.sfa.xposed.hooks.hidevpn

import android.content.Context
import android.content.Intent
import android.net.Proxy
import android.net.ProxyInfo
import android.os.Binder
import android.os.UserHandle
import de.robv.android.xposed.XposedHelpers
import io.nekohasekai.sfa.xposed.HookErrorStore
import io.nekohasekai.sfa.xposed.hooks.SafeMethodHook

class HookConnectivityManagerProxyChangeAction(private val helper: ConnectivityServiceHookHelper) {
    private companion object {
        private const val SOURCE = "HookProxyChangeAction"
    }

    fun install() {
        if (helper.sdkInt >= 29) {
            try {
                hookProxyBroadcastTracker()
                return
            } catch (e: Throwable) {
                HookErrorStore.w(SOURCE, "hookProxyBroadcastTracker failed: ${e.message}", e)
            }
        }

        try {
            hookLegacyProxyBroadcast()
        } catch (e: Throwable) {
            HookErrorStore.w(SOURCE, "hookLegacyProxyBroadcast failed: ${e.message}", e)
        }
    }

    private fun hookProxyBroadcastTracker() {
        val trackerClass = helper.resolveConnectivityModuleClass("ProxyTracker", "connectivity")
        XposedHelpers.findAndHookMethod(
            trackerClass,
            "sendProxyBroadcast",
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val tracker = param.thisObject ?: return
                    val context = XposedHelpers.getObjectField(tracker, "mContext") as Context
                    val proxyInfo = emptyProxyInfo()
                    val intent = Intent(Proxy.PROXY_CHANGE_ACTION)
                    intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING)
                    intent.putExtra("android.intent.extra.PROXY_INFO", proxyInfo)
                    val ident = Binder.clearCallingIdentity()
                    try {
                        val userAll = try {
                            UserHandle::class.java.getField("ALL").get(null) as? UserHandle
                        } catch (_: Throwable) {
                            null
                        }
                        if (userAll != null) {
                            context.sendStickyBroadcastAsUser(intent, userAll)
                        } else {
                            context.sendStickyBroadcast(intent)
                        }
                    } finally {
                        Binder.restoreCallingIdentity(ident)
                    }
                    param.result = null
                }
            },
        )
    }

    private fun hookLegacyProxyBroadcast() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "sendProxyBroadcast",
            ProxyInfo::class.java,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    param.args[0] = emptyProxyInfo()
                }
            },
        )
    }

    private fun emptyProxyInfo(): ProxyInfo = ProxyInfo.buildDirectProxy("", 0)
}
