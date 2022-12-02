package io.nekohasekai.sfa.bg

import android.content.Intent
import android.net.VpnService
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.TunInterface
import io.nekohasekai.libbox.TunOptions

class VPNService : VpnService(), PlatformInterface {

    private val commonService = CommonService(this, this)

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int) =
        commonService.onStartCommand(intent, flags, startId)

    override fun onBind(intent: Intent) = commonService.onBind(intent)

    override fun onDestroy() = commonService.onDestroy()
    override fun onRevoke() = commonService.onRevoke()

    override fun autoDetectInterfaceControl(fd: Int) {
        if (!vpnStarted) {
            return
        }
        if (!protect(fd)) {
            error("android: vpn service protect failed")
        }
    }

    var vpnStarted = false

    override fun openTun(options: TunOptions): TunInterface {
        if (prepare(this) != null) error("android: missing vpn permission")

        val builder = Builder().setSession("sing-box").setMtu(options.mtu)

        val inet4Address = options.inet4Address
        if (inet4Address.hasNext()) {
            while (inet4Address.hasNext()) {
                val address = inet4Address.next()
                builder.addAddress(address.address, address.prefix)
            }
        }

        val inet6Address = options.inet6Address
        if (inet6Address.hasNext()) {
            while (inet6Address.hasNext()) {
                val address = inet6Address.next()
                builder.addAddress(address.address, address.prefix)
            }
        }

        if (options.autoRoute) {
            builder.addDnsServer(options.dnsServerAddress)

            val inet4RouteAddress = options.inet4RouteAddress
            if (inet4RouteAddress.hasNext()) {
                while (inet4RouteAddress.hasNext()) {
                    val address = inet4RouteAddress.next()
                    builder.addRoute(address.address, address.prefix)
                }
            } else {
                builder.addRoute("0.0.0.0", 0)
            }

            val inet6RouteAddress = options.inet6RouteAddress
            if (inet6RouteAddress.hasNext()) {
                while (inet6RouteAddress.hasNext()) {
                    val address = inet6RouteAddress.next()
                    builder.addRoute(address.address, address.prefix)
                }
            } else {
                builder.addRoute("::", 0)
            }

            val includePackage = options.includePackage
            if (includePackage.hasNext()) {
                while (includePackage.hasNext()) {
                    builder.addAllowedApplication(includePackage.next())
                }
            }

            val excludePackage = options.excludePackage
            if (excludePackage.hasNext()) {
                while (excludePackage.hasNext()) {
                    builder.addDisallowedApplication(excludePackage.next())
                }
            }
        }

        val pfd =
            builder.establish() ?: error("android: the application is not prepared or is revoked")

        vpnStarted = true
        return object : TunInterface {
            override fun fileDescriptor(): Int {
                return pfd.fd
            }

            override fun close() {
                pfd.close()
                vpnStarted = false
            }
        }
    }

    override fun writeLog(message: String) = commonService.writeLog(message)

}