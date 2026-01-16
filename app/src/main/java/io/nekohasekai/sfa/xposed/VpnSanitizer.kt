package io.nekohasekai.sfa.xposed

import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.ProxyInfo
import android.os.Build
import android.os.Parcel
import android.os.Process
import de.robv.android.xposed.XposedHelpers
import java.util.Locale

object VpnSanitizer {
    private val vpnInterfacePrefixes = arrayOf(
        "tun",
    )

    private val getStackedLinksMethod by lazy {
        LinkProperties::class.java.getMethod("getStackedLinks")
    }
    private val removeStackedLinkMethod by lazy {
        LinkProperties::class.java.getMethod("removeStackedLink", String::class.java)
    }
    private val setHttpProxyMethod by lazy {
        LinkProperties::class.java.getMethod("setHttpProxy", ProxyInfo::class.java)
    }
    private val removeTransportTypeMethod by lazy {
        NetworkCapabilities::class.java.getMethod("removeTransportType", Int::class.javaPrimitiveType)
    }
    private val addCapabilityMethod by lazy {
        NetworkCapabilities::class.java.getMethod("addCapability", Int::class.javaPrimitiveType)
    }

    fun shouldHide(uid: Int): Boolean {
        if (!PrivilegeSettingsStore.shouldHideUid(uid)) {
            return false
        }
        if (VpnAppStore.isVpnUidExcludeSelf(uid)) {
            return false
        }
        return true
    }

    fun sanitizeRequestCapabilities(source: NetworkCapabilities): NetworkCapabilities {
        val caps = NetworkCapabilities(source)
        sanitizeTransport(caps)
        return caps
    }

    fun sanitizeNetworkCapabilities(source: NetworkCapabilities): NetworkCapabilities {
        val caps = NetworkCapabilities(source)
        sanitizeTransport(caps)
        clearUnderlyingNetworks(caps)
        clearOwnerUid(caps)
        clearVpnTransportInfo(caps)
        return caps
    }

    fun sanitizeLinkProperties(source: LinkProperties): LinkProperties {
        val lp = cloneLinkProperties(source)
        clearHttpProxy(lp)
        val iface = lp.interfaceName
        if (isVpnInterface(iface)) {
            lp.setInterfaceName(null)
        }
        @Suppress("UNCHECKED_CAST")
        val stacked = getStackedLinksMethod.invoke(lp) as? List<LinkProperties>
        if (!stacked.isNullOrEmpty()) {
            for (link in stacked) {
                clearHttpProxy(link)
                val iface = link.interfaceName
                if (iface != null && isVpnInterface(iface)) {
                    removeStackedLinkMethod.invoke(lp, iface)
                }
            }
        }
        return lp
    }

    fun hasVpnInterface(lp: LinkProperties): Boolean {
        if (isVpnInterface(lp.interfaceName)) {
            return true
        }
        @Suppress("UNCHECKED_CAST")
        val stacked = getStackedLinksMethod.invoke(lp) as? List<LinkProperties> ?: return false
        return stacked.any { isVpnInterface(it.interfaceName) }
    }

    fun isVpnInterface(iface: String?): Boolean {
        if (iface.isNullOrEmpty()) return false
        val name = iface.lowercase(Locale.US)
        return vpnInterfacePrefixes.any { name.startsWith(it) }
    }

    private fun sanitizeTransport(caps: NetworkCapabilities) {
        removeTransportTypeMethod.invoke(caps, NetworkCapabilities.TRANSPORT_VPN)
        addCapabilityMethod.invoke(caps, NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    }

    private fun clearUnderlyingNetworks(caps: NetworkCapabilities) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val field = XposedHelpers.findField(NetworkCapabilities::class.java, "mUnderlyingNetworks")
            field.set(caps, null)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val field = XposedHelpers.findFieldIfExists(NetworkCapabilities::class.java, "mUnderlyingNetworks")
            field?.set(caps, null)
        }
    }

    private fun clearOwnerUid(caps: NetworkCapabilities) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val field = XposedHelpers.findField(NetworkCapabilities::class.java, "mOwnerUid")
            field.setInt(caps, Process.INVALID_UID)
        }
    }

    private fun clearVpnTransportInfo(caps: NetworkCapabilities) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }
        val field = XposedHelpers.findField(NetworkCapabilities::class.java, "mTransportInfo")
        val info = field.get(caps) ?: return
        if (info.javaClass.name.contains("VpnTransportInfo")) {
            field.set(caps, null)
        }
    }

    private fun clearHttpProxy(lp: LinkProperties) {
        setHttpProxyMethod.invoke(lp, null as ProxyInfo?)
    }

    fun cloneLinkProperties(source: LinkProperties): LinkProperties {
        val parcel = Parcel.obtain()
        return try {
            source.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            LinkProperties.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }

    fun cloneNetworkInfo(source: NetworkInfo): NetworkInfo {
        val parcel = Parcel.obtain()
        return try {
            source.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            NetworkInfo.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
