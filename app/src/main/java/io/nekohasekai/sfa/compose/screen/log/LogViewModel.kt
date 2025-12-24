package io.nekohasekai.sfa.compose.screen.log

import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LogEntry
import io.nekohasekai.sfa.compose.util.AnsiColorUtils
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.utils.CommandClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicLong

data class ProcessedLogEntry(
    val id: Long,
    val originalEntry: LogEntry,
    val annotatedString: AnnotatedString,
)

enum class LogLevel(val label: String, val priority: Int) {
    Default("Default", 7),

    PANIC("Panic", 0),
    FATAL("Fatal", 1),
    ERROR("Error", 2),
    WARNING("Warn", 3),
    INFO("Info", 4),
    DEBUG("Debug", 5),
    TRACE("Trace", 6),
}

data class LogUiState(
    val logs: List<ProcessedLogEntry> = emptyList(),
    val isConnected: Boolean = false,
    val serviceStatus: Status = Status.Stopped,
    val isPaused: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val defaultLogLevel: LogLevel = LogLevel.Default,
    val filterLogLevel: LogLevel = LogLevel.Default,
    val isOptionsMenuOpen: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedLogIndices: Set<Int> = emptySet(),
)

class LogViewModel : ViewModel(), CommandClient.Handler {
    companion object {
        private val maxLines = 3000
    }

    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    private val _autoScrollEnabled = MutableStateFlow(true)
    val isAtBottom: StateFlow<Boolean> = _autoScrollEnabled.asStateFlow()

    private val _scrollToBottomTrigger = MutableStateFlow(0)
    val scrollToBottomTrigger: StateFlow<Int> = _scrollToBottomTrigger.asStateFlow()

    private val _searchQueryInternal = MutableStateFlow("")
    private val logIdGenerator = AtomicLong(0)

    private val allLogs = LinkedList<ProcessedLogEntry>()
    private val bufferedLogs = LinkedList<ProcessedLogEntry>()
    private val commandClient =
        CommandClient(
            scope = viewModelScope,
            connectionType = CommandClient.ConnectionType.Log,
            handler = this,
        )

    init {
        viewModelScope.launch {
            _searchQueryInternal
                .debounce(300)
                .distinctUntilChanged()
                .collect { _ ->
                    updateDisplayedLogs()
                }
        }
    }

    private fun processLogEntry(entry: LogEntry): ProcessedLogEntry {
        return ProcessedLogEntry(
            id = logIdGenerator.incrementAndGet(),
            originalEntry = entry,
            annotatedString = AnsiColorUtils.ansiToAnnotatedString(entry.message),
        )
    }

    fun updateServiceStatus(status: Status) {
        _uiState.update { it.copy(serviceStatus = status) }

        when (status) {
            Status.Started -> {
                commandClient.connect()
            }

            Status.Stopped, Status.Stopping -> {
                commandClient.disconnect()
                _uiState.update { it.copy(isConnected = false) }
            }

            else -> {}
        }
    }

    override fun onConnected() {
        _uiState.update { it.copy(isConnected = true) }
    }

    override fun onDisconnected() {
        _uiState.update { it.copy(isConnected = false) }
    }

    override fun setDefaultLogLevel(level: Int) {
        val logLevel = LogLevel.entries.find { it.priority == level } ?: error("Unknown log level: $level")
        _uiState.update { it.copy(defaultLogLevel = logLevel) }
        updateDisplayedLogs()
    }

    override fun clearLogs() {
        allLogs.clear()
        bufferedLogs.clear()
        _uiState.update { it.copy(isPaused = false) }
        updateDisplayedLogs()
    }

    fun requestClearLogs() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    Libbox.newStandaloneCommandClient().clearLogs()
                }
            }
        }
    }

    override fun appendLogs(message: List<LogEntry>) {
        val processedLogs = message.map { processLogEntry(it) }
        if (_uiState.value.isPaused) {
            bufferedLogs.addAll(processedLogs)
        } else {
            val totalSize = allLogs.size + processedLogs.size
            val removeCount = (totalSize - maxLines).coerceAtLeast(0)

            if (removeCount > 0) {
                repeat(removeCount) {
                    allLogs.removeFirst()
                }
            }

            allLogs.addAll(processedLogs)
            updateDisplayedLogs()

            if (_autoScrollEnabled.value && !_uiState.value.isPaused && !_uiState.value.isSearchActive) {
                scrollToBottom()
            }
        }
    }

    fun togglePause() {
        val currentState = _uiState.value
        if (currentState.isPaused && bufferedLogs.isNotEmpty()) {
            // When resuming, add buffered logs
            val totalSize = allLogs.size + bufferedLogs.size
            val removeCount = (totalSize - maxLines).coerceAtLeast(0)

            if (removeCount > 0) {
                repeat(removeCount) {
                    allLogs.removeFirst()
                }
            }

            allLogs.addAll(bufferedLogs)
            bufferedLogs.clear()
        }

        _uiState.update { it.copy(isPaused = !it.isPaused) }
        updateDisplayedLogs()
    }

    fun toggleSearch() {
        _uiState.update {
            it.copy(
                isSearchActive = !it.isSearchActive,
                searchQuery = if (!it.isSearchActive) it.searchQuery else "",
            )
        }
        updateDisplayedLogs()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        _searchQueryInternal.value = query
    }

    fun setLogLevel(level: LogLevel) {
        _uiState.update { it.copy(filterLogLevel = level) }
        updateDisplayedLogs()
    }

    fun toggleOptionsMenu() {
        _uiState.update { it.copy(isOptionsMenuOpen = !it.isOptionsMenuOpen) }
    }

    fun setAutoScrollEnabled(enabled: Boolean) {
        _autoScrollEnabled.value = enabled
    }

    fun scrollToBottom() {
        _autoScrollEnabled.value = true
        _scrollToBottomTrigger.value++
    }

    fun toggleSelectionMode() {
        _uiState.update {
            if (it.isSelectionMode) {
                // Exit selection mode, clear selections, and resume if it was paused by selection mode
                it.copy(isSelectionMode = false, selectedLogIndices = emptySet(), isPaused = false)
            } else {
                // Enter selection mode and pause log updates
                it.copy(isSelectionMode = true, isPaused = true)
            }
        }
    }

    fun toggleLogSelection(index: Int) {
        _uiState.update { state ->
            val newSelection =
                if (state.selectedLogIndices.contains(index)) {
                    state.selectedLogIndices - index
                } else {
                    state.selectedLogIndices + index
                }

            // Exit selection mode and unpause if no items are selected
            if (newSelection.isEmpty()) {
                state.copy(
                    isSelectionMode = false,
                    selectedLogIndices = emptySet(),
                    isPaused = false,
                )
            } else {
                state.copy(selectedLogIndices = newSelection)
            }
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(isSelectionMode = false, selectedLogIndices = emptySet(), isPaused = false)
        }
    }

    fun getSelectedLogsText(): String {
        val state = _uiState.value
        return state.selectedLogIndices
            .sorted()
            .mapNotNull { index ->
                state.logs.getOrNull(index)?.originalEntry?.message
            }
            .joinToString("\n")
    }

    fun getAllLogsText(): String {
        return _uiState.value.logs.joinToString("\n") { it.originalEntry.message }
    }

    private fun updateDisplayedLogs() {
        val currentState = _uiState.value
        val levelPriority =
            if (currentState.filterLogLevel != LogLevel.Default) {
                currentState.filterLogLevel.priority
            } else {
                currentState.defaultLogLevel.priority
            }
        val searchQuery = currentState.searchQuery

        val logsToDisplay =
            allLogs.asSequence()
                .filter { log -> log.originalEntry.level <= levelPriority }
                .filter { log ->
                    searchQuery.isEmpty() || log.originalEntry.message.contains(searchQuery, ignoreCase = true)
                }
                .toList()

        val selectionCleared =
            if (_uiState.value.isSelectionMode && _uiState.value.logs != logsToDisplay) {
                emptySet<Int>()
            } else {
                _uiState.value.selectedLogIndices
            }

        _uiState.update { it.copy(logs = logsToDisplay, selectedLogIndices = selectionCleared) }
    }

    override fun onCleared() {
        super.onCleared()
        commandClient.disconnect()
    }
}
