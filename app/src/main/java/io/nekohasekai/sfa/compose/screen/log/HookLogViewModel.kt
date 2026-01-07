package io.nekohasekai.sfa.compose.screen.log

import android.content.Context
import android.text.format.DateFormat
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sfa.bg.LogEntry
import io.nekohasekai.sfa.compose.util.AnsiColorUtils
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.utils.HookErrorClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class HookLogViewModel : BaseLogViewModel() {

    fun loadLogs(context: Context) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                HookErrorClient.query(context)
            }
            if (result.failure != null) {
                val detail = buildErrorMessage(result)
                allLogs.clear()
                _uiState.update {
                    it.copy(
                        logs = emptyList(),
                        isConnected = false,
                        errorTitle = "Error",
                        errorMessage = detail,
                    )
                }
                return@launch
            }
            val logs = result.logs.map { processLogEntry(it) }
            allLogs.clear()
            allLogs.addAll(logs)
            _uiState.update {
                it.copy(
                    logs = emptyList(),
                    isConnected = true,
                    errorTitle = null,
                    errorMessage = null,
                )
            }
            updateDisplayedLogs()
        }
    }

    private companion object {
        private const val ANSI_RESET = "\u001B[0m"
        private const val ANSI_RED = "\u001B[31m"
        private const val ANSI_YELLOW = "\u001B[33m"
        private const val ANSI_CYAN = "\u001B[36m"
        private const val ANSI_WHITE = "\u001B[37m"
    }

    private fun processLogEntry(entry: LogEntry): ProcessedLogEntry {
        val level = when (entry.level) {
            LogEntry.LEVEL_DEBUG -> LogLevel.DEBUG
            LogEntry.LEVEL_INFO -> LogLevel.INFO
            LogEntry.LEVEL_WARN -> LogLevel.WARNING
            LogEntry.LEVEL_ERROR -> LogLevel.ERROR
            else -> LogLevel.Default
        }
        val (levelName, levelColor) = when (entry.level) {
            LogEntry.LEVEL_DEBUG -> "DEBUG" to ANSI_WHITE
            LogEntry.LEVEL_INFO -> "INFO" to ANSI_CYAN
            LogEntry.LEVEL_WARN -> "WARN" to ANSI_YELLOW
            LogEntry.LEVEL_ERROR -> "ERROR" to ANSI_RED
            else -> "UNKNOWN" to ANSI_WHITE
        }
        val timestamp = DateFormat.format("HH:mm:ss", Date(entry.timestamp)).toString()
        val message = buildString {
            append(levelColor).append(levelName).append(ANSI_RESET)
            append("[").append(timestamp).append("] ")
            append("[").append(entry.source).append("]: ")
            append(entry.message)
            if (!entry.stackTrace.isNullOrEmpty()) {
                append("\n").append(entry.stackTrace)
            }
        }
        return ProcessedLogEntry(
            id = logIdGenerator.incrementAndGet(),
            entry = LogEntryData(level, AnsiColorUtils.stripAnsi(message)),
            annotatedString = AnsiColorUtils.ansiToAnnotatedString(message),
        )
    }

    private fun buildErrorMessage(result: HookErrorClient.Result): String {
        val message = when (result.failure) {
            HookErrorClient.Failure.SERVICE_UNAVAILABLE ->
                "Connectivity service unavailable. Reboot or activate LSPosed module."
            HookErrorClient.Failure.TRANSACTION_FAILED ->
                "Hook transaction rejected. Reboot to load LSPosed module."
            HookErrorClient.Failure.REMOTE_ERROR ->
                "Remote error while reading logs."
            HookErrorClient.Failure.PROTOCOL_ERROR ->
                "Log protocol mismatch. Reboot to update LSPosed module."
            null -> "Unknown error."
        }
        val detail = result.detail?.takeIf { it.isNotBlank() }
        return if (detail != null) "$message\n$detail" else message
    }

    override fun updateServiceStatus(status: Status) {
        _uiState.update { it.copy(serviceStatus = status) }
    }

    override fun togglePause() {
        _uiState.update { it.copy(isPaused = false) }
    }

    override fun requestClearLogs() {
    }
}
