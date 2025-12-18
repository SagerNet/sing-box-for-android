package io.nekohasekai.sfa.update

import androidx.compose.runtime.mutableStateOf
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.database.Settings
import java.io.File

object UpdateState {
    val hasUpdate = mutableStateOf(false)
    val updateInfo = mutableStateOf<UpdateInfo?>(null)
    val isChecking = mutableStateOf(false)

    val isDownloading = mutableStateOf(false)
    val downloadError = mutableStateOf<String?>(null)

    val cachedApkFile = mutableStateOf<File?>(null)

    sealed class InstallStatus {
        data object Idle : InstallStatus()
        data object Installing : InstallStatus()
        data object Success : InstallStatus()
        data class Failed(val error: String) : InstallStatus()
    }

    val installStatus = mutableStateOf<InstallStatus>(InstallStatus.Idle)

    fun setUpdate(info: UpdateInfo?) {
        updateInfo.value = info
        hasUpdate.value = info != null
        saveToCache(info)
    }

    fun setInstallStatus(status: InstallStatus) {
        installStatus.value = status
    }

    fun clear() {
        hasUpdate.value = false
        updateInfo.value = null
        isDownloading.value = false
        downloadError.value = null
        installStatus.value = InstallStatus.Idle
        cachedApkFile.value = null
        clearCache()
    }

    fun resetDownload() {
        isDownloading.value = false
        downloadError.value = null
    }

    fun loadFromCache() {
        val json = Settings.cachedUpdateInfo
        if (json.isBlank()) return

        val info = UpdateInfo.fromJson(json) ?: return
        if (info.versionCode <= BuildConfig.VERSION_CODE) {
            clearCache()
            return
        }

        updateInfo.value = info
        hasUpdate.value = true

        val apkPath = Settings.cachedApkPath
        if (apkPath.isNotBlank()) {
            val apkFile = File(apkPath)
            if (apkFile.exists() && apkFile.length() > 0) {
                cachedApkFile.value = apkFile
            } else {
                Settings.cachedApkPath = ""
            }
        }
    }

    private fun saveToCache(info: UpdateInfo?) {
        Settings.cachedUpdateInfo = info?.toJson() ?: ""
    }

    fun saveApkPath(file: File) {
        Settings.cachedApkPath = file.absolutePath
        cachedApkFile.value = file
    }

    private fun clearCache() {
        Settings.cachedUpdateInfo = ""
        Settings.cachedApkPath = ""
        Settings.lastShownUpdateVersion = 0
    }
}
