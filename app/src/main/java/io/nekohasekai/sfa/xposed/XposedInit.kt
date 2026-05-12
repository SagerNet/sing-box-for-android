package io.nekohasekai.sfa.xposed

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

class XposedInit : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        HookErrorStore.i("XposedInit", "onModuleLoaded process=${param.processName} system=${param.isSystemServer}")
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        HookInstaller.install(param.classLoader)
    }

    companion object {
        const val TAG = "sing-box-lsposed"
    }
}
