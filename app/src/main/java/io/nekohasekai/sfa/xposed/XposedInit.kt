package io.nekohasekai.sfa.xposed

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class XposedInit(base: XposedInterface, param: XposedModuleInterface.ModuleLoadedParam) : XposedModule(base, param) {

    override fun onSystemServerLoaded(param: XposedModuleInterface.SystemServerLoadedParam) {
        HookInstaller.install(param.classLoader)
    }

    companion object {
        const val TAG = "sing-box-lsposed"
    }
}
