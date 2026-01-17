package io.nekohasekai.sfa.xposed.hooks.hidevpn

import android.net.NetworkCapabilities
import android.os.Binder
import android.os.Parcel
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.nekohasekai.sfa.xposed.HookErrorStore
import io.nekohasekai.sfa.xposed.HookStatusStore
import io.nekohasekai.sfa.xposed.VpnHideContext
import io.nekohasekai.sfa.xposed.VpnSanitizer
import io.nekohasekai.sfa.xposed.hooks.SafeMethodHook
import io.nekohasekai.sfa.xposed.hooks.XHook

class HookNetworkCapabilitiesWriteToParcel : XHook {
    private companion object {
        private const val SOURCE = "HookNCWriteToParcel"
    }

    private val copyCtor by lazy {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            NetworkCapabilities::class.java.getDeclaredConstructor(
                NetworkCapabilities::class.java,
                Long::class.javaPrimitiveType,
            ).apply { isAccessible = true }
        } else {
            NetworkCapabilities::class.java.getDeclaredConstructor(
                NetworkCapabilities::class.java,
            ).apply { isAccessible = true }
        }
    }
    private val removeTransportTypeMethod by lazy {
        NetworkCapabilities::class.java.getMethod("removeTransportType", Int::class.javaPrimitiveType)
    }
    private val addCapabilityMethod by lazy {
        NetworkCapabilities::class.java.getMethod("addCapability", Int::class.javaPrimitiveType)
    }

    private val inWrite = ThreadLocal.withInitial { false }

    override fun injectHook() {
        XposedHelpers.findAndHookMethod(
            NetworkCapabilities::class.java,
            "writeToParcel",
            Parcel::class.java,
            Int::class.javaPrimitiveType!!,
            object : SafeMethodHook(SOURCE) {
                override fun beforeHook(param: MethodHookParam) {
                    if (inWrite.get() == true) {
                        return
                    }
                    val targetUid = VpnHideContext.consumeTargetUid()
                    val shouldHide = when {
                        targetUid != null -> VpnSanitizer.shouldHide(targetUid)
                        else -> VpnSanitizer.shouldHide(Binder.getCallingUid())
                    }
                    if (!shouldHide) {
                        return
                    }
                    val caps = param.thisObject as NetworkCapabilities
                    val sanitized = copyNetworkCapabilities(caps)
                    sanitizeNetworkCapabilities(sanitized)
                    HookStatusStore.markPatched()
                    inWrite.set(true)
                    try {
                        XposedBridge.invokeOriginalMethod(param.method, sanitized, param.args)
                        param.result = null
                    } finally {
                        inWrite.set(false)
                    }
                }
            },
        )
        HookStatusStore.markHookActive()
        HookErrorStore.i(SOURCE, "Hooked NetworkCapabilities.writeToParcel (sender)")
    }

    private fun copyNetworkCapabilities(caps: NetworkCapabilities): NetworkCapabilities = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        copyCtor.newInstance(caps, 0L) as NetworkCapabilities
    } else {
        copyCtor.newInstance(caps) as NetworkCapabilities
    }

    private fun sanitizeNetworkCapabilities(caps: NetworkCapabilities) {
        removeTransportTypeMethod.invoke(caps, NetworkCapabilities.TRANSPORT_VPN)
        addCapabilityMethod.invoke(caps, NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        clearVpnTransportInfo(caps)
        clearUnderlyingNetworks(caps)
        clearOwnerUid(caps)
    }

    private fun clearVpnTransportInfo(caps: NetworkCapabilities) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            return
        }
        val field = XposedHelpers.findField(NetworkCapabilities::class.java, "mTransportInfo")
        val info = field.get(caps) ?: return
        if (info.javaClass.name.contains("VpnTransportInfo")) {
            field.set(caps, null)
        }
    }

    private fun clearUnderlyingNetworks(caps: NetworkCapabilities) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val field = XposedHelpers.findField(NetworkCapabilities::class.java, "mUnderlyingNetworks")
            field.set(caps, null)
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val field = XposedHelpers.findFieldIfExists(NetworkCapabilities::class.java, "mUnderlyingNetworks")
            field?.set(caps, null)
        }
    }

    private fun clearOwnerUid(caps: NetworkCapabilities) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            return
        }
        val field = XposedHelpers.findField(NetworkCapabilities::class.java, "mOwnerUid")
        field.setInt(caps, android.os.Process.INVALID_UID)
    }
}
