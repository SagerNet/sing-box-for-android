package io.nekohasekai.sfa.database

import androidx.room.Room
import io.nekohasekai.sfa.Application
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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

object Settings {

    private val instance by lazy {
        Application.application.getDatabasePath(Path.SETTINGS_DATABASE_PATH).parentFile?.mkdirs()
        Room.databaseBuilder(
            Application.application,
            KeyValueDatabase::class.java,
            Path.SETTINGS_DATABASE_PATH
        ).allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .setQueryExecutor { GlobalScope.launch { it.run() } }
            .build()
    }
    val dataStore = RoomPreferenceDataStore(instance.keyValuePairDao())
    var selectedProfile by dataStore.long(SettingsKey.SELECTED_PROFILE) { -1L }
    var serviceMode by dataStore.string(SettingsKey.SERVICE_MODE) { ServiceMode.NORMAL }
    var startedByUser by dataStore.boolean(SettingsKey.STARTED_BY_USER)

    const val ANALYSIS_UNKNOWN = -1
    const val ANALYSIS_ALLOWED = 0
    const val ANALYSIS_DISALLOWED = 1

    var analyticsAllowed by dataStore.int(SettingsKey.ANALYTICS_ALLOWED) { ANALYSIS_UNKNOWN }
    var checkUpdateEnabled by dataStore.boolean(SettingsKey.CHECK_UPDATE_ENABLED) { true }

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
        val profile = Profiles.get(selectedProfile) ?: return false
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