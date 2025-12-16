package io.nekohasekai.sfa.update

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val releaseNotes: String?,
    val isPrerelease: Boolean,
)
