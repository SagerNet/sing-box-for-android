package io.nekohasekai.sfa.bg

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
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
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.ktx.toStringIterator
import io.nekohasekai.sfa.vendor.PrivilegedServiceUtils
import java.io.File
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

        override fun openShellSession(
            user: String?,
            command: String?,
            env: Array<out String>?,
            term: String?,
            rows: Int,
            cols: Int,
        ): IRootShellSession {
            val resolved = UserResolver.resolve(packageManager, user!!)
            val shell: String
            val shellEnv: Array<String>
            val cwd: String
            if (resolved.packageName == UserResolver.TERMUX_PACKAGE) {
                val termuxPrefix = File(UserResolver.TERMUX_PREFIX)
                val actualShell = UserResolver.findTermuxShell(termuxPrefix, resolved.homeDir)
                cwd = resolved.homeDir
                shellEnv =
                    buildTermuxEnvironment(env, actualShell, cwd, termuxPrefix.absolutePath, term)
                shell = if (command.isNullOrEmpty()) {
                    val loginBin = File(termuxPrefix, "bin/login")
                    if (loginBin.canExecute()) loginBin.absolutePath else actualShell
                } else {
                    actualShell
                }
            } else if (resolved.uid == 0) {
                val termuxPrefix = File(UserResolver.TERMUX_PREFIX)
                val termuxAvailable = File(termuxPrefix, "bin").isDirectory
                if (termuxAvailable) {
                    shell = UserResolver.findTermuxShell(termuxPrefix, UserResolver.TERMUX_HOME)
                    cwd = UserResolver.TERMUX_HOME
                    shellEnv = buildTermuxEnvironment(
                        env,
                        shell,
                        cwd,
                        termuxPrefix.absolutePath,
                        term,
                    )
                } else {
                    shell = "/system/bin/sh"
                    cwd = "/data/local"
                    shellEnv = buildBasicEnvironment(env, shell, cwd, term)
                }
            } else {
                shell = UserResolver.findShell(resolved)
                cwd = resolved.homeDir
                shellEnv = buildBasicEnvironment(env, shell, cwd, term)
            }
            val args: Array<String>
            if (command.isNullOrEmpty()) {
                args = arrayOf("-" + File(shell).name)
            } else {
                args = arrayOf(File(shell).name, "-c", command)
            }
            val supplementaryGids = if (resolved.packageName == "root" || resolved.packageName == "shell") {
                intArrayOf()
            } else {
                packageManager.getPackageGids(resolved.packageName)
            }
            val argsIter = args.asIterable().toStringIterator()
            val envIter = shellEnv.asIterable().toStringIterator()
            val groupsIter = IntArrayIterator(supplementaryGids)
            val isPipe = term.isNullOrEmpty()
            val session = if (isPipe) {
                Libbox.openNativePipeSession(
                    shell,
                    cwd,
                    argsIter,
                    envIter,
                    resolved.uid,
                    resolved.gid,
                    groupsIter,
                )
            } else {
                Libbox.openNativeShellSession(
                    shell, cwd, argsIter, envIter,
                    term, rows, cols,
                    resolved.uid, resolved.gid, groupsIter,
                )
            }
            return RootShellSession(session)
        }

        override fun lookupSFTPServer(): String {
            val termuxPrefix = File(UserResolver.TERMUX_PREFIX)
            for (name in arrayOf("libexec/sftp-server", "lib/openssh/sftp-server")) {
                val candidate = File(termuxPrefix, name)
                if (candidate.canExecute()) return candidate.absolutePath
            }
            throw IOException("sftp-server not found, install openssh in Termux")
        }
    }

    private fun buildTermuxEnvironment(
        sshEnv: Array<out String>?,
        shell: String,
        home: String,
        prefix: String,
        term: String?,
    ): Array<String> {
        val env = parseEnvArray(sshEnv)
        env["HOME"] = home
        env["PREFIX"] = prefix
        env["PATH"] = "$prefix/bin"
        env["TMPDIR"] = "$prefix/tmp"
        env["SHELL"] = shell
        env["LANG"] = "en_US.UTF-8"
        env["COLORTERM"] = "truecolor"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            env["LD_LIBRARY_PATH"] = "$prefix/lib"
        } else {
            env.remove("LD_LIBRARY_PATH")
        }
        val termuxExec = File("$prefix/lib/libtermux-exec.so")
        if (termuxExec.exists()) {
            env["LD_PRELOAD"] = termuxExec.absolutePath
        }
        if (!term.isNullOrEmpty()) {
            env["TERM"] = term
        }
        addAndroidSystemEnvironment(env)
        return env.map { (k, v) -> "$k=$v" }.toTypedArray()
    }

    private class RootShellSession(
        private val session: ShellSession,
    ) : IRootShellSession.Stub() {

        override fun getMasterFD(): ParcelFileDescriptor = ParcelFileDescriptor.fromFd(session.masterFD())

        override fun resize(rows: Int, cols: Int) {
            session.resize(rows, cols)
        }

        override fun signal(sig: Int) {
            session.signal(sig)
        }

        override fun waitFor(): Int = session.waitExit()

        override fun close() {
            session.close()
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
                    "toString" ->
                        proxyObject.javaClass.name + "@" +
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

internal fun parseEnvArray(sshEnv: Array<out String>?): MutableMap<String, String> {
    val env = mutableMapOf<String, String>()
    sshEnv?.forEach { entry ->
        val idx = entry.indexOf('=')
        if (idx > 0) env[entry.substring(0, idx)] = entry.substring(idx + 1)
    }
    return env
}

internal fun buildBasicEnvironment(
    sshEnv: Array<out String>?,
    shell: String,
    home: String,
    term: String?,
): Array<String> {
    val env = parseEnvArray(sshEnv)
    env["HOME"] = home
    env["PATH"] = "/system/bin:/system/xbin:/vendor/bin"
    env["SHELL"] = shell
    env["TMPDIR"] = "/data/local/tmp"
    if (!term.isNullOrEmpty()) {
        env["TERM"] = term
    }
    addAndroidSystemEnvironment(env)
    return env.map { (k, v) -> "$k=$v" }.toTypedArray()
}

internal fun addAndroidSystemEnvironment(env: MutableMap<String, String>) {
    val androidVars = arrayOf(
        "ANDROID_ASSETS", "ANDROID_DATA", "ANDROID_ROOT", "ANDROID_STORAGE",
        "EXTERNAL_STORAGE", "ASEC_MOUNTPOINT", "LOOP_MOUNTPOINT",
        "ANDROID_RUNTIME_ROOT", "ANDROID_ART_ROOT",
        "ANDROID_I18N_ROOT", "ANDROID_TZDATA_ROOT",
        "BOOTCLASSPATH", "DEX2OATBOOTCLASSPATH", "SYSTEMSERVERCLASSPATH",
    )
    for (name in androidVars) {
        val value = System.getenv(name)
        if (value != null) {
            env[name] = value
        }
    }
}
