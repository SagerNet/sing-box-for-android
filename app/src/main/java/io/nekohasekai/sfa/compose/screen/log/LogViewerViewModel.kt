package io.nekohasekai.sfa.compose.screen.log

import io.nekohasekai.sfa.constant.Status
import kotlinx.coroutines.flow.StateFlow

interface LogViewerViewModel {
    val uiState: StateFlow<LogUiState>
    val scrollToBottomTrigger: StateFlow<Int>
    val isAtBottom: StateFlow<Boolean>

    fun updateServiceStatus(status: Status)
    fun togglePause()
    fun toggleSearch()
    fun toggleOptionsMenu()
    fun updateSearchQuery(query: String)
    fun setLogLevel(level: LogLevel)
    fun setAutoScrollEnabled(enabled: Boolean)
    fun scrollToBottom()
    fun toggleSelectionMode()
    fun toggleLogSelection(index: Int)
    fun clearSelection()
    fun getSelectedLogsText(): String
    fun getAllLogsText(): String
    fun requestClearLogs()
}
