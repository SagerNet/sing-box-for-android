package io.nekohasekai.sfa.db

import androidx.room.Room
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.constant.Path
import io.nekohasekai.sfa.constant.SettingsKey
import io.nekohasekai.sfa.db.preference.KeyValueDatabase
import io.nekohasekai.sfa.db.preference.RoomPreferenceDataStore
import io.nekohasekai.sfa.ktx.string
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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

}