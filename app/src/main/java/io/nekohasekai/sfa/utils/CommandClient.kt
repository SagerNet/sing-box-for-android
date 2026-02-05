package io.nekohasekai.sfa.utils

import android.util.Log
import go.Seq
import libbox.CommandClient
import libbox.CommandClientHandler
import libbox.CommandClientOptions
import libbox.ConnectionEvents
import libbox.Libbox
import libbox.LogEntry
import libbox.LogIterator
import libbox.OutboundGroup
import libbox.OutboundGroupIterator
import libbox.StatusMessage
import libbox.StringIterator
import io.nekohasekai.sfa.ktx.toList
import kotlinx.coroutines.CoroutineScope

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

    private fun getAllHandlers(): List<Handler> = synchronized(additionalHandlers) {
        listOf(handler) + additionalHandlers
    }

    enum class ConnectionType {
        Status,
        Groups,
        Log,
        ClashMode,
        Connections,
    }

    interface Handler {
        fun onConnected() {}

        fun onDisconnected() {}

        fun updateStatus(status: StatusMessage) {}

        fun setDefaultLogLevel(level: Int) {}

        fun clearLogs() {}

        fun appendLogs(message: List<LogEntry>) {}

        fun updateGroups(newGroups: MutableList<OutboundGroup>) {}

        fun initializeClashMode(modeList: List<String>, currentMode: String) {}

        fun updateClashMode(newMode: String) {}

        fun writeConnectionEvents(events: ConnectionEvents) {}
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
                    ConnectionType.Connections -> Libbox.CommandConnections
                }
            options.addCommand(command)
        }
        options.statusInterval = 1 * 1000 * 1000 * 1000
        val commandClient = CommandClient(clientHandler, options)
        commandClient.connect()
        this.commandClient = commandClient
    }

    fun disconnect() {
        commandClient?.apply {
            runCatching {
                disconnect()
            }
//            Seq.destroyRef(refnum)
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

        override fun initializeClashMode(modeList: StringIterator, currentMode: String) {
            val modes = modeList.toList()
            getAllHandlers().forEach { it.initializeClashMode(modes, currentMode) }
        }

        override fun updateClashMode(newMode: String) {
            getAllHandlers().forEach { it.updateClashMode(newMode) }
        }

        override fun writeConnectionEvents(events: ConnectionEvents?) {
            if (events == null) return
            getAllHandlers().forEach { it.writeConnectionEvents(events) }
        }
    }
}
