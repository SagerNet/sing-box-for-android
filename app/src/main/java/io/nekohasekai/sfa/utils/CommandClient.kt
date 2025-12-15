package io.nekohasekai.sfa.utils

import android.util.Log
import go.Seq
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.Connections
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LogEntry
import io.nekohasekai.libbox.LogIterator
import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.sfa.ktx.toList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

open class CommandClient(
    private val scope: CoroutineScope,
    private val connectionTypes: List<ConnectionType>,
    private val handler: Handler,
) {
    constructor(
        scope: CoroutineScope,
        connectionType: ConnectionType,
        handler: Handler,
    ) : this(scope, listOf(connectionType), handler)

    private val additionalHandlers = mutableListOf<Handler>()
    private var cachedGroups: MutableList<OutboundGroup>? = null

    fun addHandler(handler: Handler) {
        synchronized(additionalHandlers) {
            if (!additionalHandlers.contains(handler)) {
                additionalHandlers.add(handler)
                cachedGroups?.let { groups ->
                    handler.updateGroups(groups)
                }
            }
        }
    }

    fun removeHandler(handler: Handler) {
        synchronized(additionalHandlers) {
            additionalHandlers.remove(handler)
        }
    }

    private fun getAllHandlers(): List<Handler> {
        return synchronized(additionalHandlers) {
            listOf(handler) + additionalHandlers
        }
    }

    enum class ConnectionType {
        Status,
        Groups,
        Log,
        ClashMode,
    }

    interface Handler {
        fun onConnected() {}

        fun onDisconnected() {}

        fun updateStatus(status: StatusMessage) {}

        fun setDefaultLogLevel(level: Int) {}

        fun clearLogs() {}

        fun appendLogs(message: List<LogEntry>) {}

        fun updateGroups(newGroups: MutableList<OutboundGroup>) {}

        fun initializeClashMode(
            modeList: List<String>,
            currentMode: String,
        ) {}

        fun updateClashMode(newMode: String) {}
    }

    private var commandClient: CommandClient? = null
    private val clientHandler = ClientHandler()

    fun connect() {
        disconnect()
        val options = CommandClientOptions()
        connectionTypes.forEach { connectionType ->
            val command =
                when (connectionType) {
                    ConnectionType.Status -> Libbox.CommandStatus
                    ConnectionType.Groups -> Libbox.CommandGroup
                    ConnectionType.Log -> Libbox.CommandLog
                    ConnectionType.ClashMode -> Libbox.CommandClashMode
                }
            options.addCommand(command)
        }
        options.statusInterval = 1 * 1000 * 1000 * 1000
        val commandClient = CommandClient(clientHandler, options)
        scope.launch(Dispatchers.IO) {
            for (i in 1..10) {
                delay(100 + i.toLong() * 50)
                try {
                    commandClient.connect()
                } catch (ignored: Exception) {
                    continue
                }
                if (!isActive) {
                    runCatching {
                        commandClient.disconnect()
                    }
                    return@launch
                }
                this@CommandClient.commandClient = commandClient
                return@launch
            }
            runCatching {
                commandClient.disconnect()
            }
        }
    }

    fun disconnect() {
        commandClient?.apply {
            runCatching {
                disconnect()
            }
            Seq.destroyRef(refnum)
        }
        commandClient = null
    }

    private inner class ClientHandler : CommandClientHandler {
        override fun connected() {
            getAllHandlers().forEach { it.onConnected() }
            Log.d("CommandClient", "connected")
        }

        override fun disconnected(message: String?) {
            getAllHandlers().forEach { it.onDisconnected() }
            Log.d("CommandClient", "disconnected: $message")
        }

        override fun writeGroups(message: OutboundGroupIterator?) {
            if (message == null) {
                return
            }
            val groups = mutableListOf<OutboundGroup>()
            while (message.hasNext()) {
                groups.add(message.next())
            }
            cachedGroups = groups
            getAllHandlers().forEach { it.updateGroups(groups) }
        }

        override fun setDefaultLogLevel(level: Int) {
            getAllHandlers().forEach { it.setDefaultLogLevel(level) }
        }

        override fun clearLogs() {
            getAllHandlers().forEach { it.clearLogs() }
        }

        override fun writeLogs(messageList: LogIterator?) {
            if (messageList == null) {
                return
            }
            val logs = messageList.toList()
            getAllHandlers().forEach { it.appendLogs(logs) }
        }

        override fun writeStatus(message: StatusMessage) {
            getAllHandlers().forEach { it.updateStatus(message) }
        }

        override fun initializeClashMode(
            modeList: StringIterator,
            currentMode: String,
        ) {
            val modes = modeList.toList()
            getAllHandlers().forEach { it.initializeClashMode(modes, currentMode) }
        }

        override fun updateClashMode(newMode: String) {
            getAllHandlers().forEach { it.updateClashMode(newMode) }
        }

        override fun writeConnections(message: Connections?) {
        }
    }
}
