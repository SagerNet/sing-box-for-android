package io.nekohasekai.sfa.bg

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.utils.HTTPClient
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit

class UpdateProfileWork {

    companion object {
        private const val WORK_NAME = "UpdateProfile"

        suspend fun reconfigureUpdater() {
            runCatching {
                reconfigureUpdater0()
            }.onFailure {
                Log.e("UpdateProfileWork", "reconfigureUpdater", it)
            }
        }

        private suspend fun reconfigureUpdater0() {
            WorkManager.getInstance(Application.application).cancelUniqueWork(WORK_NAME)

            val remoteProfiles = ProfileManager.list()
                .filter { it.typed.type == TypedProfile.Type.Remote && it.typed.autoUpdate }
            if (remoteProfiles.isEmpty()) return

            var minDelay =
                remoteProfiles.minByOrNull { it.typed.autoUpdateInterval }!!.typed.autoUpdateInterval.toLong()
            val now = System.currentTimeMillis() / 1000L
            val minInitDelay =
                remoteProfiles.minOf { now - (it.typed.lastUpdated.time / 1000L) - (minDelay * 60) }
            if (minDelay < 15) minDelay = 15

            WorkManager.getInstance(Application.application).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest.Builder(UpdateTask::class.java, minDelay, TimeUnit.MINUTES)
                    .apply {
                        if (minInitDelay > 0) setInitialDelay(minInitDelay, TimeUnit.SECONDS)
                        setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                    }
                    .build()
            )
        }

    }

    class UpdateTask(
        appContext: Context, params: WorkerParameters
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val remoteProfiles = ProfileManager.list()
                .filter { it.typed.type == TypedProfile.Type.Remote && it.typed.autoUpdate }
            if (remoteProfiles.isEmpty()) return Result.success()
            var success = true
            for (profile in remoteProfiles) {
                try {
                    val content = HTTPClient().use { it.getString(profile.typed.remoteURL) }
                    Libbox.checkConfig(content)
                    File(profile.typed.path).writeText(content)
                    profile.typed.lastUpdated = Date()
                    ProfileManager.update(profile)
                } catch (e: Exception) {
                    Log.e("UpdateProfileWork", "error when updating profile ${profile.name}", e)
                    success = false
                }
            }
            return if (success) {
                Result.success()
            } else {
                Result.retry()
            }
        }

    }


}