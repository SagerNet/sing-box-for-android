package io.nekohasekai.sfa.database

import androidx.room.Room
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.constant.Path
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Suppress("RedundantSuspendModifier")
object Profiles {

    private val instance by lazy {
        Application.application.getDatabasePath(Path.PROFILES_DATABASE_PATH).parentFile?.mkdirs()
        Room.databaseBuilder(
            Application.application, ProfileDatabase::class.java, Path.PROFILES_DATABASE_PATH
        ).fallbackToDestructiveMigration().setQueryExecutor { GlobalScope.launch { it.run() } }
            .build()
    }

    suspend fun listProfiles(): List<Profile> {
        return instance.profileDao().list()
    }

}