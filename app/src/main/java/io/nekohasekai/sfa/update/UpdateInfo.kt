package io.nekohasekai.sfa.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val releaseNotes: String?,
    val isPrerelease: Boolean,
    val fileSize: Long = 0,
) {
    fun toJson(): String = Json.encodeToString(this)

    companion object {
        fun fromJson(json: String): UpdateInfo? = runCatching {
            Json.decodeFromString<UpdateInfo>(json)
        }.getOrNull()
    }
}
