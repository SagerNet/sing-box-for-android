package io.nekohasekai.sfa.database

import android.os.Build
import androidx.room.Room
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.bg.ProxyService
import io.nekohasekai.sfa.bg.VPNService
import io.nekohasekai.sfa.constant.Path
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.SettingsKey
import io.nekohasekai.sfa.database.preference.KeyValueDatabase
import io.nekohasekai.sfa.database.preference.RoomPreferenceDataStore
import io.nekohasekai.sfa.ktx.boolean
import io.nekohasekai.sfa.ktx.int
import io.nekohasekai.sfa.ktx.long
import io.nekohasekai.sfa.ktx.string
import io.nekohasekai.sfa.ktx.stringSet
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

object Settings {
    @OptIn(DelicateCoroutinesApi::class)
    private val instance by lazy {
        Application.application.getDatabasePath(Path.SETTINGS_DATABASE_PATH).parentFile?.mkdirs()
        Room.databaseBuilder(
            Application.application,
            KeyValueDatabase::class.java,
            Path.SETTINGS_DATABASE_PATH,
        ).allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .enableMultiInstanceInvalidation()
            .setQueryExecutor { GlobalScope.launch { it.run() } }
            .build()
    }
    val dataStore = RoomPreferenceDataStore(instance.keyValuePairDao())
    var selectedProfile by dataStore.long(SettingsKey.SELECTED_PROFILE) { -1L }
    var serviceMode by dataStore.string(SettingsKey.SERVICE_MODE) { ServiceMode.NORMAL }
    var startedByUser by dataStore.boolean(SettingsKey.STARTED_BY_USER)

    var checkUpdateEnabled by dataStore.boolean(SettingsKey.CHECK_UPDATE_ENABLED) { false }
    var updateCheckPrompted by dataStore.boolean(SettingsKey.UPDATE_CHECK_PROMPTED) { false }
    var updateTrack by dataStore.string(SettingsKey.UPDATE_TRACK) {
        val versionName = BuildConfig.VERSION_NAME.lowercase()
        if (versionName.contains("-alpha") ||
            versionName.contains("-beta") ||
            versionName.contains("-rc")
        ) {
            "beta"
        } else {
            "stable"
        }
    }
    var silentInstallEnabled by dataStore.boolean(SettingsKey.SILENT_INSTALL_ENABLED) { false }
    var silentInstallMethod by dataStore.string(SettingsKey.SILENT_INSTALL_METHOD) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "PACKAGE_INSTALLER"
        } else {
            "SHIZUKU"
        }
    }
    var autoUpdateEnabled by dataStore.boolean(SettingsKey.AUTO_UPDATE_ENABLED) { false }
    var disableMemoryLimit by dataStore.boolean(SettingsKey.DISABLE_MEMORY_LIMIT)
    var dynamicNotification by dataStore.boolean(SettingsKey.DYNAMIC_NOTIFICATION) { true }
    var disableDeprecatedWarnings by dataStore.boolean(SettingsKey.DISABLE_DEPRECATED_WARNINGS) { false }

    const val PER_APP_PROXY_DISABLED = 0
    const val PER_APP_PROXY_EXCLUDE = 1
    const val PER_APP_PROXY_INCLUDE = 2

    var autoRedirect by dataStore.boolean(SettingsKey.AUTO_REDIRECT) { false }
    var perAppProxyEnabled by dataStore.boolean(SettingsKey.PER_APP_PROXY_ENABLED) { false }
    var perAppProxyMode by dataStore.int(SettingsKey.PER_APP_PROXY_MODE) { PER_APP_PROXY_EXCLUDE }
    var perAppProxyList by dataStore.stringSet(SettingsKey.PER_APP_PROXY_LIST) { emptySet() }
    var perAppProxyManagedMode by dataStore.boolean(SettingsKey.PER_APP_PROXY_MANAGED_MODE) { false }
    var perAppProxyManagedList by dataStore.stringSet(SettingsKey.PER_APP_PROXY_MANAGED_LIST) { emptySet() }

    const val PACKAGE_QUERY_MODE_SHIZUKU = "SHIZUKU"
    const val PACKAGE_QUERY_MODE_ROOT = "ROOT"
    var perAppProxyPackageQueryMode by dataStore.string(SettingsKey.PER_APP_PROXY_PACKAGE_QUERY_MODE) { PACKAGE_QUERY_MODE_SHIZUKU }

    fun getEffectivePerAppProxyList(): Set<String> {
        return if (perAppProxyManagedMode) {
            perAppProxyList union perAppProxyManagedList
        } else {
            perAppProxyList
        }
    }

    var systemProxyEnabled by dataStore.boolean(SettingsKey.SYSTEM_PROXY_ENABLED) { true }

    var dashboardItemOrder by dataStore.string(SettingsKey.DASHBOARD_ITEM_ORDER) { "" }
    var dashboardDisabledItems by dataStore.stringSet(SettingsKey.DASHBOARD_DISABLED_ITEMS) { emptySet() }

    var cachedUpdateInfo by dataStore.string(SettingsKey.CACHED_UPDATE_INFO) { "" }
    var cachedApkPath by dataStore.string(SettingsKey.CACHED_APK_PATH) { "" }
    var lastShownUpdateVersion by dataStore.int(SettingsKey.LAST_SHOWN_UPDATE_VERSION) { 0 }

    fun serviceClass(): Class<*> {
        return when (serviceMode) {
            ServiceMode.VPN -> VPNService::class.java
            else -> ProxyService::class.java
        }
    }

    suspend fun rebuildServiceMode(): Boolean {
        var newMode = ServiceMode.NORMAL
        try {
            if (needVPNService()) {
                newMode = ServiceMode.VPN
            }
        } catch (_: Exception) {
        }
        if (serviceMode == newMode) {
            return false
        }
        serviceMode = newMode
        return true
    }

    private suspend fun needVPNService(): Boolean {
        val selectedProfileId = selectedProfile
        if (selectedProfileId == -1L) return false
        val profile = ProfileManager.get(selectedProfile) ?: return false
        val content = JSONObject(File(profile.typed.path).readText())
        val inbounds = content.getJSONArray("inbounds")
        for (index in 0 until inbounds.length()) {
            val inbound = inbounds.getJSONObject(index)
            if (inbound.getString("type") == "tun") {
                return true
            }
        }
        return false
    }
}
