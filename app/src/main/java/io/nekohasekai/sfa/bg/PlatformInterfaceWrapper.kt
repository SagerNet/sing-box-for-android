package io.nekohasekai.sfa.bg

import android.annotation.SuppressLint
import android.content.Intent
import android.net.NetworkCapabilities
import android.os.Build
import android.os.DeadObjectException
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.Settings
import android.system.OsConstants
import android.util.Log
import androidx.annotation.RequiresApi
import io.nekohasekai.libbox.AutoRedirectHandler
import io.nekohasekai.libbox.AutoRedirectSession
import io.nekohasekai.libbox.BridgeOptions
import io.nekohasekai.libbox.BridgeSession
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NeighborEntryIterator
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.constant.Action
import io.nekohasekai.sfa.ktx.toList
import io.nekohasekai.sfa.ktx.toStringIterator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.security.KeyStore
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import io.nekohasekai.libbox.NeighborEntry as LibboxNeighborEntry
import io.nekohasekai.libbox.NetworkInterface as LibboxNetworkInterface

private var neighborCallback: INeighborTableCallback.Stub? = null

interface PlatformInterfaceWrapper : PlatformInterface {
    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
    }

    override fun openTun(options: TunOptions): Int {
        error("invalid argument")
    }

    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner {
        try {
            val uid =
                Application.connectivity.getConnectionOwnerUid(
                    ipProtocol,
                    InetSocketAddress(sourceAddress, sourcePort),
                    InetSocketAddress(destinationAddress, destinationPort),
                )
            if (uid == Process.INVALID_UID) error("android: connection owner not found")
            val packages = Application.packageManager.getPackagesForUid(uid)
            val owner = ConnectionOwner()
            owner.userId = uid
            owner.userName = packages?.firstOrNull() ?: ""
            owner.setAndroidPackageNames(StringArray(packages?.toList()?.iterator() ?: emptyList<String>().iterator()))
            return owner
        } catch (e: Exception) {
            Log.e("PlatformInterface", "getConnectionOwnerUid", e)
            e.printStackTrace(System.err)
            throw e
        }
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        DefaultNetworkMonitor.setListener(listener)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        DefaultNetworkMonitor.setListener(null)
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val networks = Application.connectivity.allNetworks
        val networkInterfaces = NetworkInterface.getNetworkInterfaces().toList()
        val interfaces = mutableListOf<LibboxNetworkInterface>()
        for (network in networks) {
            val boxInterface = LibboxNetworkInterface()
            val linkProperties = Application.connectivity.getLinkProperties(network) ?: continue
            val networkCapabilities =
                Application.connectivity.getNetworkCapabilities(network) ?: continue
            boxInterface.name = linkProperties.interfaceName
            val networkInterface =
                networkInterfaces.find { it.name == boxInterface.name } ?: continue
            boxInterface.dnsServer =
                StringArray(linkProperties.dnsServers.mapNotNull { it.hostAddress }.iterator())
            boxInterface.gateway =
                StringArray(
                    linkProperties.routes
                        .filter { it.destination.prefixLength == 0 }
                        .mapNotNull { it.gateway }
                        .filterNot { it.isAnyLocalAddress }
                        .mapNotNull { it.hostAddress }
                        .iterator(),
                )
            boxInterface.type =
                when {
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                    else -> Libbox.InterfaceTypeOther
                }
            boxInterface.index = networkInterface.index
            runCatching {
                boxInterface.mtu = networkInterface.mtu
            }.onFailure {
                Log.e(
                    "PlatformInterface",
                    "failed to get mtu for interface ${boxInterface.name}",
                    it,
                )
            }
            boxInterface.addresses =
                StringArray(
                    networkInterface.interfaceAddresses.mapTo(mutableListOf()) { it.toPrefix() }
                        .iterator(),
                )
            var dumpFlags = 0
            if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                dumpFlags = OsConstants.IFF_UP or OsConstants.IFF_RUNNING
            }
            if (networkInterface.isLoopback) {
                dumpFlags = dumpFlags or OsConstants.IFF_LOOPBACK
            }
            if (networkInterface.isPointToPoint) {
                dumpFlags = dumpFlags or OsConstants.IFF_POINTOPOINT
            }
            if (networkInterface.supportsMulticast()) {
                dumpFlags = dumpFlags or OsConstants.IFF_MULTICAST
            }
            boxInterface.flags = dumpFlags
            boxInterface.metered =
                !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            interfaces.add(boxInterface)
        }
        return InterfaceArray(interfaces.iterator())
    }

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun clearDNSCache() {
    }

    override fun readWIFIState(): WIFIState? {
        @Suppress("DEPRECATION")
        val wifiInfo =
            Application.wifiManager.connectionInfo ?: return null
        var ssid = wifiInfo.ssid
        if (ssid == "<unknown ssid>") {
            return WIFIState("", "")
        }
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length - 1)
        }
        return WIFIState(ssid, wifiInfo.bssid)
    }

    override fun localDNSTransport(): LocalDNSTransport? = LocalResolver

    override fun startNeighborMonitor(listener: NeighborUpdateListener?) {
        if (listener == null) return
        val callback = object : INeighborTableCallback.Stub() {
            override fun onNeighborTableUpdated(entries: ParceledListSlice<*>?) {
                if (entries == null) return
                @Suppress("UNCHECKED_CAST")
                val list = entries.list as List<NeighborEntry>
                listener.updateNeighborTable(
                    NeighborEntryArray(
                        list.map { entry ->
                            LibboxNeighborEntry().apply {
                                address = entry.address
                                macAddress = entry.macAddress
                                hostname = entry.hostname
                            }
                        }.iterator(),
                    ),
                )
            }
        }
        neighborCallback = callback
        runBlocking(Dispatchers.IO) {
            RootClient.registerNeighborTableCallback(callback)
        }
    }

    override fun usePlatformShell(): Boolean = true

    override fun checkPlatformShell() {
        val available = RootClient.rootAvailable.value ?: runBlocking(Dispatchers.IO) {
            RootClient.checkRootAvailable()
        }
        if (!available) {
            error("missing root permission")
        }
    }

    override fun openShellSession(
        user: PlatformUser?,
        command: String?,
        environ: StringIterator?,
        term: String?,
        rows: Int,
        cols: Int,
    ): ShellSession {
        user!!
        val envList = environ?.toList().orEmpty()
        if (user.uid == Process.myUid()) {
            val resolved = ResolvedUser(user.username, user.uid, user.gid, user.homeDir)
            val shell = UserResolver.findShell(resolved)
            val shellEnv = buildBasicEnvironment(envList.toTypedArray(), shell, resolved.homeDir, term)
            val args = if (command.isNullOrEmpty()) {
                arrayOf("-" + File(shell).name)
            } else {
                arrayOf(File(shell).name, "-c", command)
            }
            val argsIter = args.asIterable().toStringIterator()
            val envIter = shellEnv.asIterable().toStringIterator()
            return if (term.isNullOrEmpty()) {
                Libbox.openNativePipeSession(
                    shell,
                    resolved.homeDir,
                    argsIter,
                    envIter,
                    -1,
                    -1,
                    null,
                )
            } else {
                Libbox.openNativeShellSession(
                    shell,
                    resolved.homeDir,
                    argsIter,
                    envIter,
                    term,
                    rows,
                    cols,
                    -1,
                    -1,
                    null,
                )
            }
        }
        val rootSession = runBlocking(Dispatchers.IO) {
            RootClient.openShellSession(
                user.username,
                command,
                envList.toTypedArray(),
                term,
                rows,
                cols,
            )
        }
        return RootShellSessionWrapper(rootSession)
    }

    override fun readSystemSSHHostKey(): String {
        error("not supported")
    }

    override fun lookupSFTPServer(): String = runBlocking(Dispatchers.IO) {
        RootClient.lookupSFTPServer()
    }

    override fun tailscaleHostname(): String = Settings.Global.getString(
        Application.application.contentResolver,
        Settings.Global.DEVICE_NAME,
    )?.takeIf { it.isNotBlank() }
        ?: "${Build.MANUFACTURER} ${Build.MODEL}"

    override fun usePlatformBridge(): Boolean = RootClient.rootAvailable.value ?: runBlocking(Dispatchers.IO) {
        RootClient.checkRootAvailable()
    }

    override fun createBridge(options: BridgeOptions?): BridgeSession {
        options!!
        val session = runBlocking(Dispatchers.IO) {
            RootClient.openBridge(
                options.bridgeName,
                options.mtu,
                options.inet4Port,
                options.inet6Port,
                options.ruleIndex,
                options.routeTable,
            )
        }
        return RootBridgeSessionWrapper(session)
    }

    override fun usePlatformAutoRedirect(): Boolean = RootClient.rootAvailable.value ?: runBlocking(Dispatchers.IO) {
        RootClient.checkRootAvailable()
    }

    override fun createAutoRedirect(options: ByteArray?, handler: AutoRedirectHandler?): AutoRedirectSession {
        options!!
        handler!!
        val binderHandler = object : IAutoRedirectHandler.Stub() {
            override fun judgeFlow(
                ipProtocol: Int,
                sourceAddress: String?,
                sourcePort: Int,
                destinationAddress: String?,
                destinationPort: Int,
                firstPacket: ByteArray?,
            ): Int = try {
                handler.judgeFlow(
                    ipProtocol,
                    sourceAddress,
                    sourcePort,
                    destinationAddress,
                    destinationPort,
                    firstPacket,
                )
            } catch (e: Exception) {
                throw IllegalStateException(e.message ?: e.toString())
            }

            override fun writeLog(level: Int, message: String?) {
                handler.writeLog(level, message)
            }

            // Binder only marshals a handful of exception types; a Go error escaping here
            // reaches the root service as a bare failure without the message, so it is
            // converted to IllegalStateException.
            override fun getRedirectListener(): ParcelFileDescriptor = try {
                ParcelFileDescriptor.adoptFd(handler.redirectListenerFileDescriptor())
            } catch (e: Exception) {
                throw IllegalStateException(e.message ?: e.toString())
            }

            override fun getRouteAddressSet(): ParcelFileDescriptor = try {
                ParcelFileDescriptor.adoptFd(handler.routeAddressSetFileDescriptor())
            } catch (e: Exception) {
                throw IllegalStateException(e.message ?: e.toString())
            }
        }
        val session = runBlocking(Dispatchers.IO) {
            RootClient.startAutoRedirect(options, binderHandler)
        }
        return RootAutoRedirectSessionWrapper(session)
    }

    // Without a bypass flag on the queue rules, a root process dying while the
    // VPN is up leaves every new flow of VPN apps dropped in the kernel, so the
    // service is stopped instead of running with a dead network.
    private class RootAutoRedirectSessionWrapper(
        private val session: IAutoRedirectSession,
    ) : AutoRedirectSession,
        IBinder.DeathRecipient {
        init {
            session.asBinder().linkToDeath(this, 0)
        }

        override fun binderDied() {
            Log.e("PlatformInterface", "auto-redirect root service died, stopping service")
            Application.application.sendBroadcast(
                Intent(Action.SERVICE_CLOSE).setPackage(Application.application.packageName),
            )
        }

        override fun close() {
            try {
                session.asBinder().unlinkToDeath(this, 0)
            } catch (_: NoSuchElementException) {
            }
            try {
                session.close()
            } catch (_: DeadObjectException) {
            }
        }

        override fun updateRouteAddressSet() {
            session.updateRouteAddressSet()
        }
    }

    override fun lookupUser(username: String?): io.nekohasekai.libbox.PlatformUser {
        val resolved = UserResolver.resolve(Application.packageManager, username!!)
        val platformUser = io.nekohasekai.libbox.PlatformUser()
        platformUser.username = resolved.packageName
        platformUser.uid = resolved.uid
        platformUser.gid = resolved.gid
        platformUser.homeDir = resolved.homeDir
        return platformUser
    }

    override fun registerMyInterface(name: String?) {
    }

    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) {
        val callback = neighborCallback ?: return
        neighborCallback = null
        runBlocking(Dispatchers.IO) {
            RootClient.unregisterNeighborTableCallback(callback)
        }
    }

    private class RootBridgeSessionWrapper(
        private val session: IBridgeSession,
    ) : BridgeSession {
        override fun fileDescriptor(): Int = session.fileDescriptor.detachFd()

        override fun name(): String = session.name

        override fun inet6Active(): Boolean = session.isInet6Active

        override fun setEgress(interfaceName: String?) {
            session.setEgress(interfaceName ?: "")
        }

        override fun close() {
            session.close()
        }
    }

    private class RootShellSessionWrapper(
        private val rootSession: IRootShellSession,
    ) : ShellSession {
        private val masterPfd: ParcelFileDescriptor = rootSession.masterFD

        override fun masterFD(): Int = masterPfd.fd

        override fun resize(rows: Int, cols: Int) {
            rootSession.resize(rows, cols)
        }

        override fun signal(signal: Int) {
            rootSession.signal(signal)
        }

        override fun waitExit(): Int = rootSession.waitFor()

        override fun close() {
            masterPfd.close()
            rootSession.close()
        }
    }

    private class NeighborEntryArray(private val iterator: Iterator<LibboxNeighborEntry>) : NeighborEntryIterator {
        override fun hasNext(): Boolean = iterator.hasNext()

        override fun next(): LibboxNeighborEntry = iterator.next()
    }

    private class InterfaceArray(private val iterator: Iterator<LibboxNetworkInterface>) : NetworkInterfaceIterator {
        override fun hasNext(): Boolean = iterator.hasNext()

        override fun next(): LibboxNetworkInterface = iterator.next()
    }

    class StringArray(private val iterator: Iterator<String>) : StringIterator {
        override fun len(): Int {
            // not used by core
            return 0
        }

        override fun hasNext(): Boolean = iterator.hasNext()

        override fun next(): String = iterator.next()
    }

    private fun InterfaceAddress.toPrefix(): String = if (address is Inet6Address) {
        "${Inet6Address.getByAddress(address.address).hostAddress}/$networkPrefixLength"
    } else {
        "${address.hostAddress}/$networkPrefixLength"
    }

    private val NetworkInterface.flags: Int
        @SuppressLint("SoonBlockedPrivateApi")
        get() {
            val getFlagsMethod = NetworkInterface::class.java.getDeclaredMethod("getFlags")
            return getFlagsMethod.invoke(this) as Int
        }
}
