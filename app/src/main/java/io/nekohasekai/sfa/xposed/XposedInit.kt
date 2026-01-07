package io.nekohasekai.sfa.xposed

import android.content.Context
import io.nekohasekai.sfa.xposed.hooks.HookIConnectivityManagerOnTransact
import io.nekohasekai.sfa.xposed.hooks.hidevpn.ConnectivityServiceHookHelper
import io.nekohasekai.sfa.xposed.hooks.hidevpn.HookNetworkCapabilitiesWriteToParcel
import io.nekohasekai.sfa.xposed.hooks.hidevpn.HookNetworkInterfaceGetName
import io.nekohasekai.sfa.xposed.hooks.hidevpnapp.HookPackageManagerGetInstalledPackages
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class XposedInit(
    base: XposedInterface,
    param: XposedModuleInterface.ModuleLoadedParam,
) : XposedModule(base, param) {

    override fun onSystemServerLoaded(param: XposedModuleInterface.SystemServerLoadedParam) {
        val systemContext = resolveSystemContext()
        HookErrorStore.i("XposedInit", "handleSystemServerLoaded")
        val hooks = arrayOf(
            ConnectivityServiceHookHelper(param.classLoader),
            HookIConnectivityManagerOnTransact(param.classLoader, systemContext),
            HookPackageManagerGetInstalledPackages(param.classLoader),
            HookNetworkCapabilitiesWriteToParcel(),
            HookNetworkInterfaceGetName(param.classLoader),
        )

        hooks.forEach { hook ->
            try {
                hook.injectHook()
            } catch (e: Throwable) {
                HookErrorStore.e(
                    "XposedInit",
                    "Failed to inject ${hook.javaClass.simpleName}",
                    e,
                )
            }
        }
    }

    companion object {
        const val TAG = "sing-box-lsposed"
    }

    private fun resolveSystemContext(): Context? {
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val currentThread = activityThread.getMethod("currentActivityThread").invoke(null)
            activityThread.getMethod("getSystemContext").invoke(currentThread) as? Context
        } catch (e: Throwable) {
            HookErrorStore.e("XposedInit", "resolveSystemContext failed", e)
            null
        }
    }
}
