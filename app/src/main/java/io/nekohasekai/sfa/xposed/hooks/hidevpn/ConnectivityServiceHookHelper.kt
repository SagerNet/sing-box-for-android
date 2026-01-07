package io.nekohasekai.sfa.xposed.hooks.hidevpn

import android.content.Context
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkInfo
import android.os.Build
import android.os.IBinder
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import io.nekohasekai.sfa.xposed.HookErrorStore
import io.nekohasekai.sfa.xposed.PrivilegeSettingsStore
import io.nekohasekai.sfa.xposed.VpnAppStore
import io.nekohasekai.sfa.xposed.VpnSanitizer
import io.nekohasekai.sfa.xposed.hooks.SafeMethodHook
import io.nekohasekai.sfa.xposed.hooks.XHook
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class ConnectivityServiceHookHelper(private val classLoader: ClassLoader) : XHook {
    companion object {
        private const val SOURCE = "ConnectivityServiceHookHelper"
    }

    private val hooked = AtomicBoolean(false)
    private val initializerHooked = AtomicBoolean(false)
    private var classLoadUnhook: XC_MethodHook.Unhook? = null
    private val serviceManagerHooked = AtomicBoolean(false)
    private var connectivityClassLoader: ClassLoader = classLoader
    private val skipLogKeys = ConcurrentHashMap<String, Boolean>()
    val sdkInt = Build.VERSION.SDK_INT

    lateinit var cls: Class<*>
        private set

    override fun injectHook() {
        val foundClass = findConnectivityServiceClass()
        if (foundClass != null) {
            installHooks(foundClass, "direct")
            return
        }
        hookConnectivityServiceInitializer()
        hookClassLoaderFallback()
        tryHookFromServiceManager()
    }

    private fun installHooks(cls: Class<*>, source: String) {
        if (!hooked.compareAndSet(false, true)) {
            return
        }
        this.cls = cls
        connectivityClassLoader = cls.classLoader ?: classLoader
        HookErrorStore.i(
            SOURCE,
            "Installing ConnectivityService hooks ($source) cls=${cls.name} loader=${connectivityClassLoader.javaClass.name}",
        )

        // Install all individual hooks
        HookConnectivityManagerGetActiveNetwork(this).install()
        HookConnectivityManagerGetActiveNetworkInfo(this).install()
        HookConnectivityManagerGetNetworkInfo(this).install()
        HookConnectivityManagerGetAllNetworkInfo(this).install()
        HookConnectivityManagerGetAllNetworks(this).install()
        HookConnectivityManagerGetNetworkForType(this).install()
        HookConnectivityManagerGetNetworkCapabilities(this).install()
        HookConnectivityManagerGetLinkProperties(this).install()
        HookConnectivityManagerRequestNetwork(this).install()
        HookConnectivityManagerGetDefaultProxy(this).install()
        HookConnectivityManagerConnectivityAction(this).install()
        HookConnectivityManagerProxyChangeAction(this).install()

        HookErrorStore.i(SOURCE, "Hooked ConnectivityService ($source) cls=${cls.name}")
    }

    // region Service Discovery

    private fun findConnectivityServiceClass(): Class<*>? {
        val candidates = listOf(
            "com.android.server.ConnectivityService",
        )
        val loaders = listOf(
            classLoader,
            classLoader.parent,
            Thread.currentThread().contextClassLoader,
            ClassLoader.getSystemClassLoader(),
            ClassLoader.getSystemClassLoader()?.parent,
        )
        for (name in candidates) {
            for (loader in loaders) {
                try {
                    val found = if (loader != null) {
                        Class.forName(name, false, loader)
                    } else {
                        Class.forName(name)
                    }
                    HookErrorStore.i(
                        SOURCE,
                        "ConnectivityService class found: $name via ${loader?.javaClass?.name ?: "null"}",
                    )
                    return found
                } catch (_: Throwable) {
                }
            }
        }
        HookErrorStore.i(SOURCE, "ConnectivityService class not found in known classloaders")
        return null
    }

    private fun hookConnectivityServiceInitializer() {
        if (sdkInt < 31 || sdkInt >= 33) {
            HookErrorStore.d(SOURCE, "Skip ConnectivityServiceInitializer: sdk=$sdkInt (only exists in API 31-32)")
            return
        }
        val candidates = listOf(
            "com.android.server.ConnectivityServiceInitializer",
            "com.android.server.ConnectivityServiceInitializerB",
        )
        val loaders = listOf(
            classLoader,
            classLoader.parent,
            Thread.currentThread().contextClassLoader,
            ClassLoader.getSystemClassLoader(),
            ClassLoader.getSystemClassLoader()?.parent,
        )
        for (name in candidates) {
            for (loader in loaders) {
                val cls = try {
                    if (loader != null) {
                        Class.forName(name, false, loader)
                    } else {
                        Class.forName(name)
                    }
                } catch (_: Throwable) {
                    null
                } ?: continue
                try {
                    if (initializerHooked.get()) {
                        return
                    }
                    XposedHelpers.findAndHookConstructor(
                        cls,
                        Context::class.java,
                        object : SafeMethodHook(SOURCE) {
                            override fun afterHook(param: MethodHookParam) {
                                if (hooked.get()) return
                                val instance = param.thisObject ?: return
                                val connectivity = findConnectivityServiceInstance(instance) ?: return
                                installHooks(connectivity.javaClass, "initializer_ctor")
                            }
                        },
                    )
                    XposedHelpers.findAndHookMethod(
                        cls,
                        "onStart",
                        object : SafeMethodHook(SOURCE) {
                            override fun afterHook(param: MethodHookParam) {
                                if (hooked.get()) return
                                val instance = param.thisObject ?: return
                                val connectivity = findConnectivityServiceInstance(instance) ?: return
                                installHooks(connectivity.javaClass, "initializer")
                            }
                        },
                    )
                    initializerHooked.set(true)
                    HookErrorStore.i(
                        SOURCE,
                        "Hooked $name (ctor/onStart) via ${loader?.javaClass?.name ?: "null"}",
                    )
                    return
                } catch (e: Throwable) {
                    HookErrorStore.w(SOURCE, "Hook $name failed: ${e.message}", e)
                }
            }
        }
        HookErrorStore.d(SOURCE, "ConnectivityServiceInitializer not found in known classloaders")
    }

    private fun hookClassLoaderFallback() {
        if (classLoadUnhook != null) {
            return
        }
        try {
            classLoadUnhook = XposedHelpers.findAndHookMethod(
                ClassLoader::class.java,
                "loadClass",
                String::class.java,
                Boolean::class.javaPrimitiveType,
                object : SafeMethodHook(SOURCE) {
                    override fun afterHook(param: MethodHookParam) {
                        val name = param.args[0] as? String ?: return
                        if (hooked.get()) {
                            classLoadUnhook?.unhook()
                            classLoadUnhook = null
                            return
                        }
                        when (name) {
                            "com.android.server.ConnectivityService" -> {
                                val cls = param.result as? Class<*> ?: return
                                HookErrorStore.i(
                                    SOURCE,
                                    "ConnectivityService loaded via ${param.thisObject.javaClass.name}",
                                )
                                installHooks(cls, "loadClass")
                                classLoadUnhook?.unhook()
                                classLoadUnhook = null
                            }
                            "com.android.server.ConnectivityServiceInitializer",
                            "com.android.server.ConnectivityServiceInitializerB",
                            -> {
                                if (sdkInt < 31) return
                                if (initializerHooked.get()) return
                                val cls = param.result as? Class<*> ?: return
                                HookErrorStore.i(
                                    SOURCE,
                                    "ConnectivityServiceInitializer loaded via ${param.thisObject.javaClass.name}",
                                )
                                hookConnectivityServiceInitializerClass(cls)
                            }
                        }
                    }
                },
            )
            HookErrorStore.i(SOURCE, "Hooked ClassLoader.loadClass for ConnectivityService")
        } catch (e: Throwable) {
            HookErrorStore.w(SOURCE, "Hook ClassLoader.loadClass failed: ${e.message}", e)
        }
    }

    private fun tryHookFromServiceManager() {
        if (hooked.get()) return
        val binder = try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val checkService = serviceManager.getMethod("checkService", String::class.java)
            checkService.invoke(null, Context.CONNECTIVITY_SERVICE) as? IBinder
        } catch (_: Throwable) {
            null
        }
        if (binder != null) {
            HookErrorStore.i(
                SOURCE,
                "ConnectivityService binder from ServiceManager: ${binder.javaClass.name}",
            )
            installHooks(binder.javaClass, "ServiceManager.checkService")
            return
        }
        hookServiceManagerAddService()
    }

    private fun hookServiceManagerAddService() {
        if (!serviceManagerHooked.compareAndSet(false, true)) {
            return
        }
        try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            XposedHelpers.findAndHookMethod(
                serviceManager,
                "addService",
                String::class.java,
                IBinder::class.java,
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : SafeMethodHook(SOURCE) {
                    override fun afterHook(param: MethodHookParam) {
                        if (hooked.get()) return
                        val name = param.args[0] as? String ?: return
                        if (name != Context.CONNECTIVITY_SERVICE) return
                        val binder = param.args[1] as? IBinder ?: return
                        HookErrorStore.i(
                            SOURCE,
                            "ConnectivityService registered: ${binder.javaClass.name}",
                        )
                        installHooks(binder.javaClass, "ServiceManager.addService")
                    }
                },
            )
            HookErrorStore.i(SOURCE, "Hooked ServiceManager.addService for ConnectivityService")
        } catch (e: Throwable) {
            HookErrorStore.w(SOURCE, "Hook ServiceManager.addService failed: ${e.message}", e)
        }
    }

    private fun hookConnectivityServiceInitializerClass(cls: Class<*>) {
        if (sdkInt < 31) return
        if (initializerHooked.get()) return
        try {
            XposedHelpers.findAndHookConstructor(
                cls,
                Context::class.java,
                object : SafeMethodHook(SOURCE) {
                    override fun afterHook(param: MethodHookParam) {
                        if (hooked.get()) return
                        val instance = param.thisObject ?: return
                        val connectivity = findConnectivityServiceInstance(instance) ?: return
                        installHooks(connectivity.javaClass, "initializer_ctor")
                    }
                },
            )
            XposedHelpers.findAndHookMethod(
                cls,
                "onStart",
                object : SafeMethodHook(SOURCE) {
                    override fun afterHook(param: MethodHookParam) {
                        if (hooked.get()) return
                        val instance = param.thisObject ?: return
                        val connectivity = findConnectivityServiceInstance(instance) ?: return
                        installHooks(connectivity.javaClass, "initializer")
                    }
                },
            )
            initializerHooked.set(true)
            HookErrorStore.i(SOURCE, "Hooked ${cls.name} (ctor/onStart) via loadClass")
        } catch (e: Throwable) {
            HookErrorStore.w(SOURCE, "Hook ${cls.name} via loadClass failed: ${e.message}", e)
        }
    }

    private fun findConnectivityServiceInstance(instance: Any): Any? {
        try {
            val direct = XposedHelpers.getObjectField(instance, "mConnectivity")
            if (direct != null) {
                return direct
            }
        } catch (_: Throwable) {
        }
        return try {
            val fields = instance.javaClass.declaredFields
            for (field in fields) {
                if (field.type.name.endsWith(".ConnectivityService")) {
                    field.isAccessible = true
                    val value = field.get(instance)
                    if (value != null) {
                        return value
                    }
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    // endregion

    // region Helper Methods

    fun shouldHide(connectivityService: Any, uid: Int): Boolean {
        if (!PrivilegeSettingsStore.isEnabled()) {
            logSkipOnce(uid, "hide_disabled", "Skip hide: uid=$uid hide settings disabled")
            return false
        }
        if (!PrivilegeSettingsStore.isUidSelected(uid)) {
            logSkipOnce(uid, "hide_not_selected", "Skip hide: uid=$uid not in hide list")
            return false
        }
        if (VpnAppStore.isVpnUidExcludeSelf(uid)) {
            logSkipOnce(uid, "uid_vpn_app", "Skip hide: uid=$uid vpn app")
            return false
        }
        val hasVpn = hasVpnForUid(connectivityService, uid)
        if (!hasVpn) {
            logSkipOnce(uid, "uid_no_vpn", "Skip hide: uid=$uid noVpnForUid")
        }
        return hasVpn
    }

    fun hasVpnForUid(connectivityService: Any, uid: Int): Boolean {
        if (sdkInt >= 31) {
            return XposedHelpers.callMethod(connectivityService, "getVpnForUid", uid) != null
        }
        @Suppress("UNCHECKED_CAST")
        val networks = XposedHelpers.callMethod(connectivityService, "getVpnUnderlyingNetworks", uid)
            as? Array<Network>
        return networks != null && networks.isNotEmpty()
    }

    fun isVpnNetwork(connectivityService: Any, network: Network): Boolean {
        val nai = XposedHelpers.callMethod(connectivityService, "getNetworkAgentInfoForNetwork", network)
            ?: return false
        return isVpnNai(nai)
    }

    fun isVpnNai(nai: Any): Boolean {
        return XposedHelpers.callMethod(nai, "isVPN") as Boolean
    }

    fun getUnderlyingNetwork(connectivityService: Any, uid: Int): Network? {
        val nai = getUnderlyingNai(connectivityService, uid) ?: return null
        return XposedHelpers.callMethod(nai, "network") as Network?
    }

    fun getUnderlyingLinkProperties(connectivityService: Any, uid: Int): LinkProperties? {
        val nai = getUnderlyingNai(connectivityService, uid) ?: return null
        val lp = XposedHelpers.getObjectField(nai, "linkProperties") as LinkProperties?
            ?: return null
        return VpnSanitizer.cloneLinkProperties(lp)
    }

    fun getUnderlyingNetworkInfo(connectivityService: Any, uid: Int): NetworkInfo? {
        val nai = getUnderlyingNai(connectivityService, uid) ?: return null
        return XposedHelpers.callMethod(connectivityService, "getFilteredNetworkInfo", nai, uid, false)
            as NetworkInfo?
    }

    fun getUnderlyingNai(connectivityService: Any, uid: Int): Any? {
        @Suppress("UNCHECKED_CAST")
        val networks = XposedHelpers.callMethod(connectivityService, "getVpnUnderlyingNetworks", uid)
            as? Array<Network>
        if (networks != null && networks.isNotEmpty()) {
            return XposedHelpers.callMethod(connectivityService, "getNetworkAgentInfoForNetwork", networks[0])
        }
        val defaultNai = XposedHelpers.callMethod(connectivityService, "getDefaultNetwork")
        if (defaultNai != null && !isVpnNai(defaultNai)) {
            return defaultNai
        }
        return null
    }

    /**
     * Resolves a class from the Connectivity module, handling APEX package rewriting.
     *
     * When the Connectivity module runs as an APEX (Android 12+), all classes get prefixed
     * with "android.net.connectivity.". This method derives the correct prefix from
     * the already-loaded ConnectivityService class.
     *
     * @param simpleClassName Simple class name (e.g., "ProxyTracker")
     * @param subPackage Sub-package under com.android.server (e.g., "connectivity"), or null
     */
    fun resolveConnectivityModuleClass(simpleClassName: String, subPackage: String? = null): Class<*> {
        val base = cls.name
        val serverPackage = if (base.endsWith(".ConnectivityService")) {
            base.removeSuffix(".ConnectivityService")
        } else {
            base.substringBeforeLast(".ConnectivityService", base)
        }

        val fullClassName = if (subPackage != null) {
            "$serverPackage.$subPackage.$simpleClassName"
        } else {
            "$serverPackage.$simpleClassName"
        }

        return XposedHelpers.findClass(fullClassName, connectivityClassLoader)
    }

    fun resolveNriAndNaiClasses(): Pair<Class<*>, Class<*>> {
        val nriClass = XposedHelpers.findClass(
            cls.name + '$' + "NetworkRequestInfo",
            connectivityClassLoader,
        )
        val naiClass = resolveConnectivityModuleClass("NetworkAgentInfo", "connectivity")
        return Pair(nriClass, naiClass)
    }

    fun getAsUid(nri: Any): Int {
        val fieldName = if (sdkInt >= 31) "mAsUid" else "mUid"
        return XposedHelpers.getIntField(nri, fieldName)
    }

    fun logSkipOnce(uid: Int, reason: String, message: String) {
        val key = "$uid:$reason"
        if (skipLogKeys.putIfAbsent(key, true) == null) {
            HookErrorStore.d(SOURCE, message)
        }
    }

    // endregion
}
