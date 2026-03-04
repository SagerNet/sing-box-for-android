package io.nekohasekai.sfa.bg

import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteCallbackList
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.NeighborEntryIterator
import io.nekohasekai.libbox.NeighborSubscription
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.vendor.PrivilegedServiceUtils
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class RootServer : RootService() {

    private val neighborCallbacks = RemoteCallbackList<INeighborTableCallback>()
    private var neighborSubscription: NeighborSubscription? = null

    private val hostnameByMAC = ConcurrentHashMap<String, String>()

    @Volatile
    private var lastNeighborEntries: List<Pair<String, String>>? = null

    private var tetheringCallback: Any? = null
    private var tetheringManager: Any? = null

    private val binder = object : IRootService.Stub() {
        override fun destroy() {
            stopSelf()
        }

        override fun getInstalledPackages(flags: Int, userId: Int): ParceledListSlice<PackageInfo> {
            val allPackages = PrivilegedServiceUtils.getInstalledPackages(flags, userId)
            return ParceledListSlice(allPackages)
        }

        override fun installPackage(apk: ParcelFileDescriptor?, size: Long, userId: Int) {
            if (apk == null) throw IOException("APK file descriptor is null")
            PrivilegedServiceUtils.installPackage(apk, size, userId)
        }

        override fun exportDebugInfo(outputPath: String?): String = DebugInfoExporter.export(
            this@RootServer,
            outputPath!!,
            BuildConfig.APPLICATION_ID,
        )

        override fun registerNeighborTableCallback(callback: INeighborTableCallback?) {
            if (callback == null) return
            neighborCallbacks.register(callback)
            synchronized(neighborCallbacks) {
                if (neighborSubscription == null) {
                    try {
                        neighborSubscription =
                            Libbox.subscribeNeighborTable(object : NeighborUpdateListener {
                                override fun updateNeighborTable(entries: NeighborEntryIterator?) {
                                    if (entries == null) return
                                    val rawList = mutableListOf<Pair<String, String>>()
                                    while (entries.hasNext()) {
                                        val entry = entries.next()
                                        rawList.add(entry.address to entry.macAddress)
                                    }
                                    lastNeighborEntries = rawList
                                    broadcastEnrichedEntries(rawList)
                                }
                            })
                    } catch (e: Exception) {
                        Log.e("RootServer", "subscribeNeighborTable failed", e)
                    }
                    startTetheringMonitor()
                }
            }
        }

        override fun unregisterNeighborTableCallback(callback: INeighborTableCallback?) {
            if (callback == null) return
            neighborCallbacks.unregister(callback)
            synchronized(neighborCallbacks) {
                if (neighborCallbacks.registeredCallbackCount == 0) {
                    neighborSubscription?.close()
                    neighborSubscription = null
                    stopTetheringMonitor()
                }
            }
        }
    }

    private fun broadcastEnrichedEntries(rawList: List<Pair<String, String>>) {
        val list = rawList.map { (address, mac) ->
            NeighborEntry(address, mac, hostnameByMAC[mac.uppercase()] ?: "")
        }
        Log.d("RootServer", "neighborTable updated: ${list.size} entries")
        val slice = ParceledListSlice(list)
        val count = neighborCallbacks.beginBroadcast()
        try {
            repeat(count) { i ->
                try {
                    neighborCallbacks.getBroadcastItem(i).onNeighborTableUpdated(slice)
                } catch (_: Exception) {
                }
            }
        } finally {
            neighborCallbacks.finishBroadcast()
        }
    }

    // TetheringManager reflection (API 30+)

    private val classTetheredClient by lazy {
        Class.forName("android.net.TetheredClient")
    }
    private val getMacAddress by lazy {
        classTetheredClient.getDeclaredMethod("getMacAddress")
    }
    private val getAddresses by lazy {
        classTetheredClient.getDeclaredMethod("getAddresses")
    }
    private val classAddressInfo by lazy {
        Class.forName("android.net.TetheredClient\$AddressInfo")
    }
    private val getHostname by lazy {
        classAddressInfo.getDeclaredMethod("getHostname")
    }

    private fun startTetheringMonitor() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val manager = getSystemService("tethering") ?: return
            tetheringManager = manager
            val callbackClass =
                Class.forName("android.net.TetheringManager\$TetheringEventCallback")
            val registerMethod = manager.javaClass.getMethod(
                "registerTetheringEventCallback",
                java.util.concurrent.Executor::class.java,
                callbackClass,
            )
            val proxy = Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass),
            ) { proxyObject, method, args ->
                when (method.name) {
                    "hashCode" -> System.identityHashCode(proxyObject)
                    "equals" -> proxyObject === args?.get(0)
                    "toString" -> proxyObject.javaClass.name + "@" +
                        Integer.toHexString(System.identityHashCode(proxyObject))
                    "onClientsChanged" -> {
                        if (args != null) {
                            @Suppress("UNCHECKED_CAST")
                            handleClientsChanged(args[0] as Collection<*>)
                        }
                        null
                    }
                    else -> null
                }
            }
            tetheringCallback = proxy
            registerMethod.invoke(manager, Executors.newSingleThreadExecutor(), proxy)
            Log.d("RootServer", "TetheringManager monitor started")
        } catch (e: Exception) {
            Log.e("RootServer", "startTetheringMonitor failed", e)
        }
    }

    private fun stopTetheringMonitor() {
        val manager = tetheringManager ?: return
        val callback = tetheringCallback ?: return
        try {
            val callbackClass =
                Class.forName("android.net.TetheringManager\$TetheringEventCallback")
            val unregisterMethod = manager.javaClass.getMethod(
                "unregisterTetheringEventCallback",
                callbackClass,
            )
            unregisterMethod.invoke(manager, callback)
        } catch (e: Exception) {
            Log.e("RootServer", "stopTetheringMonitor failed", e)
        }
        tetheringCallback = null
        tetheringManager = null
        hostnameByMAC.clear()
    }

    private fun handleClientsChanged(clients: Collection<*>) {
        hostnameByMAC.clear()
        for (client in clients) {
            if (client == null) continue
            try {
                val mac = getMacAddress.invoke(client).toString().uppercase()
                @Suppress("UNCHECKED_CAST")
                val addresses = getAddresses.invoke(client) as List<*>
                for (info in addresses) {
                    if (info == null) continue
                    val hostname = getHostname.invoke(info) as? String
                    if (!hostname.isNullOrEmpty()) {
                        hostnameByMAC[mac] = hostname
                    }
                }
            } catch (e: Exception) {
                Log.e("RootServer", "handleClientsChanged reflection error", e)
            }
        }
        Log.d("RootServer", "tethered clients updated: ${hostnameByMAC.size} hostnames")
        lastNeighborEntries?.let { broadcastEnrichedEntries(it) }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        stopTetheringMonitor()
        neighborSubscription?.close()
        neighborSubscription = null
        neighborCallbacks.kill()
        super.onDestroy()
    }
}
