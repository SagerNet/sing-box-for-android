package io.nekohasekai.sfa.compose.screen.log

import androidx.compose.ui.text.AnnotatedString
import io.nekohasekai.sfa.constant.Status

data class LogEntryData(
    val level: LogLevel,
    val message: String,
)

data class ProcessedLogEntry(
    val id: Long,
    val entry: LogEntryData,
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
    val errorTitle: String? = null,
    val errorMessage: String? = null,
)
