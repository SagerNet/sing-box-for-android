package io.nekohasekai.sfa.xposed

import io.nekohasekai.sfa.xposed.hooks.HookIConnectivityManagerOnTransact
import io.nekohasekai.sfa.xposed.hooks.hidevpn.ConnectivityServiceHookHelper
import io.nekohasekai.sfa.xposed.hooks.hidevpn.HookNetworkCapabilitiesWriteToParcel
import io.nekohasekai.sfa.xposed.hooks.hidevpn.HookNetworkInterfaceGetName
import io.nekohasekai.sfa.xposed.hooks.hidevpnapp.HookPackageManagerGetInstalledPackages

object HookInstaller {

    private const val TAG = "XposedInit"

    fun install(classLoader: ClassLoader) {
        HookErrorStore.i(TAG, "handleSystemServerStarting")
        val hooks = arrayOf(
            ConnectivityServiceHookHelper(classLoader),
            HookIConnectivityManagerOnTransact(classLoader),
            HookPackageManagerGetInstalledPackages(classLoader),
            HookNetworkCapabilitiesWriteToParcel(),
            HookNetworkInterfaceGetName(classLoader),
        )

        hooks.forEach { hook ->
            try {
                hook.injectHook()
            } catch (e: Throwable) {
                HookErrorStore.e(
                    TAG,
                    "Failed to inject ${hook.javaClass.simpleName}",
                    e,
                )
            }
        }
    }
}
