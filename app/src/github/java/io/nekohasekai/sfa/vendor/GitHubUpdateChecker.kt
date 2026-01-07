package io.nekohasekai.sfa.vendor

import android.os.Build
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.ktx.unwrap
import io.nekohasekai.sfa.update.UpdateCheckException
import io.nekohasekai.sfa.update.UpdateInfo
import io.nekohasekai.sfa.update.UpdateTrack
import io.nekohasekai.sfa.utils.HTTPClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable

class GitHubUpdateChecker : Closeable {
    companion object {
        private const val RELEASES_URL = "https://api.github.com/repos/SagerNet/sing-box/releases"
        private const val METADATA_FILENAME = "SFA-version-metadata.json"
    }

    private val client = Libbox.newHTTPClient().apply {
        modernTLS()
        keepAlive()
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun checkUpdate(track: UpdateTrack): UpdateInfo? {
        return getLatestUpdate(track, checkVersion = true)
    }

    fun forceGetLatestUpdate(track: UpdateTrack): UpdateInfo? {
        return getLatestUpdate(track, checkVersion = false)
    }

    private fun getLatestUpdate(track: UpdateTrack, checkVersion: Boolean): UpdateInfo? {
        val includePrerelease = track == UpdateTrack.BETA
        val release = getLatestRelease(includePrerelease) ?: return null

        if (!release.assets.any { it.name == METADATA_FILENAME }) {
            throw UpdateCheckException.TrackNotSupported()
        }

        val metadata = downloadMetadata(release)!!

        if (checkVersion && metadata.versionCode <= BuildConfig.VERSION_CODE) {
            return null
        }

        val isLegacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
        val apkAsset = release.assets.find { asset ->
            asset.name.endsWith(".apk") &&
                !asset.name.contains("play") &&
                asset.name.contains("legacy-android-5") == isLegacy
        }

        return UpdateInfo(
            versionCode = metadata.versionCode,
            versionName = metadata.versionName,
            downloadUrl = apkAsset?.browserDownloadUrl ?: release.htmlUrl,
            releaseUrl = release.htmlUrl,
            releaseNotes = release.body,
            isPrerelease = release.prerelease,
            fileSize = apkAsset?.size ?: 0,
        )
    }

    private fun getLatestRelease(includePrerelease: Boolean): GitHubRelease? {
        val request = client.newRequest()
        request.setURL(RELEASES_URL)
        request.setHeader("Accept", "application/vnd.github.v3+json")
        request.setUserAgent(HTTPClient.userAgent)

        val response = request.execute()
        val content = response.content.unwrap

        val releases = json.decodeFromString<List<GitHubRelease>>(content)

        return if (includePrerelease) {
            releases.firstOrNull()
        } else {
            releases.firstOrNull { !it.prerelease && !it.draft }
        }
    }

    private fun downloadMetadata(release: GitHubRelease): VersionMetadata? {
        val metadataAsset = release.assets.find { it.name == METADATA_FILENAME }
            ?: return null

        val request = client.newRequest()
        request.setURL(metadataAsset.browserDownloadUrl)
        request.setUserAgent(HTTPClient.userAgent)

        val response = request.execute()
        val content = response.content.unwrap

        return json.decodeFromString<VersionMetadata>(content)
    }

    override fun close() {
        client.close()
    }

    @Serializable
    data class GitHubRelease(
        @SerialName("tag_name") val tagName: String = "",
        val name: String = "",
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        @SerialName("html_url") val htmlUrl: String = "",
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    data class GitHubAsset(
        val name: String = "",
        @SerialName("browser_download_url") val browserDownloadUrl: String = "",
        val size: Long = 0,
    )

    @Serializable
    data class VersionMetadata(
        @SerialName("version_code") val versionCode: Int = 0,
        @SerialName("version_name") val versionName: String = "",
    )
}
