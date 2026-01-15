package io.nekohasekai.sfa.compose.screen.log

import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LogEntry
import io.nekohasekai.sfa.compose.util.AnsiColorUtils
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.utils.AppLifecycleObserver
import io.nekohasekai.sfa.utils.CommandClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedList

class LogViewModel : BaseLogViewModel(), CommandClient.Handler {
    companion object {
        private val maxLines = 3000
    }

    private val bufferedLogs = LinkedList<ProcessedLogEntry>()
    private val commandClient =
        CommandClient(
            scope = viewModelScope,
            connectionType = CommandClient.ConnectionType.Log,
            handler = this,
        )
    private var lastServiceStatus: Status = Status.Stopped

    init {
        viewModelScope.launch {
            AppLifecycleObserver.isForeground.collect { foreground ->
                if (lastServiceStatus != Status.Started) return@collect
                if (foreground) {
                    commandClient.connect()
                } else {
                    commandClient.disconnect()
                }
            }
        }
    }

    private fun processLogEntry(entry: LogEntry): ProcessedLogEntry {
        val level = LogLevel.entries.find { it.priority == entry.level } ?: LogLevel.Default
        return ProcessedLogEntry(
            id = logIdGenerator.incrementAndGet(),
            entry = LogEntryData(level = level, message = entry.message),
            annotatedString = AnsiColorUtils.ansiToAnnotatedString(entry.message),
        )
    }

    override fun updateServiceStatus(status: Status) {
        lastServiceStatus = status
        _uiState.update { it.copy(serviceStatus = status) }

        when (status) {
            Status.Started -> {
                if (AppLifecycleObserver.isForeground.value) {
                    commandClient.connect()
                }
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

    override fun requestClearLogs() {
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

    override fun togglePause() {
        val currentState = _uiState.value
        if (currentState.isPaused && bufferedLogs.isNotEmpty()) {
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

    override fun onCleared() {
        super.onCleared()
        commandClient.disconnect()
    }
}
