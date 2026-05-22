package io.nekohasekai.sfa.compose.screen.profile

import android.text.TextWatcher
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.Libbox
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
    private var editor: ManualScrollTextProcessor? = null
    private var textWatcher: TextWatcher? = null
    private var configCheckJob: Job? = null

    private val readOnlyKeyListener = android.view.View.OnKeyListener { _, _, _ -> true }

    private val readOnlySelectionCallback =
        object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = true

            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                menu?.let { m ->
                    m.removeItem(android.R.id.cut)
                    m.removeItem(android.R.id.paste)
                    m.removeItem(android.R.id.pasteAsPlainText)
                    m.removeItem(android.R.id.replaceText)
                    m.removeItem(android.R.id.undo)
                    m.removeItem(android.R.id.redo)
                    m.removeItem(android.R.id.autofill)
                    m.removeItem(android.R.id.textAssist)
                }
                return true
            }

            override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean = false

            override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
        }

    fun attachEditor(textProcessor: ManualScrollTextProcessor) {
        editor = textProcessor
        textProcessor.resumeAutoScroll()

        textProcessor.isEnabled = true
        textProcessor.isFocusable = true
        textProcessor.isFocusableInTouchMode = true
        textProcessor.setTextIsSelectable(true)
        textProcessor.setSingleLine(false)
        textProcessor.maxLines = Integer.MAX_VALUE
        textProcessor.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        textProcessor.isCursorVisible = true
        textProcessor.isLongClickable = true

        textWatcher = textProcessor.addTextChangedListener { editable ->
            val length = editable?.length ?: 0
            val original = _uiState.value.originalContent
            val hasUnsavedChanges = when {
                length != original.length -> true
                editable == null -> original.isNotEmpty()
                else -> editable.toString() != original
            }
            _uiState.update { state ->
                state.copy(
                    canUndo = textProcessor.canUndo(),
                    canRedo = textProcessor.canRedo(),
                    hasUnsavedChanges = hasUnsavedChanges,
                )
            }
            scheduleConfigurationCheck()
        }
    }

    fun setReadOnly(isReadOnly: Boolean) {
        val textProcessor = editor ?: return
        if (isReadOnly) {
            textProcessor.setOnKeyListener(readOnlyKeyListener)
            textProcessor.customSelectionActionModeCallback = readOnlySelectionCallback
        } else {
            textProcessor.setOnKeyListener(null)
            textProcessor.customSelectionActionModeCallback = null
        }
    }

    fun detachEditor() {
        val textProcessor = editor ?: return
        textWatcher?.let { textProcessor.removeTextChangedListener(it) }
        textWatcher = null
        editor = null
        configCheckJob?.cancel()
        configCheckJob = null
    }

    private fun scheduleConfigurationCheck() {
        configCheckJob?.cancel()

        if (_uiState.value.configurationError != null) {
            _uiState.update { it.copy(configurationError = null) }
        }

        configCheckJob =
            viewModelScope.launch {
                delay(2000)
                val content =
                    withContext(Dispatchers.Main) {
                        editor?.text?.toString().orEmpty()
                    }
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
                    editor?.let {
                        it.resumeAutoScroll()
                        it.setTextContent(content)
                    }
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
                        editor?.text?.toString() ?: ""
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
                        editor?.text?.toString() ?: ""
                    }
                val formatted = Libbox.formatConfig(currentContent).unwrap

                if (formatted != currentContent) {
                    withContext(Dispatchers.Main) {
                        editor?.let {
                            it.resumeAutoScroll()
                            it.setTextContent(formatted)
                        }
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
            if (it.canUndo()) {
                it.resumeAutoScroll()
                it.undo()
                _uiState.update { state ->
                    state.copy(
                        canUndo = it.canUndo(),
                        canRedo = it.canRedo(),
                    )
                }
            }
        }
    }

    fun redo() {
        editor?.let {
            if (it.canRedo()) {
                it.resumeAutoScroll()
                it.redo()
                _uiState.update { state ->
                    state.copy(
                        canUndo = it.canUndo(),
                        canRedo = it.canRedo(),
                    )
                }
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
        if (query.isNotEmpty()) {
            performSearch(query)
        } else {
            _uiState.update {
                it.copy(
                    searchResultCount = 0,
                    currentSearchIndex = 0,
                )
            }
        }
    }

    private fun performSearch(query: String) {
        editor?.let { textProcessor ->
            val text = textProcessor.text?.toString() ?: ""
            if (text.isEmpty() || query.isEmpty()) {
                _uiState.update {
                    it.copy(
                        searchResultCount = 0,
                        currentSearchIndex = 0,
                    )
                }
                return
            }

            val matches = mutableListOf<Int>()
            var index = text.indexOf(query, ignoreCase = true)
            while (index != -1) {
                matches.add(index)
                index = text.indexOf(query, index + 1, ignoreCase = true)
            }

            _uiState.update {
                it.copy(
                    searchResultCount = matches.size,
                    currentSearchIndex = if (matches.isNotEmpty()) 1 else 0,
                )
            }

            // Highlight first match
            if (matches.isNotEmpty()) {
                val firstMatch = matches[0]
                textProcessor.resumeAutoScroll()
                textProcessor.setSelection(firstMatch, firstMatch + query.length)
            }
        }
    }

    fun findNext() {
        val state = _uiState.value
        if (state.searchResultCount == 0 || state.searchQuery.isEmpty()) return

        editor?.let { textProcessor ->
            val text = textProcessor.text?.toString() ?: ""
            val currentPosition = textProcessor.selectionEnd

            var nextIndex = text.indexOf(state.searchQuery, currentPosition, ignoreCase = true)
            if (nextIndex == -1) {
                // Wrap around to beginning
                nextIndex = text.indexOf(state.searchQuery, 0, ignoreCase = true)
            }

            if (nextIndex != -1) {
                textProcessor.resumeAutoScroll()
                textProcessor.setSelection(nextIndex, nextIndex + state.searchQuery.length)

                // Update current index
                val matches = mutableListOf<Int>()
                var index = text.indexOf(state.searchQuery, ignoreCase = true)
                var currentMatchIndex = 0
                var counter = 0
                while (index != -1) {
                    if (index == nextIndex) {
                        currentMatchIndex = counter + 1
                    }
                    matches.add(index)
                    counter++
                    index = text.indexOf(state.searchQuery, index + 1, ignoreCase = true)
                }

                _uiState.update {
                    it.copy(currentSearchIndex = currentMatchIndex)
                }
            }
        }
    }

    fun findPrevious() {
        val state = _uiState.value
        if (state.searchResultCount == 0 || state.searchQuery.isEmpty()) return

        editor?.let { textProcessor ->
            val text = textProcessor.text?.toString() ?: ""
            val currentPosition = textProcessor.selectionStart

            var prevIndex = text.lastIndexOf(state.searchQuery, currentPosition - 1, ignoreCase = true)
            if (prevIndex == -1) {
                // Wrap around to end
                prevIndex = text.lastIndexOf(state.searchQuery, ignoreCase = true)
            }

            if (prevIndex != -1) {
                textProcessor.resumeAutoScroll()
                textProcessor.setSelection(prevIndex, prevIndex + state.searchQuery.length)

                // Update current index
                val matches = mutableListOf<Int>()
                var index = text.indexOf(state.searchQuery, ignoreCase = true)
                var currentMatchIndex = 0
                var counter = 0
                while (index != -1) {
                    if (index == prevIndex) {
                        currentMatchIndex = counter + 1
                    }
                    matches.add(index)
                    counter++
                    index = text.indexOf(state.searchQuery, index + 1, ignoreCase = true)
                }

                _uiState.update {
                    it.copy(currentSearchIndex = currentMatchIndex)
                }
            }
        }
    }

    fun insertSymbol(symbol: String) {
        editor?.let { textProcessor ->
            val text = textProcessor.text ?: return@let
            val rawStart = textProcessor.selectionStart
            val rawEnd = textProcessor.selectionEnd
            // selectionStart/End can be reversed (backward drag-selection) or -1 (no cursor)
            val start: Int
            val end: Int
            if (rawStart < 0 || rawEnd < 0) {
                start = text.length
                end = text.length
            } else {
                start = minOf(rawStart, rawEnd).coerceIn(0, text.length)
                end = maxOf(rawStart, rawEnd).coerceIn(0, text.length)
            }

            val newText =
                StringBuilder(text)
                    .replace(start, end, symbol)
                    .toString()

            textProcessor.resumeAutoScroll()
            textProcessor.setTextContent(newText)
            textProcessor.setSelection(start + symbol.length)
        }
    }

    fun focusEditor() {
        editor?.let { textProcessor ->
            // Ensure the editor is focusable
            textProcessor.isFocusable = true
            textProcessor.isFocusableInTouchMode = true
            textProcessor.resumeAutoScroll()
            textProcessor.requestFocus()

            // Keep the current selection if there's a search active
            if (_uiState.value.searchQuery.isNotEmpty() && _uiState.value.searchResultCount > 0) {
                // Selection is already set by search, just request focus
                textProcessor.requestFocus()
            } else if (!_uiState.value.isReadOnly) {
                // No search active and not read-only, place cursor at current position
                val currentPosition = textProcessor.selectionEnd
                textProcessor.setSelection(currentPosition)
            }
        }
    }

    fun focusEditorWithCurrentSearchResult() {
        editor?.let { textProcessor ->
            // Ensure the editor is focusable
            textProcessor.isFocusable = true
            textProcessor.isFocusableInTouchMode = true
            textProcessor.resumeAutoScroll()

            val state = _uiState.value
            if (state.searchQuery.isNotEmpty() && state.searchResultCount > 0) {
                // Make sure current search result is selected
                val text = textProcessor.text?.toString() ?: ""
                val currentSelection = textProcessor.selectionStart

                // Find which match is currently selected or find the nearest one
                var matchIndex = text.indexOf(state.searchQuery, currentSelection, ignoreCase = true)
                if (matchIndex == -1 && currentSelection > 0) {
                    // Try from the beginning if no match found after cursor
                    matchIndex = text.indexOf(state.searchQuery, 0, ignoreCase = true)
                }

                if (matchIndex != -1) {
                    textProcessor.setSelection(matchIndex, matchIndex + state.searchQuery.length)
                }
            }
            textProcessor.requestFocus()
        }
    }

    fun selectAll() {
        editor?.let { textProcessor ->
            val text = textProcessor.text?.toString() ?: ""
            if (text.isNotEmpty()) {
                textProcessor.resumeAutoScroll()
                textProcessor.setSelection(0, text.length)
                textProcessor.requestFocus()
            }
        }
    }

    fun cut() {
        editor?.let { textProcessor ->
            if (textProcessor.hasSelection()) {
                textProcessor.onTextContextMenuItem(android.R.id.cut)
            }
        }
    }

    fun copy() {
        editor?.let { textProcessor ->
            if (textProcessor.hasSelection()) {
                textProcessor.onTextContextMenuItem(android.R.id.copy)
            }
        }
    }

    fun paste() {
        editor?.let { textProcessor ->
            if (!_uiState.value.isReadOnly) {
                textProcessor.onTextContextMenuItem(android.R.id.paste)
            }
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
