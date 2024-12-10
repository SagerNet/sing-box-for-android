package io.nekohasekai.sfa.bg

import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.ktx.toIpPrefix
import io.nekohasekai.sfa.ktx.toList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class VPNService : VpnService(), PlatformInterfaceWrapper {

    companion object {
        private const val TAG = "VPNService"
    }

    private val service = BoxService(this, this)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) =
        service.onStartCommand()

    override fun onBind(intent: Intent): IBinder {
        val binder = super.onBind(intent)
        if (binder != null) {
            return binder
        }
        return service.onBind()
    }

    override fun onDestroy() {
        service.onDestroy()
    }

    override fun onRevoke() {
        runBlocking {
            withContext(Dispatchers.Main) {
                service.onRevoke()
            }
        }
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        protect(fd)
    }

    var systemProxyAvailable = false
    var systemProxyEnabled = false

    override fun openTun(options: TunOptions): Int {
        if (prepare(this) != null) error("android: missing vpn permission")

        val builder = Builder()
            .setSession("sing-box")
            .setMtu(options.mtu)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val inet4Address = options.inet4Address
        while (inet4Address.hasNext()) {
            val address = inet4Address.next()
            builder.addAddress(address.address(), address.prefix())
        }

        val inet6Address = options.inet6Address
        while (inet6Address.hasNext()) {
            val address = inet6Address.next()
            builder.addAddress(address.address(), address.prefix())
        }

        if (options.autoRoute) {
            builder.addDnsServer(options.dnsServerAddress.value)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val inet4RouteAddress = options.inet4RouteAddress
                if (inet4RouteAddress.hasNext()) {
                    while (inet4RouteAddress.hasNext()) {
                        builder.addRoute(inet4RouteAddress.next().toIpPrefix())
                    }
                } else if (options.inet4Address.hasNext()) {
                    builder.addRoute("0.0.0.0", 0)
                }

                val inet6RouteAddress = options.inet6RouteAddress
                if (inet6RouteAddress.hasNext()) {
                    while (inet6RouteAddress.hasNext()) {
                        builder.addRoute(inet6RouteAddress.next().toIpPrefix())
                    }
                } else if (options.inet6Address.hasNext()) {
                    builder.addRoute("::", 0)
                }

                val inet4RouteExcludeAddress = options.inet4RouteExcludeAddress
                while (inet4RouteExcludeAddress.hasNext()) {
                    builder.excludeRoute(inet4RouteExcludeAddress.next().toIpPrefix())
                }

                val inet6RouteExcludeAddress = options.inet6RouteExcludeAddress
                while (inet6RouteExcludeAddress.hasNext()) {
                    builder.excludeRoute(inet6RouteExcludeAddress.next().toIpPrefix())
                }
            } else {
                val inet4RouteAddress = options.inet4RouteRange
                if (inet4RouteAddress.hasNext()) {
                    while (inet4RouteAddress.hasNext()) {
                        val address = inet4RouteAddress.next()
                        builder.addRoute(address.address(), address.prefix())
                    }
                }

                val inet6RouteAddress = options.inet6RouteRange
                if (inet6RouteAddress.hasNext()) {
                    while (inet6RouteAddress.hasNext()) {
                        val address = inet6RouteAddress.next()
                        builder.addRoute(address.address(), address.prefix())
                    }
                }
            }

            if (Settings.perAppProxyEnabled) {
                val appList = Settings.perAppProxyList
                if (Settings.perAppProxyMode == Settings.PER_APP_PROXY_INCLUDE) {
                    appList.forEach {
                        try {
                            builder.addAllowedApplication(it)
                        } catch (_: NameNotFoundException) {
                        }
                    }
                    builder.addAllowedApplication(packageName)
                } else {
                    appList.forEach {
                        try {
                            builder.addDisallowedApplication(it)
                        } catch (_: NameNotFoundException) {
                        }
                    }
                }
            } else {
                val includePackage = options.includePackage
                if (includePackage.hasNext()) {
                    while (includePackage.hasNext()) {
                        try {
                            builder.addAllowedApplication(includePackage.next())
                        } catch (_: NameNotFoundException) {
                        }
                    }
                }

                val excludePackage = options.excludePackage
                if (excludePackage.hasNext()) {
                    while (excludePackage.hasNext()) {
                        try {
                            builder.addDisallowedApplication(excludePackage.next())
                        } catch (_: NameNotFoundException) {
                        }
                    }
                }
            }
        }

        if (options.isHTTPProxyEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            systemProxyAvailable = true
            systemProxyEnabled = Settings.systemProxyEnabled
            if (systemProxyEnabled) builder.setHttpProxy(
                ProxyInfo.buildDirectProxy(
                    options.httpProxyServer,
                    options.httpProxyServerPort,
                    options.httpProxyBypassDomain.toList()
                )
            )
        } else {
            systemProxyAvailable = false
            systemProxyEnabled = false
        }

        val pfd =
            builder.establish() ?: error("android: the application is not prepared or is revoked")
        service.fileDescriptor = pfd
        return pfd.fd
    }

    override fun writeLog(message: String) = service.writeLog(message)

    override fun sendNotification(notification: Notification) =
        service.sendNotification(notification)

}