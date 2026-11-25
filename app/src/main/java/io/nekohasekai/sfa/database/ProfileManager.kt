package io.nekohasekai.sfa.database

import androidx.room.Room
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.bg.UpdateProfileWork
import io.nekohasekai.sfa.constant.Path
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Suppress("RedundantSuspendModifier")
object ProfileManager {
    private val callbacks = mutableListOf<() -> Unit>()

    fun registerCallback(callback: () -> Unit) {
        callbacks.add(callback)
    }

    fun unregisterCallback(callback: () -> Unit) {
        callbacks.remove(callback)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val instance by lazy {
        Application.application.getDatabasePath(Path.PROFILES_DATABASE_PATH).parentFile?.mkdirs()
        Room
            .databaseBuilder(
                Application.application,
                ProfileDatabase::class.java,
                Path.PROFILES_DATABASE_PATH,
            )
            .addMigrations(ProfileDatabase.MIGRATION_1_2, ProfileDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigrationOnDowngrade()
            .enableMultiInstanceInvalidation()
            .setQueryExecutor { GlobalScope.launch { it.run() } }
            .build()
    }

    suspend fun nextOrder(): Long = instance.profileDao().nextOrder() ?: 0

    suspend fun nextFileID(): Long = instance.profileDao().nextFileID() ?: 1

    suspend fun get(id: Long): Profile? = instance.profileDao().get(id)

    suspend fun create(profile: Profile, andSelect: Boolean = false): Profile {
        profile.id = instance.profileDao().insert(profile)
        if (andSelect) {
            Settings.selectedProfile = profile.id
        }
        onProfilesChanged()
        return profile
    }

    suspend fun update(profile: Profile): Int {
        try {
            return instance.profileDao().update(profile)
        } finally {
            onProfilesChanged()
        }
    }

    suspend fun update(profiles: List<Profile>): Int {
        try {
            return instance.profileDao().update(profiles)
        } finally {
            onProfilesChanged()
        }
    }

    suspend fun delete(profile: Profile): Int {
        try {
            return instance.profileDao().delete(profile)
        } finally {
            onProfilesChanged()
        }
    }

    suspend fun delete(profiles: List<Profile>): Int {
        try {
            return instance.profileDao().delete(profiles)
        } finally {
            onProfilesChanged()
        }
    }

    suspend fun list(): List<Profile> = instance.profileDao().list()

    private suspend fun onProfilesChanged() {
        UpdateProfileWork.reconfigureUpdater()
        for (callback in callbacks.toList()) {
            callback()
        }
    }

    fun remoteServerDao(): RemoteServer.Dao = instance.remoteServerDao()
}
