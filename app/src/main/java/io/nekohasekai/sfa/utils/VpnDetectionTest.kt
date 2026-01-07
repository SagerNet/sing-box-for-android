package io.nekohasekai.sfa.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.NetworkInterface
import java.util.Collections

data class DetectionResult(
    val frameworkDetected: List<String>,
    val nativeDetected: Boolean,
    val frameworkInterfaces: List<String>,
    val nativeInterfaces: List<String>,
    val httpProxy: String?,
)

object VpnDetectionTest {

    fun runDetection(context: Context): DetectionResult {
        val frameworkDetected = LinkedHashSet<String>()
        val frameworkInterfaces = LinkedHashSet<String>()

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return DetectionResult(emptyList(), false, emptyList(), emptyList(), null)

        // Check activeNetworkInfo
        val activeInfo = cm.activeNetworkInfo
        if (activeInfo?.type == ConnectivityManager.TYPE_VPN) {
            frameworkDetected += "ActiveNetworkInfo"
        }

        // Check networkInfo(TYPE_VPN)
        val vpnInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_VPN)
        if (vpnInfo != null && vpnInfo.isConnected) {
            frameworkDetected += "NetworkInfo"
        }

        // Check networkForType(VPN)
        val vpnNetwork = runCatching {
            val method = cm.javaClass.getMethod(
                "getNetworkForType",
                Int::class.javaPrimitiveType,
            )
            method.invoke(cm, ConnectivityManager.TYPE_VPN) as? Network
        }.getOrNull()
        if (vpnNetwork != null) {
            frameworkDetected += "NetworkForType"
        }

        // Check all networks for VPN transport or missing NOT_VPN capability
        val networks = cm.allNetworks ?: emptyArray()
        for (network in networks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                frameworkDetected += "NetworkCapabilities"
            }
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                frameworkDetected += "NetworkCapabilities"
            }
            // Check interface name in LinkProperties
            val lp = cm.getLinkProperties(network)
            if (isVpnInterface(lp?.interfaceName)) {
                lp?.interfaceName?.let(frameworkInterfaces::add)
                frameworkDetected += "LinkProperties"
            }
        }

        // Check activeLinkProperties interface
        val activeLinkProperties = runCatching { cm.getLinkProperties(cm.activeNetwork) }.getOrNull()
        if (isVpnInterface(activeLinkProperties?.interfaceName)) {
            activeLinkProperties?.interfaceName?.let(frameworkInterfaces::add)
            frameworkDetected += "LinkProperties"
        }

        // Native: Check network interfaces (getifaddrs)
        val nativeInterfaces = checkNetworkInterfaces()

        val httpProxy = readHttpProxy(cm)
        return DetectionResult(
            frameworkDetected.toList(),
            nativeInterfaces.isNotEmpty(),
            frameworkInterfaces.toList(),
            nativeInterfaces,
            httpProxy,
        )
    }

    private fun checkNetworkInterfaces(): List<String> {
        val list = try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
        } catch (_: Throwable) {
            return emptyList()
        }
        val matches = ArrayList<String>()
        for (iface in list) {
            val name = iface.name ?: continue
            val isUp = runCatching { iface.isUp }.getOrElse { false }
            if (!isUp) continue
            if (isVpnInterface(name)) {
                matches.add(name)
            }
        }
        return matches
    }

    private fun isVpnInterface(name: String?): Boolean {
        if (name.isNullOrEmpty()) return false
        val lower = name.lowercase()
        return lower.startsWith("tun") || lower.startsWith("ppp") || lower.startsWith("tap")
    }

    private fun readHttpProxy(cm: ConnectivityManager): String? {
        val defaultProxy = try {
            val method = cm.javaClass.getMethod("getDefaultProxy")
            method.invoke(cm) as? android.net.ProxyInfo
        } catch (_: Throwable) {
            null
        }
        val activeLinkProperties = runCatching { cm.getLinkProperties(cm.activeNetwork) }.getOrNull()
        val networks = cm.allNetworks ?: emptyArray()
        val proxies = buildList {
            add(formatProxyInfo(defaultProxy))
            add(formatProxyInfo(readProxyFromLinkProperties(activeLinkProperties)))
            for (network in networks) {
                add(formatProxyInfo(readProxyFromLinkProperties(cm.getLinkProperties(network))))
            }
        }
        return proxies.firstOrNull { !it.isNullOrEmpty() }
    }

    private fun readProxyFromLinkProperties(lp: android.net.LinkProperties?): android.net.ProxyInfo? {
        if (lp == null) return null
        return try {
            val method = lp.javaClass.getMethod("getHttpProxy")
            method.invoke(lp) as? android.net.ProxyInfo
        } catch (_: Throwable) {
            try {
                val field = lp.javaClass.getDeclaredField("mHttpProxy")
                field.isAccessible = true
                field.get(lp) as? android.net.ProxyInfo
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun formatProxyInfo(proxyInfo: android.net.ProxyInfo?): String? {
        if (proxyInfo == null) return null
        return try {
            val host = proxyInfo.host
            val port = proxyInfo.port
            if (!host.isNullOrEmpty() && port > 0) {
                return "$host:$port"
            }
            val pac = proxyInfo.pacFileUrl?.toString()
            if (!pac.isNullOrEmpty()) pac else null
        } catch (_: Throwable) {
            null
        }
    }
}
