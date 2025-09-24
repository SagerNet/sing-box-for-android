package io.nekohasekai.sfa.compose.screen.configuration

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.ProfileContent
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Date

class ProfileImportHandler(private val context: Context) {
    sealed class ImportResult {
        data class Success(val profile: Profile) : ImportResult()

        data class Error(val message: String) : ImportResult()
    }

    sealed class QRCodeParseResult {
        data class RemoteProfile(val name: String, val host: String, val url: String) :
            QRCodeParseResult()

        data class LocalProfile(val name: String) : QRCodeParseResult()

        data class Error(val message: String) : QRCodeParseResult()
    }

    suspend fun importFromUri(uri: Uri): ImportResult =
        withContext(Dispatchers.IO) {
            try {
                val data =
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@withContext ImportResult.Error(context.getString(R.string.error_empty_file))

                // Get the filename from the URI
                val filename = getFileNameFromUri(uri)

                // Try to detect if it's a JSON configuration file
                val dataString = String(data)
                if (isJsonConfiguration(dataString)) {
                    // It's a JSON configuration, import it directly as a local profile
                    return@withContext importJsonConfiguration(dataString, filename)
                }

                // Try to decode as ProfileContent (the old way)
                val content =
                    try {
                        Libbox.decodeProfileContent(data)
                    } catch (e: Exception) {
                        // If it fails, try one more time as JSON
                        if (dataString.trimStart().startsWith("{") || dataString.trimStart().startsWith("[")) {
                            return@withContext importJsonConfiguration(dataString, filename)
                        }
                        return@withContext ImportResult.Error(
                            context.getString(R.string.error_decode_profile, e.message),
                        )
                    }

                importProfile(content)
            } catch (e: Exception) {
                ImportResult.Error(e.message ?: "Unknown error")
            }
        }

    suspend fun parseQRCode(data: String): QRCodeParseResult =
        withContext(Dispatchers.IO) {
            try {
                // Check if it's a sing-box remote profile import link
                if (data.startsWith("sing-box://import-remote-profile")) {
                    try {
                        val profileInfo = Libbox.parseRemoteProfileImportLink(data)
                        return@withContext QRCodeParseResult.RemoteProfile(
                            name = profileInfo.name,
                            host = profileInfo.host,
                            url = profileInfo.url,
                        )
                    } catch (e: Exception) {
                        return@withContext QRCodeParseResult.Error(
                            context.getString(R.string.error_decode_profile, e.message),
                        )
                    }
                }

                // Check if it's a direct URL
                if (data.startsWith("http://") || data.startsWith("https://")) {
                    val profileName = extractProfileNameFromUrl(data)
                    return@withContext QRCodeParseResult.RemoteProfile(
                        name = profileName,
                        host = extractHostFromUrl(data),
                        url = data,
                    )
                }

                // Try to decode as profile content
                val content =
                    try {
                        Libbox.decodeProfileContent(data.toByteArray())
                    } catch (e: Exception) {
                        return@withContext QRCodeParseResult.Error(
                            context.getString(R.string.error_decode_profile, e.message),
                        )
                    }

                return@withContext QRCodeParseResult.LocalProfile(name = content.name)
            } catch (e: Exception) {
                QRCodeParseResult.Error(e.message ?: "Unknown error")
            }
        }

    suspend fun importFromQRCode(data: String): ImportResult =
        withContext(Dispatchers.IO) {
            try {
                // Check if it's a sing-box remote profile import link
                if (data.startsWith("sing-box://import-remote-profile")) {
                    try {
                        val profileInfo = Libbox.parseRemoteProfileImportLink(data)
                        return@withContext importRemoteProfile(profileInfo.name, profileInfo.url)
                    } catch (e: Exception) {
                        return@withContext ImportResult.Error(
                            context.getString(R.string.error_decode_profile, e.message),
                        )
                    }
                }

                // Check if it's a URL or direct profile content
                if (data.startsWith("http://") || data.startsWith("https://")) {
                    // Handle remote profile URL
                    val profileName = extractProfileNameFromUrl(data)
                    importRemoteProfile(profileName, data)
                } else {
                    // Try to decode as profile content
                    val content =
                        try {
                            Libbox.decodeProfileContent(data.toByteArray())
                        } catch (e: Exception) {
                            return@withContext ImportResult.Error(
                                context.getString(R.string.error_decode_profile, e.message),
                            )
                        }
                    importProfile(content)
                }
            } catch (e: Exception) {
                ImportResult.Error(e.message ?: "Unknown error")
            }
        }

    private suspend fun importProfile(content: ProfileContent): ImportResult {
        val typedProfile = TypedProfile()
        val profile = Profile(name = content.name, typed = typedProfile)
        profile.userOrder = ProfileManager.nextOrder()

        when (content.type) {
            Libbox.ProfileTypeLocal -> {
                typedProfile.type = TypedProfile.Type.Local
            }
            Libbox.ProfileTypeiCloud -> {
                return ImportResult.Error(context.getString(R.string.icloud_profile_unsupported))
            }
            Libbox.ProfileTypeRemote -> {
                typedProfile.type = TypedProfile.Type.Remote
                typedProfile.remoteURL = content.remotePath
                typedProfile.autoUpdate = content.autoUpdate
                typedProfile.autoUpdateInterval = content.autoUpdateInterval
                typedProfile.lastUpdated = Date(content.lastUpdated)
            }
        }

        // Save config file
        val configDirectory = File(context.filesDir, "configs").also { it.mkdirs() }
        val configFile = File(configDirectory, "${profile.userOrder}.json")
        configFile.writeText(content.config)
        typedProfile.path = configFile.path

        // Create profile in database
        ProfileManager.create(profile)

        // If no profile is currently selected, select this one
        if (Settings.selectedProfile == -1L) {
            Settings.selectedProfile = profile.id
        }

        return ImportResult.Success(profile)
    }

    private suspend fun importRemoteProfile(
        name: String,
        url: String,
    ): ImportResult {
        val typedProfile =
            TypedProfile().apply {
                type = TypedProfile.Type.Remote
                remoteURL = url
                autoUpdate = true
                autoUpdateInterval = 60
                lastUpdated = Date()
            }

        val profile =
            Profile(name = name, typed = typedProfile).apply {
                userOrder = ProfileManager.nextOrder()
            }

        // Create empty config file for remote profile
        val configDirectory = File(context.filesDir, "configs").also { it.mkdirs() }
        val configFile = File(configDirectory, "${profile.userOrder}.json")
        configFile.writeText("{}")
        typedProfile.path = configFile.path

        ProfileManager.create(profile)

        // If no profile is currently selected, select this one
        if (Settings.selectedProfile == -1L) {
            Settings.selectedProfile = profile.id
        }

        return ImportResult.Success(profile)
    }

    private fun extractProfileNameFromUrl(url: String): String {
        // Extract name from URL or use default
        return url.substringAfterLast("/")
            .substringBeforeLast(".")
            .takeIf { it.isNotEmpty() }
            ?: "Remote Profile"
    }

    private fun extractHostFromUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
            uri.host ?: url
        } catch (e: Exception) {
            url
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var filename = "Imported Profile"

        // Try to get filename from content resolver
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                filename = cursor.getString(nameIndex)
                    ?.substringBeforeLast(".") // Remove extension
                    ?.takeIf { it.isNotEmpty() }
                    ?: filename
            }
        }

        // Fallback to getting from URI path
        if (filename == "Imported Profile") {
            uri.lastPathSegment?.let { segment ->
                filename = segment
                    .substringBeforeLast(".")
                    .takeIf { it.isNotEmpty() }
                    ?: filename
            }
        }

        return filename
    }

    private fun isJsonConfiguration(content: String): Boolean {
        val trimmed = content.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return false
        }

        return try {
            // Try to parse as JSON and check for sing-box configuration fields
            val json = JSONObject(content)
            // Check for common sing-box configuration fields
            json.has("inbounds") ||
                json.has("outbounds") ||
                json.has("route") ||
                json.has("dns") ||
                json.has("experimental")
        } catch (e: Exception) {
            // If it's an array, it might still be valid
            trimmed.startsWith("[") && trimmed.endsWith("]")
        }
    }

    private suspend fun importJsonConfiguration(
        jsonContent: String,
        profileName: String,
    ): ImportResult {
        return try {
            // Validate the JSON configuration using sing-box
            try {
                // Try to check the configuration
                Libbox.checkConfig(jsonContent)
            } catch (e: Exception) {
                // Configuration validation failed
                return ImportResult.Error(
                    context.getString(R.string.error_invalid_configuration, e.message),
                )
            }

            // Create a local profile with the JSON configuration
            val typedProfile =
                TypedProfile().apply {
                    type = TypedProfile.Type.Local
                }

            val profile =
                Profile(
                    name = profileName.ifEmpty { "Imported Profile" },
                    typed = typedProfile,
                ).apply {
                    userOrder = ProfileManager.nextOrder()
                }

            // Save the configuration file
            val configDirectory = File(context.filesDir, "configs").also { it.mkdirs() }
            val configFile = File(configDirectory, "${profile.userOrder}.json")
            configFile.writeText(jsonContent)
            typedProfile.path = configFile.path

            // Create profile in database
            ProfileManager.create(profile)

            ImportResult.Success(profile)
        } catch (e: Exception) {
            ImportResult.Error(e.message ?: "Unknown error importing JSON configuration")
        }
    }
}
