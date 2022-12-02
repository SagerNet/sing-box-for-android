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

    suspend fun nextOrder(): Long {
        return instance.profileDao().nextOrder() ?: 0
    }

    suspend fun get(id: Long): Profile? {
        return instance.profileDao().get(id)
    }

    suspend fun create(profile: Profile): Profile {
        profile.id = instance.profileDao().insert(profile)
        return profile
    }

    suspend fun update(profile: Profile): Int {
        return instance.profileDao().update(profile)
    }

    suspend fun update(profiles: List<Profile>): Int {
        return instance.profileDao().update(profiles)
    }

    suspend fun delete(profile: Profile): Int {
        return instance.profileDao().delete(profile)
    }

    suspend fun delete(profiles: List<Profile>): Int {
        return instance.profileDao().delete(profiles)
    }

    suspend fun list(): List<Profile> {
        return instance.profileDao().list()
    }

}