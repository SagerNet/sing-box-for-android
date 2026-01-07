package io.nekohasekai.sfa.xposed.hooks

import de.robv.android.xposed.XC_MethodHook
import io.nekohasekai.sfa.xposed.HookErrorStore

abstract class SafeMethodHook(private val source: String) : XC_MethodHook() {
    @Volatile
    private var disabled = false

    final override fun beforeHookedMethod(param: MethodHookParam) {
        if (disabled) return
        try {
            beforeHook(param)
        } catch (e: Throwable) {
            disabled = true
            HookErrorStore.e(source, "Hook disabled due to unrecoverable error", e)
        }
    }

    final override fun afterHookedMethod(param: MethodHookParam) {
        if (disabled) return
        try {
            afterHook(param)
        } catch (e: Throwable) {
            disabled = true
            HookErrorStore.e(source, "Hook disabled due to unrecoverable error", e)
        }
    }

    protected open fun beforeHook(param: MethodHookParam) {}
    protected open fun afterHook(param: MethodHookParam) {}
}
