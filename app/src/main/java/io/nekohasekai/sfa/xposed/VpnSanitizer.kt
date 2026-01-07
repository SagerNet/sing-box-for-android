package io.nekohasekai.sfa.xposed

import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Parcel
import android.os.Process
import de.robv.android.xposed.XposedHelpers
import java.util.Locale

object VpnSanitizer {
    private val vpnInterfacePrefixes = arrayOf(
        "tun",
    )

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
        val stacked = XposedHelpers.callMethod(lp, "getStackedLinks") as? List<LinkProperties>
        if (!stacked.isNullOrEmpty()) {
            for (link in stacked) {
                clearHttpProxy(link)
                val name = link.interfaceName
                if (isVpnInterface(name)) {
                    XposedHelpers.callMethod(lp, "removeStackedLink", name)
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
        val stacked = XposedHelpers.callMethod(lp, "getStackedLinks") as? List<LinkProperties>
            ?: return false
        return stacked.any { isVpnInterface(it.interfaceName) }
    }

    fun isVpnInterface(iface: String?): Boolean {
        if (iface.isNullOrEmpty()) return false
        val name = iface.lowercase(Locale.US)
        return vpnInterfacePrefixes.any { name.startsWith(it) }
    }

    private fun sanitizeTransport(caps: NetworkCapabilities) {
        XposedHelpers.callMethod(caps, "removeTransportType", NetworkCapabilities.TRANSPORT_VPN)
        XposedHelpers.callMethod(caps, "addCapability", NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    }

    private fun clearUnderlyingNetworks(caps: NetworkCapabilities) {
        XposedHelpers.callMethod(caps, "setUnderlyingNetworks", null)
    }

    private fun clearOwnerUid(caps: NetworkCapabilities) {
        XposedHelpers.callMethod(caps, "setOwnerUid", Process.INVALID_UID)
    }

    private fun clearVpnTransportInfo(caps: NetworkCapabilities) {
        val field = XposedHelpers.findField(NetworkCapabilities::class.java, "mTransportInfo")
        val info = field.get(caps) ?: return
        if (info.javaClass.name.contains("VpnTransportInfo")) {
            field.set(caps, null)
        }
    }

    private fun clearHttpProxy(lp: LinkProperties) {
        XposedHelpers.callMethod(lp, "setHttpProxy", null)
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
