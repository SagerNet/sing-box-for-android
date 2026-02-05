package io.nekohasekai.sfa.compose.screen.profile

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.UpdateProfileWork
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.utils.HTTPClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EditProfileUiState(
    val profile: Profile? = null,
    val name: String = "",
    val icon: String? = null,
    val profileType: TypedProfile.Type? = null,
    val remoteUrl: String = "",
    val autoUpdate: Boolean = false,
    val autoUpdateInterval: Int = 60,
    val lastUpdated: Date? = null,
    // Original values for change detection
    val originalName: String = "",
    val originalIcon: String? = null,
    val originalRemoteUrl: String = "",
    val originalAutoUpdate: Boolean = false,
    val originalAutoUpdateInterval: Int = 60,
    // State flags
    val hasChanges: Boolean = false,
    val isLoading: Boolean = true,
    val isUpdating: Boolean = false,
    val showUpdateSuccess: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val autoUpdateIntervalError: String? = null,
    val showIconDialog: Boolean = false,
)

class EditProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    // Store the content to export when user selects a file location
    var pendingExportContent: String? = null
    var pendingExportFileName: String? = null

    fun loadProfile(profileId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = ProfileManager.get(profileId)
                if (profile == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Profile not found",
                        )
                    }
                    return@launch
                }

                val typedProfile = profile.typed
                _uiState.update {
                    it.copy(
                        profile = profile,
                        name = profile.name,
                        originalName = profile.name,
                        icon = profile.icon,
                        originalIcon = profile.icon,
                        profileType = typedProfile.type,
                        remoteUrl = typedProfile.remoteURL,
                        originalRemoteUrl = typedProfile.remoteURL,
                        autoUpdate = typedProfile.autoUpdate,
                        originalAutoUpdate = typedProfile.autoUpdate,
                        autoUpdateInterval = typedProfile.autoUpdateInterval,
                        originalAutoUpdateInterval = typedProfile.autoUpdateInterval,
                        lastUpdated = typedProfile.lastUpdated,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message,
                    )
                }
            }
        }
    }

    fun updateName(name: String) {
        _uiState.update { state ->
            state.copy(
                name = name,
                hasChanges =
                checkHasChanges(
                    state.copy(name = name),
                ),
            )
        }
    }

    fun updateIcon(icon: String?) {
        _uiState.update { state ->
            state.copy(
                icon = icon,
                hasChanges =
                checkHasChanges(
                    state.copy(icon = icon),
                ),
            )
        }
    }

    fun showIconDialog() {
        _uiState.update { it.copy(showIconDialog = true) }
    }

    fun hideIconDialog() {
        _uiState.update { it.copy(showIconDialog = false) }
    }

    fun updateRemoteUrl(url: String) {
        _uiState.update { state ->
            state.copy(
                remoteUrl = url,
                hasChanges =
                checkHasChanges(
                    state.copy(remoteUrl = url),
                ),
            )
        }
    }

    fun updateAutoUpdate(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                autoUpdate = enabled,
                hasChanges =
                checkHasChanges(
                    state.copy(autoUpdate = enabled),
                ),
            )
        }
    }

    fun updateAutoUpdateInterval(interval: String) {
        val intValue = interval.toIntOrNull() ?: 60
        val error =
            when {
                interval.isBlank() -> getApplication<Application>().getString(R.string.profile_input_required)
                intValue < 15 -> getApplication<Application>().getString(R.string.profile_auto_update_interval_minimum_hint)
                else -> null
            }

        _uiState.update { state ->
            state.copy(
                autoUpdateInterval = intValue,
                autoUpdateIntervalError = error,
                hasChanges =
                if (error == null) {
                    checkHasChanges(state.copy(autoUpdateInterval = intValue))
                } else {
                    state.hasChanges
                },
            )
        }
    }

    private fun checkHasChanges(state: EditProfileUiState): Boolean = state.name != state.originalName ||
        state.icon != state.originalIcon ||
        state.remoteUrl != state.originalRemoteUrl ||
        state.autoUpdate != state.originalAutoUpdate ||
        state.autoUpdateInterval != state.originalAutoUpdateInterval

    fun saveChanges() {
        val state = _uiState.value
        val profile = state.profile ?: return

        if (state.autoUpdateIntervalError != null) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSaving = true) }

            try {
                // Update profile object
                profile.name = state.name
                profile.icon = state.icon
                profile.typed.remoteURL = state.remoteUrl

                // Handle auto-update changes
                val autoUpdateChanged = state.autoUpdate != state.originalAutoUpdate
                profile.typed.autoUpdate = state.autoUpdate
                profile.typed.autoUpdateInterval = state.autoUpdateInterval

                // Save to database
                ProfileManager.update(profile)

                // Reconfigure updater if auto-update was enabled
                if (autoUpdateChanged && state.autoUpdate) {
                    UpdateProfileWork.reconfigureUpdater()
                }

                // Update UI state with new original values
                _uiState.update {
                    it.copy(
                        originalName = state.name,
                        originalIcon = state.icon,
                        originalRemoteUrl = state.remoteUrl,
                        originalAutoUpdate = state.autoUpdate,
                        originalAutoUpdateInterval = state.autoUpdateInterval,
                        hasChanges = false,
                        isSaving = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = e.message,
                    )
                }
            }
        }
    }

    fun updateRemoteProfile() {
        val state = _uiState.value
        val profile = state.profile ?: return

        if (profile.typed.type != TypedProfile.Type.Remote) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isUpdating = true) }

            try {
                var selectedProfileUpdated = false

                // Fetch remote config
                val content = HTTPClient().use { it.getString(profile.typed.remoteURL) }
                Libbox.checkConfig(content)

                // Check if content changed
                val file = File(profile.typed.path)
                if (!file.exists() || file.readText() != content) {
                    file.writeText(content)
                    if (profile.id == Settings.selectedProfile) {
                        selectedProfileUpdated = true
                    }
                }

                // Update last updated time
                profile.typed.lastUpdated = Date()
                ProfileManager.update(profile)

                // Update UI state with success indicator
                _uiState.update {
                    it.copy(
                        lastUpdated = profile.typed.lastUpdated,
                        isUpdating = false,
                        showUpdateSuccess = true,
                    )
                }

                // Reload service if needed
                if (selectedProfileUpdated) {
                    try {
                        Libbox.newStandaloneCommandClient().serviceReload()
                    } catch (e: Exception) {
                        // Service reload errors are not critical
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUpdating = false,
                        errorMessage = e.message,
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearUpdateSuccess() {
        _uiState.update { it.copy(showUpdateSuccess = false) }
    }

    fun prepareExport(context: Context): String? {
        val state = _uiState.value
        val profile = state.profile ?: return null

        return try {
            // Read the configuration file
            val configFile = File(profile.typed.path)
            if (!configFile.exists()) {
                Toast.makeText(context, "Configuration file not found", Toast.LENGTH_SHORT).show()
                return null
            }

            val content = configFile.readText()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "${profile.name.replace(" ", "_")}_$timestamp.json"

            // Store content for later when user picks location
            pendingExportContent = content
            pendingExportFileName = fileName

            fileName
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(
                    io.nekohasekai.sfa.R.string.failed_read_configuration,
                    e.message,
                ),
                Toast.LENGTH_SHORT,
            ).show()
            null
        }
    }

    fun saveExportToUri(context: Context, uri: Uri) {
        val content = pendingExportContent ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Configuration exported successfully",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                // Clear pending export data
                pendingExportContent = null
                pendingExportFileName = null
            }
        }
    }
}
