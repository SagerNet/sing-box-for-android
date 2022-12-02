package io.nekohasekai.sfa.db

import androidx.room.Room
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.bg.ProxyService
import io.nekohasekai.sfa.bg.VPNService
import io.nekohasekai.sfa.constant.Path
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.SettingsKey
import io.nekohasekai.sfa.db.preference.KeyValueDatabase
import io.nekohasekai.sfa.db.preference.RoomPreferenceDataStore
import io.nekohasekai.sfa.ktx.string
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

object Settings {

    private val instance by lazy {
        Application.application.getDatabasePath(Path.SETTINGS_DATABASE_PATH).parentFile?.mkdirs()
        Room.databaseBuilder(
            Application.application, KeyValueDatabase::class.java, Path.SETTINGS_DATABASE_PATH
        ).fallbackToDestructiveMigration().setQueryExecutor { GlobalScope.launch { it.run() } }
            .build()
    }
    private val settingsStore = RoomPreferenceDataStore(instance.keyValuePairDao())

    var configurationContent by settingsStore.string(SettingsKey.CONFIGURATION_CONTENT)
    var serviceMode by settingsStore.string(SettingsKey.SERVICE_MODE) { ServiceMode.NORMAL }

    fun serviceClass(): Class<*> {
        return when (serviceMode) {
            ServiceMode.VPN -> VPNService::class.java
            else -> ProxyService::class.java
        }
    }

    fun rebuildServiceMode(): Boolean {
        var newMode = ServiceMode.NORMAL
        try {
            if (needVPNService()) {
                newMode = ServiceMode.VPN
            }
        } catch (_: JSONException) {
        }
        if (serviceMode == newMode) {
            return false
        }
        serviceMode = newMode
        return true
    }

    private fun needVPNService(): Boolean {
        val configBody = JSONObject(configurationContent)
        val inbounds = configBody.getJSONArray("inbounds")
        for (index in 0 until inbounds.length()) {
            val inbound = inbounds.getJSONObject(index)
            if (inbound.getString("type") == "tun") {
                return true
            }
        }
        return false
    }

}