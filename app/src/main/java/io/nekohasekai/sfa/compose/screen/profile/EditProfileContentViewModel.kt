package io.nekohasekai.sfa.compose.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.compat.ProfileCodeEditor
import io.nekohasekai.sfa.compat.ProfileEditorColors
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.ktx.unwrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class EditProfileContentUiState(
    val isLoading: Boolean = false,
    val originalContent: String = "",
    val hasUnsavedChanges: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val showSaveSuccessMessage: Boolean = false,
    val errorMessage: String? = null,
    val configurationError: String? = null,
    val isCheckingConfig: Boolean = false,
    val showSearchBar: Boolean = false,
    val searchQuery: String = "",
    val searchResultCount: Int = 0,
    val currentSearchIndex: Int = 0,
    val isReadOnly: Boolean = false, // Add read-only flag
    val profileName: String = "", // Add profile name
)

class EditProfileContentViewModel(private val profileId: Long, initialIsReadOnly: Boolean = false) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            EditProfileContentUiState(
                isReadOnly = initialIsReadOnly,
            ),
        )
    val uiState: StateFlow<EditProfileContentUiState> = _uiState.asStateFlow()

    private var profile: Profile? = null
    private var editor: ProfileCodeEditor? = null
    private var configCheckJob: Job? = null

    fun attachEditor(editor: ProfileCodeEditor) {
        this.editor = editor
        editor.onTextChanged = {
            val content = editor.getText()
            _uiState.update { state ->
                state.copy(
                    canUndo = editor.canUndo(),
                    canRedo = editor.canRedo(),
                    hasUnsavedChanges = content != state.originalContent,
                )
            }
            scheduleConfigurationCheck()
        }
        editor.onSearchResultChanged = { count, current ->
            _uiState.update {
                it.copy(
                    searchResultCount = count,
                    currentSearchIndex = current,
                )
            }
        }
        editor.onCompletionWindowClosed = {
            scheduleConfigurationCheck()
        }
    }

    fun setReadOnly(isReadOnly: Boolean) {
        editor?.setReadOnly(isReadOnly)
    }

    fun applyEditorColors(colors: ProfileEditorColors) {
        editor?.applyColors(colors)
    }

    fun detachEditor() {
        editor?.release()
        editor = null
        configCheckJob?.cancel()
        configCheckJob = null
    }

    private fun scheduleConfigurationCheck() {
        configCheckJob?.cancel()

        if (editor?.isCompletionWindowShowing() != true && _uiState.value.configurationError != null) {
            _uiState.update { it.copy(configurationError = null) }
        }

        configCheckJob =
            viewModelScope.launch {
                delay(2000)
                val content =
                    withContext(Dispatchers.Main) {
                        val currentEditor = editor
                        if (currentEditor == null || currentEditor.isCompletionWindowShowing()) {
                            null
                        } else {
                            currentEditor.getText()
                        }
                    } ?: return@launch
                checkConfigurationInBackground(content)
            }
    }

    private suspend fun checkConfigurationInBackground(content: String) {
        if (content.isBlank()) {
            // Don't check empty content
            return
        }

        withContext(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isCheckingConfig = true) }

                // Check configuration
                Libbox.checkConfig(content)

                // Configuration is valid, clear any error
                _uiState.update {
                    it.copy(
                        configurationError = null,
                        isCheckingConfig = false,
                    )
                }
            } catch (e: Exception) {
                // Configuration has errors, show them
                _uiState.update {
                    it.copy(
                        configurationError = e.message ?: "Invalid configuration",
                        isCheckingConfig = false,
                    )
                }
            }
        }
    }

    fun loadConfiguration() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val loadedProfile =
                    ProfileManager.get(profileId)
                        ?: throw IllegalArgumentException("Profile not found")
                profile = loadedProfile

                // Just load the content, we already have profile metadata from Intent
                val content = File(loadedProfile.typed.path).readText()

                withContext(Dispatchers.Main) {
                    editor?.setText(content)
                    _uiState.update {
                        it.copy(
                            originalContent = content,
                            hasUnsavedChanges = false,
                            isLoading = false,
                            profileName = loadedProfile.name,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load configuration",
                    )
                }
            }
        }
    }

    fun saveConfiguration() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val currentContent =
                    withContext(Dispatchers.Main) {
                        editor?.getText() ?: ""
                    }

                // Save to file without validation
                profile?.let { p ->
                    File(p.typed.path).writeText(currentContent)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        originalContent = currentContent,
                        hasUnsavedChanges = false,
                        showSaveSuccessMessage = true,
                    )
                }

                // Hide success message after delay
                delay(2000)
                _uiState.update { it.copy(showSaveSuccessMessage = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Save failed",
                    )
                }
            }
        }
    }

    fun formatConfiguration() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val currentContent =
                    withContext(Dispatchers.Main) {
                        editor?.getText() ?: ""
                    }
                val formatted = Libbox.formatConfig(currentContent).unwrap

                if (formatted != currentContent) {
                    withContext(Dispatchers.Main) {
                        editor?.setText(formatted)
                    }
                    // Note: hasUnsavedChanges will be updated by the text change listener
                }

                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Format failed",
                    )
                }
            }
        }
    }

    fun undo() {
        editor?.let {
            it.undo()
            _uiState.update { state ->
                state.copy(
                    canUndo = it.canUndo(),
                    canRedo = it.canRedo(),
                )
            }
        }
    }

    fun redo() {
        editor?.let {
            it.redo()
            _uiState.update { state ->
                state.copy(
                    canUndo = it.canUndo(),
                    canRedo = it.canRedo(),
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSaveSuccessMessage() {
        _uiState.update { it.copy(showSaveSuccessMessage = false) }
    }

    fun dismissConfigurationError() {
        _uiState.update { it.copy(configurationError = null) }
    }

    fun toggleSearchBar() {
        editor?.search("")
        _uiState.update {
            val newShowSearchBar = !it.showSearchBar
            it.copy(
                showSearchBar = newShowSearchBar,
                searchQuery = "",
                searchResultCount = 0,
                currentSearchIndex = 0,
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        editor?.search(query)
    }

    fun findNext() {
        editor?.findNext()
    }

    fun findPrevious() {
        editor?.findPrevious()
    }

    fun insertSymbol(symbol: String) {
        editor?.insertSymbol(symbol)
    }

    fun focusEditor() {
        editor?.focus()
    }

    fun focusEditorWithCurrentSearchResult() {
        editor?.focusWithCurrentSearchResult()
    }

    fun selectAll() {
        editor?.selectAll()
    }

    fun cut() {
        editor?.cut()
    }

    fun copy() {
        editor?.copy()
    }

    fun paste() {
        if (!_uiState.value.isReadOnly) {
            editor?.paste()
        }
    }

    class Factory(
        private val profileId: Long,
        private val initialIsReadOnly: Boolean = false,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(EditProfileContentViewModel::class.java)) {
                return EditProfileContentViewModel(profileId, initialIsReadOnly) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
