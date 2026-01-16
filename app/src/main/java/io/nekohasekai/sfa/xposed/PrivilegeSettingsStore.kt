package io.nekohasekai.sfa.xposed

import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object PrivilegeSettingsStore {
    private const val SETTINGS_DIR = "/data/system/sing-box"
    private const val SETTINGS_FILE = "privilege_settings.conf"
    @Volatile
    private var enabled = false
    @Volatile
    private var packageSet: Set<String> = emptySet()
    @Volatile
    private var interfaceRenameEnabled = false
    @Volatile
    private var interfacePrefix = "en"
    private val uidCache = ConcurrentHashMap<Int, Boolean>()

    private val appGlobalsClass by lazy { Class.forName("android.app.AppGlobals") }
    private val getPackageManagerMethod by lazy { appGlobalsClass.getMethod("getPackageManager") }
    private var getPackagesForUidMethod: Method? = null

    fun update(
        enabled: Boolean,
        packages: Set<String>,
        interfaceRenameEnabled: Boolean,
        interfacePrefix: String,
    ) {
        this.enabled = enabled
        packageSet = packages
        this.interfaceRenameEnabled = interfaceRenameEnabled
        this.interfacePrefix = normalizePrefix(interfacePrefix)
        uidCache.clear()
        HookErrorStore.i(
            "PrivilegeSettingsStore",
            "PrivilegeSettings updated: enabled=$enabled size=${packages.size} rename=$interfaceRenameEnabled prefix=${this.interfacePrefix}",
        )
        writeSettingsFile()
    }

    fun isEnabled(): Boolean = enabled

    fun shouldRenameInterface(): Boolean {
        return interfaceRenameEnabled
    }

    fun interfacePrefix(): String = interfacePrefix

    fun isUidSelected(uid: Int): Boolean {
        val cached = uidCache[uid]
        if (cached != null) {
            return cached
        }
        val selected = getPackagesForUid(uid).any { packageSet.contains(it) }
        uidCache[uid] = selected
        return selected
    }

    fun shouldHideUid(uid: Int): Boolean {
        if (!enabled) {
            return false
        }
        return isUidSelected(uid)
    }

    private fun normalizePrefix(prefix: String): String {
        val trimmed = prefix.trim()
        if (trimmed.isEmpty()) {
            return "en"
        }
        val filtered = buildString(trimmed.length) {
            for (ch in trimmed) {
                if (ch.isLetterOrDigit() || ch == '_') {
                    append(ch)
                }
            }
        }
        return if (filtered.isEmpty()) "en" else filtered
    }

    private fun writeSettingsFile() {
        try {
            val dir = File(SETTINGS_DIR)
            if (!dir.exists() && !dir.mkdirs()) {
                HookErrorStore.e("PrivilegeSettingsStore", "Failed to create settings dir: ${dir.path}")
                return
            }
            val file = File(dir, SETTINGS_FILE)
            val packagesLine = packageSet.sorted().joinToString(",")
            val content = buildString {
                append("version=1\n")
                append("enabled=")
                append(if (enabled) "1" else "0")
                append('\n')
                append("rename=")
                append(if (interfaceRenameEnabled) "1" else "0")
                append('\n')
                append("prefix=")
                append(interfacePrefix)
                append('\n')
                append("packages=")
                append(packagesLine)
                append('\n')
            }
            file.writeText(content)
            file.setReadable(true, true)
            file.setWritable(true, true)
        } catch (e: Throwable) {
            HookErrorStore.e("PrivilegeSettingsStore", "Failed to write privilege settings file", e)
        }
    }

    private fun getPackagesForUid(uid: Int): List<String> {
        val pm = getPackageManager() ?: return emptyList()
        return try {
            val method = getPackagesForUidMethod ?: run {
                pm.javaClass.getMethod("getPackagesForUid", Int::class.javaPrimitiveType).also {
                    getPackagesForUidMethod = it
                }
            }
            val result = method.invoke(pm, uid)
            when (result) {
                is Array<*> -> result.filterIsInstance<String>()
                is List<*> -> result.filterIsInstance<String>()
                else -> emptyList()
            }
        } catch (e: Throwable) {
            HookErrorStore.e("PrivilegeSettingsStore", "getPackagesForUid failed for uid=$uid", e)
            emptyList()
        }
    }

    private fun getPackageManager(): Any? {
        return try {
            getPackageManagerMethod.invoke(null)
        } catch (e: Throwable) {
            HookErrorStore.e("PrivilegeSettingsStore", "getPackageManager failed", e)
            null
        }
    }
}
