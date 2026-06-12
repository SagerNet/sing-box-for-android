package io.nekohasekai.sfa.utils

import android.util.Log
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.ConnectionEvents
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LogEntry
import io.nekohasekai.libbox.LogIterator
import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.libbox.OutboundGroupItemIterator
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.sfa.ktx.toList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

open class CommandClient(
    private val scope: CoroutineScope,
    private val connectionTypes: List<ConnectionType>,
    private val handler: Handler,
    private val localOnly: Boolean = false,
) {
    constructor(
        scope: CoroutineScope,
        connectionType: ConnectionType,
        handler: Handler,
        localOnly: Boolean = false,
    ) : this(scope, listOf(connectionType), handler, localOnly)

    private val additionalHandlers = mutableListOf<Handler>()
    private var cachedGroups: MutableList<OutboundGroup>? = null
    private var cachedOutbounds: List<io.nekohasekai.libbox.OutboundGroupItem>? = null

    fun addHandler(handler: Handler) {
        synchronized(additionalHandlers) {
            if (!additionalHandlers.contains(handler)) {
                additionalHandlers.add(handler)
                cachedGroups?.let { groups ->
                    handler.updateGroups(groups)
                }
                cachedOutbounds?.let { outbounds ->
                    handler.updateOutbounds(outbounds)
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
        Outbounds,
    }

    enum class ConnectionErrorKind {
        // A connect attempt failed; retrying is not expected to succeed.
        ConnectFailed,

        // An established connection dropped (app suspension, network change,
        // server restart); reconnecting may recover.
        ConnectionLost,
    }

    interface Handler {
        fun onConnected() {}

        fun onDisconnected() {}

        fun onConnectionError(kind: ConnectionErrorKind, message: String) {}

        fun updateStatus(status: StatusMessage) {}

        fun setDefaultLogLevel(level: Int) {}

        fun clearLogs() {}

        fun appendLogs(message: List<LogEntry>) {}

        fun updateGroups(newGroups: MutableList<OutboundGroup>) {}

        fun updateOutbounds(outbounds: List<io.nekohasekai.libbox.OutboundGroupItem>) {}

        fun initializeClashMode(modeList: List<String>, currentMode: String) {}

        fun updateClashMode(newMode: String) {}

        fun writeConnectionEvents(events: ConnectionEvents) {}
    }

    private val access = Any()
    private var connectionEpoch = 0
    private var commandClient: io.nekohasekai.libbox.CommandClient? = null

    fun connect() {
        val epoch: Int
        val previousClient: io.nekohasekai.libbox.CommandClient?
        synchronized(access) {
            epoch = ++connectionEpoch
            previousClient = commandClient
            commandClient = null
        }
        // A remote connect dials over the network and blocks until the probe
        // completes, so it must run off the main thread.
        if (previousClient != null) {
            // The dropped Go-side Disconnected callback is suppressed by the epoch
            // bump, so the owner-initiated disconnect is reported deterministically.
            getAllHandlers().forEach { it.onDisconnected() }
        }
        scope.launch(Dispatchers.IO) {
            previousClient?.apply {
                runCatching {
                    disconnect()
                }
            }
            val options = CommandClientOptions()
            connectionTypes.forEach { connectionType ->
                val command =
                    when (connectionType) {
                        ConnectionType.Status -> Libbox.CommandStatus
                        ConnectionType.Groups -> Libbox.CommandGroup
                        ConnectionType.Log -> Libbox.CommandLog
                        ConnectionType.ClashMode -> Libbox.CommandClashMode
                        ConnectionType.Connections -> Libbox.CommandConnections
                        ConnectionType.Outbounds -> Libbox.CommandOutbounds
                    }
                options.addCommand(command)
            }
            options.statusInterval = 1 * 1000 * 1000 * 1000
            val remoteServer = if (localOnly) null else CommandTarget.remoteServer
            val newClient: io.nekohasekai.libbox.CommandClient
            try {
                newClient =
                    if (remoteServer != null) {
                        Libbox.newRemoteCommandClient(
                            ClientHandler(epoch),
                            options,
                            CommandTarget.libboxOptions(remoteServer),
                        )
                    } else {
                        io.nekohasekai.libbox.CommandClient(ClientHandler(epoch), options)
                    }
                newClient.connect()
            } catch (e: Exception) {
                Log.d("CommandClient", "connect failed", e)
                if (isActiveEpoch(epoch)) {
                    handler.onConnectionError(
                        ConnectionErrorKind.ConnectFailed,
                        e.message ?: e.toString(),
                    )
                }
                return@launch
            }
            val stale =
                synchronized(access) {
                    if (epoch != connectionEpoch) {
                        true
                    } else {
                        commandClient = newClient
                        false
                    }
                }
            if (stale) {
                runCatching {
                    newClient.disconnect()
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun disconnect() {
        val client: io.nekohasekai.libbox.CommandClient?
        synchronized(access) {
            connectionEpoch++
            client = commandClient
            commandClient = null
        }
        if (client != null) {
            getAllHandlers().forEach { it.onDisconnected() }
            // The owning scope may already be cancelled when this is called from
            // ViewModel.onCleared, so the connection is released independently.
            GlobalScope.launch(Dispatchers.IO) {
                runCatching {
                    client.disconnect()
                }
            }
        }
    }

    private fun isActiveEpoch(epoch: Int): Boolean = synchronized(access) { epoch == connectionEpoch }

    private inner class ClientHandler(private val epoch: Int) : CommandClientHandler {
        override fun connected() {
            if (!isActiveEpoch(epoch)) return
            getAllHandlers().forEach { it.onConnected() }
            Log.d("CommandClient", "connected")
        }

        override fun disconnected(message: String?) {
            if (!isActiveEpoch(epoch)) return
            getAllHandlers().forEach { it.onDisconnected() }
            if (message != null) {
                handler.onConnectionError(ConnectionErrorKind.ConnectionLost, message)
            }
            Log.d("CommandClient", "disconnected: $message")
        }

        override fun writeGroups(message: OutboundGroupIterator?) {
            if (message == null || !isActiveEpoch(epoch)) {
                return
            }
            val groups = mutableListOf<OutboundGroup>()
            while (message.hasNext()) {
                groups.add(message.next())
            }
            cachedGroups = groups
            getAllHandlers().forEach { it.updateGroups(groups) }
        }

        override fun writeOutbounds(message: OutboundGroupItemIterator?) {
            if (message == null || !isActiveEpoch(epoch)) {
                return
            }
            val outbounds = mutableListOf<io.nekohasekai.libbox.OutboundGroupItem>()
            while (message.hasNext()) {
                outbounds.add(message.next())
            }
            cachedOutbounds = outbounds
            getAllHandlers().forEach { it.updateOutbounds(outbounds) }
        }

        override fun setDefaultLogLevel(level: Int) {
            if (!isActiveEpoch(epoch)) return
            getAllHandlers().forEach { it.setDefaultLogLevel(level) }
        }

        override fun clearLogs() {
            if (!isActiveEpoch(epoch)) return
            getAllHandlers().forEach { it.clearLogs() }
        }

        override fun writeLogs(messageList: LogIterator?) {
            if (messageList == null || !isActiveEpoch(epoch)) {
                return
            }
            val logs = messageList.toList()
            getAllHandlers().forEach { it.appendLogs(logs) }
        }

        override fun writeStatus(message: StatusMessage) {
            if (!isActiveEpoch(epoch)) return
            getAllHandlers().forEach { it.updateStatus(message) }
        }

        override fun initializeClashMode(modeList: StringIterator, currentMode: String) {
            if (!isActiveEpoch(epoch)) return
            val modes = modeList.toList()
            getAllHandlers().forEach { it.initializeClashMode(modes, currentMode) }
        }

        override fun updateClashMode(newMode: String) {
            if (!isActiveEpoch(epoch)) return
            getAllHandlers().forEach { it.updateClashMode(newMode) }
        }

        override fun writeConnectionEvents(events: ConnectionEvents?) {
            if (events == null || !isActiveEpoch(epoch)) return
            getAllHandlers().forEach { it.writeConnectionEvents(events) }
        }
    }
}
