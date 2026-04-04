package io.nekohasekai.sfa.xposed

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import io.nekohasekai.sfa.xposed.hooks.HookIConnectivityManagerOnTransact
import io.nekohasekai.sfa.xposed.hooks.hidevpn.ConnectivityServiceHookHelper
import io.nekohasekai.sfa.xposed.hooks.hidevpn.HookNetworkCapabilitiesWriteToParcel
import io.nekohasekai.sfa.xposed.hooks.hidevpn.HookNetworkInterfaceGetName
import io.nekohasekai.sfa.xposed.hooks.hidevpnapp.HookPackageManagerGetInstalledPackages

class XposedInit : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        HookErrorStore.i("XposedInit", "onModuleLoaded process=${param.processName} system=${param.isSystemServer}")
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        HookErrorStore.i("XposedInit", "handleSystemServerStarting")
        val hooks = arrayOf(
            ConnectivityServiceHookHelper(param.classLoader),
            HookIConnectivityManagerOnTransact(param.classLoader),
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
}
