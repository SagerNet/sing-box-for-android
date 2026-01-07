package io.nekohasekai.sfa.xposed.hooks.hidevpn

import android.net.NetworkCapabilities
import android.os.Binder
import android.os.Bundle
import de.robv.android.xposed.XposedHelpers
import io.nekohasekai.sfa.xposed.HookErrorStore
import io.nekohasekai.sfa.xposed.VpnHideContext
import io.nekohasekai.sfa.xposed.VpnSanitizer
import io.nekohasekai.sfa.xposed.hooks.SafeMethodHook

class HookConnectivityManagerRequestNetwork(private val helper: ConnectivityServiceHookHelper) {
    private companion object {
        private const val SOURCE = "HookRequestNetwork"
    }

    fun install() {
        // Hook requestNetwork based on API level
        hookRequestNetwork()

        // Hook listenForNetwork based on API level
        hookListenForNetwork()

        // Hook pendingRequestForNetwork
        hookPendingRequestForNetwork()

        // Hook pendingListenForNetwork
        hookPendingListenForNetwork()

        // Hook createDefaultNetworkCapabilitiesForUid (API 28+)
        if (helper.sdkInt >= 28) {
            try {
                hookCreateDefaultNetworkCapabilities()
            } catch (e: Throwable) {
                HookErrorStore.w(SOURCE, "hookCreateDefaultNetworkCapabilities failed: ${e.message}", e)
            }
        }

        // Hook copyDefaultNetworkCapabilitiesForUid (API 31+)
        if (helper.sdkInt >= 31) {
            try {
                hookCopyDefaultNetworkCapabilities()
            } catch (e: Throwable) {
                HookErrorStore.w(SOURCE, "hookCopyDefaultNetworkCapabilities failed: ${e.message}", e)
            }
        }

        // Hook callCallbackForRequest
        hookCallCallbackForRequest()

        // Hook sendPendingIntentForRequest
        try {
            hookSendPendingIntentForRequest()
        } catch (e: Throwable) {
            HookErrorStore.w(SOURCE, "hookSendPendingIntentForRequest failed: ${e.message}", e)
        }
    }

    private fun hookRequestNetwork() {
        when {
            helper.sdkInt >= 36 -> {
                try {
                    hookRequestNetworkV16()
                } catch (e: Throwable) {
                    HookErrorStore.w(SOURCE, "hookRequestNetworkV16 failed: ${e.message}", e)
                    try {
                        hookRequestNetworkV12()
                    } catch (e2: Throwable) {
                        HookErrorStore.e(SOURCE, "hookRequestNetworkV12 failed: ${e2.message}", e2)
                    }
                }
            }
            helper.sdkInt >= 31 -> {
                try {
                    hookRequestNetworkV12()
                } catch (e: Throwable) {
                    HookErrorStore.w(SOURCE, "hookRequestNetworkV12 failed: ${e.message}", e)
                    try {
                        hookRequestNetworkV11()
                    } catch (e2: Throwable) {
                        HookErrorStore.e(SOURCE, "hookRequestNetworkV11 failed: ${e2.message}", e2)
                    }
                }
            }
            helper.sdkInt >= 30 -> {
                try {
                    hookRequestNetworkV11()
                } catch (e: Throwable) {
                    HookErrorStore.w(SOURCE, "hookRequestNetworkV11 failed: ${e.message}", e)
                    try {
                        hookRequestNetworkV8()
                    } catch (e2: Throwable) {
                        HookErrorStore.e(SOURCE, "hookRequestNetworkV8 failed: ${e2.message}", e2)
                    }
                }
            }
            else -> {
                try {
                    hookRequestNetworkV8()
                } catch (e: Throwable) {
                    HookErrorStore.e(SOURCE, "hookRequestNetworkV8 failed: ${e.message}", e)
                }
            }
        }
    }

    private fun hookListenForNetwork() {
        when {
            helper.sdkInt >= 36 -> {
                try {
                    hookListenForNetworkV16()
                } catch (e: Throwable) {
                    HookErrorStore.w(SOURCE, "hookListenForNetworkV16 failed: ${e.message}", e)
                    try {
                        hookListenForNetworkV12()
                    } catch (e2: Throwable) {
                        HookErrorStore.e(SOURCE, "hookListenForNetworkV12 failed: ${e2.message}", e2)
                    }
                }
            }
            helper.sdkInt >= 31 -> {
                try {
                    hookListenForNetworkV12()
                } catch (e: Throwable) {
                    HookErrorStore.w(SOURCE, "hookListenForNetworkV12 failed: ${e.message}", e)
                    try {
                        hookListenForNetworkV11()
                    } catch (e2: Throwable) {
                        HookErrorStore.e(SOURCE, "hookListenForNetworkV11 failed: ${e2.message}", e2)
                    }
                }
            }
            helper.sdkInt >= 30 -> {
                try {
                    hookListenForNetworkV11()
                } catch (e: Throwable) {
                    HookErrorStore.w(SOURCE, "hookListenForNetworkV11 failed: ${e.message}", e)
                    try {
                        hookListenForNetworkV8()
                    } catch (e2: Throwable) {
                        HookErrorStore.e(SOURCE, "hookListenForNetworkV8 failed: ${e2.message}", e2)
                    }
                }
            }
            else -> {
                try {
                    hookListenForNetworkV8()
                } catch (e: Throwable) {
                    HookErrorStore.e(SOURCE, "hookListenForNetworkV8 failed: ${e.message}", e)
                }
            }
        }
    }

    private fun hookPendingRequestForNetwork() {
        when {
            helper.sdkInt >= 31 -> {
                try {
                    hookPendingRequestForNetworkV12()
                } catch (e: Throwable) {
                    HookErrorStore.w(SOURCE, "hookPendingRequestForNetworkV12 failed: ${e.message}", e)
                    try {
                        hookPendingRequestForNetworkV11()
                    } catch (e2: Throwable) {
                        HookErrorStore.e(SOURCE, "hookPendingRequestForNetworkV11 failed: ${e2.message}", e2)
                    }
                }
            }
            helper.sdkInt >= 30 -> {
                try {
                    hookPendingRequestForNetworkV11()
                } catch (e: Throwable) {
                    HookErrorStore.w(SOURCE, "hookPendingRequestForNetworkV11 failed: ${e.message}", e)
                    try {
                        hookPendingRequestForNetworkV8()
                    } catch (e2: Throwable) {
                        HookErrorStore.e(SOURCE, "hookPendingRequestForNetworkV8 failed: ${e2.message}", e2)
                    }
                }
            }
            else -> {
                try {
                    hookPendingRequestForNetworkV8()
                } catch (e: Throwable) {
                    HookErrorStore.e(SOURCE, "hookPendingRequestForNetworkV8 failed: ${e.message}", e)
                }
            }
        }
    }

    private fun hookPendingListenForNetwork() {
        when {
            helper.sdkInt >= 31 -> {
                try {
                    hookPendingListenForNetworkV12()
                } catch (e: Throwable) {
                    HookErrorStore.w(SOURCE, "hookPendingListenForNetworkV12 failed: ${e.message}", e)
                    try {
                        hookPendingListenForNetworkV11()
                    } catch (e2: Throwable) {
                        HookErrorStore.e(SOURCE, "hookPendingListenForNetworkV11 failed: ${e2.message}", e2)
                    }
                }
            }
            helper.sdkInt >= 30 -> {
                try {
                    hookPendingListenForNetworkV11()
                } catch (e: Throwable) {
                    HookErrorStore.w(SOURCE, "hookPendingListenForNetworkV11 failed: ${e.message}", e)
                    try {
                        hookPendingListenForNetworkV8()
                    } catch (e2: Throwable) {
                        HookErrorStore.e(SOURCE, "hookPendingListenForNetworkV8 failed: ${e2.message}", e2)
                    }
                }
            }
            else -> {
                try {
                    hookPendingListenForNetworkV8()
                } catch (e: Throwable) {
                    HookErrorStore.e(SOURCE, "hookPendingListenForNetworkV8 failed: ${e.message}", e)
                }
            }
        }
    }

    // region requestNetwork versions

    private fun hookRequestNetworkV8() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "requestNetwork",
            NetworkCapabilities::class.java,
            android.os.Messenger::class.java,
            Int::class.javaPrimitiveType,
            android.os.IBinder::class.java,
            Int::class.javaPrimitiveType,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!VpnSanitizer.shouldHide(uid)) return
                    val nc = param.args[0] as? NetworkCapabilities ?: return
                    param.args[0] = VpnSanitizer.sanitizeRequestCapabilities(nc)
                }
            }
        )
    }

    private fun hookRequestNetworkV11() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "requestNetwork",
            NetworkCapabilities::class.java,
            android.os.Messenger::class.java,
            Int::class.javaPrimitiveType,
            android.os.IBinder::class.java,
            Int::class.javaPrimitiveType,
            String::class.java,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!VpnSanitizer.shouldHide(uid)) return
                    val nc = param.args[0] as? NetworkCapabilities ?: return
                    param.args[0] = VpnSanitizer.sanitizeRequestCapabilities(nc)
                }
            }
        )
    }

    private fun hookRequestNetworkV12() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "requestNetwork",
            Int::class.javaPrimitiveType,
            NetworkCapabilities::class.java,
            Int::class.javaPrimitiveType,
            android.os.Messenger::class.java,
            Int::class.javaPrimitiveType,
            android.os.IBinder::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java,
            String::class.java,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!VpnSanitizer.shouldHide(uid)) return
                    val nc = param.args[1] as? NetworkCapabilities ?: return
                    param.args[1] = VpnSanitizer.sanitizeRequestCapabilities(nc)
                }
            }
        )
    }

    private fun hookRequestNetworkV16() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "requestNetwork",
            Int::class.javaPrimitiveType,
            NetworkCapabilities::class.java,
            Int::class.javaPrimitiveType,
            android.os.Messenger::class.java,
            Int::class.javaPrimitiveType,
            android.os.IBinder::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!VpnSanitizer.shouldHide(uid)) return
                    val nc = param.args[1] as? NetworkCapabilities ?: return
                    param.args[1] = VpnSanitizer.sanitizeRequestCapabilities(nc)
                }
            }
        )
    }

    // endregion

    // region listenForNetwork versions

    private fun hookListenForNetworkV8() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "listenForNetwork",
            NetworkCapabilities::class.java,
            android.os.Messenger::class.java,
            android.os.IBinder::class.java,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!VpnSanitizer.shouldHide(uid)) return
                    val nc = param.args[0] as? NetworkCapabilities ?: return
                    param.args[0] = VpnSanitizer.sanitizeRequestCapabilities(nc)
                }
            }
        )
    }

    private fun hookListenForNetworkV11() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "listenForNetwork",
            NetworkCapabilities::class.java,
            android.os.Messenger::class.java,
            android.os.IBinder::class.java,
            String::class.java,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!VpnSanitizer.shouldHide(uid)) return
                    val nc = param.args[0] as? NetworkCapabilities ?: return
                    param.args[0] = VpnSanitizer.sanitizeRequestCapabilities(nc)
                }
            }
        )
    }

    private fun hookListenForNetworkV12() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "listenForNetwork",
            NetworkCapabilities::class.java,
            android.os.Messenger::class.java,
            android.os.IBinder::class.java,
            Int::class.javaPrimitiveType,
            String::class.java,
            String::class.java,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!VpnSanitizer.shouldHide(uid)) return
                    val nc = param.args[0] as? NetworkCapabilities ?: return
                    param.args[0] = VpnSanitizer.sanitizeRequestCapabilities(nc)
                }
            }
        )
    }

    private fun hookListenForNetworkV16() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "listenForNetwork",
            NetworkCapabilities::class.java,
            android.os.Messenger::class.java,
            android.os.IBinder::class.java,
            Int::class.javaPrimitiveType,
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!VpnSanitizer.shouldHide(uid)) return
                    val nc = param.args[0] as? NetworkCapabilities ?: return
                    param.args[0] = VpnSanitizer.sanitizeRequestCapabilities(nc)
                }
            }
        )
    }

    // endregion

    // region pendingRequestForNetwork versions

    private fun hookPendingRequestForNetworkV8() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "pendingRequestForNetwork",
            NetworkCapabilities::class.java,
            android.app.PendingIntent::class.java,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!VpnSanitizer.shouldHide(uid)) return
                    val nc = param.args[0] as? NetworkCapabilities ?: return
                    param.args[0] = VpnSanitizer.sanitizeRequestCapabilities(nc)
                }
            }
        )
    }

    private fun hookPendingRequestForNetworkV11() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "pendingRequestForNetwork",
            NetworkCapabilities::class.java,
            android.app.PendingIntent::class.java,
            String::class.java,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!VpnSanitizer.shouldHide(uid)) return
                    val nc = param.args[0] as? NetworkCapabilities ?: return
                    param.args[0] = VpnSanitizer.sanitizeRequestCapabilities(nc)
                }
            }
        )
    }

    private fun hookPendingRequestForNetworkV12() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "pendingRequestForNetwork",
            NetworkCapabilities::class.java,
            android.app.PendingIntent::class.java,
            String::class.java,
            String::class.java,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!VpnSanitizer.shouldHide(uid)) return
                    val nc = param.args[0] as? NetworkCapabilities ?: return
                    param.args[0] = VpnSanitizer.sanitizeRequestCapabilities(nc)
                }
            }
        )
    }

    // endregion

    // region pendingListenForNetwork versions

    private fun hookPendingListenForNetworkV8() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "pendingListenForNetwork",
            NetworkCapabilities::class.java,
            android.app.PendingIntent::class.java,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!VpnSanitizer.shouldHide(uid)) return
                    val nc = param.args[0] as? NetworkCapabilities ?: return
                    param.args[0] = VpnSanitizer.sanitizeRequestCapabilities(nc)
                }
            }
        )
    }

    private fun hookPendingListenForNetworkV11() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "pendingListenForNetwork",
            NetworkCapabilities::class.java,
            android.app.PendingIntent::class.java,
            String::class.java,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!VpnSanitizer.shouldHide(uid)) return
                    val nc = param.args[0] as? NetworkCapabilities ?: return
                    param.args[0] = VpnSanitizer.sanitizeRequestCapabilities(nc)
                }
            }
        )
    }

    private fun hookPendingListenForNetworkV12() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "pendingListenForNetwork",
            NetworkCapabilities::class.java,
            android.app.PendingIntent::class.java,
            String::class.java,
            String::class.java,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val uid = Binder.getCallingUid()
                    if (!VpnSanitizer.shouldHide(uid)) return
                    val nc = param.args[0] as? NetworkCapabilities ?: return
                    param.args[0] = VpnSanitizer.sanitizeRequestCapabilities(nc)
                }
            }
        )
    }

    // endregion

    // region default capabilities

    private fun hookCreateDefaultNetworkCapabilities() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "createDefaultNetworkCapabilitiesForUid",
            Int::class.javaPrimitiveType,
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    val uid = param.args[0] as? Int ?: return
                    if (!VpnSanitizer.shouldHide(uid)) return
                    val nc = param.result as? NetworkCapabilities ?: return
                    param.result = VpnSanitizer.sanitizeRequestCapabilities(nc)
                }
            }
        )
    }

    private fun hookCopyDefaultNetworkCapabilities() {
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "copyDefaultNetworkCapabilitiesForUid",
            NetworkCapabilities::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java,
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    val requestorUid = param.args[2] as? Int ?: return
                    if (!VpnSanitizer.shouldHide(requestorUid)) return
                    val nc = param.result as? NetworkCapabilities ?: return
                    param.result = VpnSanitizer.sanitizeRequestCapabilities(nc)
                }
            }
        )
    }

    // endregion

    // region callback hooks

    private fun hookCallCallbackForRequest() {
        if (helper.sdkInt >= 36) {
            // API 36+ has both WithAgent and WithBundle variants
            try {
                hookCallCallbackForRequestWithAgent()
            } catch (e: Throwable) {
                HookErrorStore.w(SOURCE, "hookCallCallbackForRequestWithAgent failed: ${e.message}", e)
            }
            try {
                hookCallCallbackForRequestWithBundle()
            } catch (e: Throwable) {
                HookErrorStore.w(SOURCE, "hookCallCallbackForRequestWithBundle failed: ${e.message}", e)
            }
        } else {
            try {
                hookCallCallbackForRequestWithAgent()
            } catch (e: Throwable) {
                HookErrorStore.w(SOURCE, "hookCallCallbackForRequestWithAgent failed: ${e.message}", e)
            }
        }
    }

    private fun hookCallCallbackForRequestWithAgent() {
        val (nriClass, naiClass) = helper.resolveNriAndNaiClasses()
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "callCallbackForRequest",
            nriClass,
            naiClass,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val nri = param.args[0] ?: return
                    val uid = helper.getAsUid(nri)
                    if (!VpnSanitizer.shouldHide(uid)) return
                    val networkAgent = param.args[1]
                    if (networkAgent != null && helper.isVpnNai(networkAgent)) {
                        val underlying = helper.getUnderlyingNai(param.thisObject, uid)
                        if (underlying != null) {
                            param.args[1] = underlying
                        }
                    }
                    VpnHideContext.setTargetUid(uid)
                }

                override fun afterHook(param: MethodHookParam) {
                    VpnHideContext.clear()
                }
            }
        )
    }

    private fun hookCallCallbackForRequestWithBundle() {
        val (nriClass, _) = helper.resolveNriAndNaiClasses()
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "callCallbackForRequest",
            nriClass,
            Int::class.javaPrimitiveType,
            Bundle::class.java,
            Int::class.javaPrimitiveType,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val nri = param.args[0] ?: return
                    val uid = helper.getAsUid(nri)
                    if (!VpnSanitizer.shouldHide(uid)) return
                    VpnHideContext.setTargetUid(uid)
                }

                override fun afterHook(param: MethodHookParam) {
                    VpnHideContext.clear()
                }
            }
        )
    }

    private fun hookSendPendingIntentForRequest() {
        val (nriClass, naiClass) = helper.resolveNriAndNaiClasses()
        XposedHelpers.findAndHookMethod(
            helper.cls,
            "sendPendingIntentForRequest",
            nriClass,
            naiClass,
            Int::class.javaPrimitiveType,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    val nri = param.args[0] ?: return
                    val uid = helper.getAsUid(nri)
                    if (!VpnSanitizer.shouldHide(uid)) return
                    val networkAgent = param.args[1]
                    if (networkAgent != null && helper.isVpnNai(networkAgent)) {
                        val underlying = helper.getUnderlyingNai(param.thisObject, uid)
                        if (underlying != null) {
                            param.args[1] = underlying
                        }
                    }
                    VpnHideContext.setTargetUid(uid)
                }

                override fun afterHook(param: MethodHookParam) {
                    VpnHideContext.clear()
                }
            }
        )
    }

    // endregion
}
