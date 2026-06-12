package io.nekohasekai.sfa.utils

import android.os.SystemClock
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.base.GlobalEventBus
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.database.RemoteServer
import io.nekohasekai.sfa.database.RemoteServerManager
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object RemoteControlManager : CommandClient.Handler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _remoteServer = MutableStateFlow<RemoteServer?>(null)
    val remoteServer = _remoteServer.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _startedAt = MutableStateFlow<Long?>(null)
    val startedAt = _startedAt.asStateFlow()

    // The monitor connection owns the session lifecycle: per-screen clients only
    // connect while it reports connected, and its errors decide between silent
    // reconnects and falling back to the local device.
    private val monitorClient = CommandClient(scope, CommandClient.ConnectionType.Status, this)

    private var sessionHadConnected = false
    private var sessionConnectedAt = 0L
    private var reconnectAttempts = 0
    private var restored = false

    // A dropped session gets this many silent reconnect attempts before the
    // failure is surfaced. The counter resets once a connection survives
    // STABLE_CONNECTION_INTERVAL_MS, so only rapid connect-drop loops exhaust it.
    private const val MAX_RECONNECT_ATTEMPTS = 3
    private const val STABLE_CONNECTION_INTERVAL_MS = 5000L

    fun restore() {
        if (restored) {
            return
        }
        restored = true
        scope.launch {
            val server =
                withContext(Dispatchers.IO) {
                    val serverId = Settings.activeRemoteServerId
                    if (serverId == 0L) {
                        return@withContext null
                    }
                    val storedServer = runCatching { RemoteServerManager.get(serverId) }.getOrNull()
                    if (storedServer == null) {
                        Settings.activeRemoteServerId = 0L
                    }
                    storedServer
                }
            if (server != null && _remoteServer.value == null) {
                enterRemoteControl(server)
            }
            // The initial state was already handled by enterRemoteControl.
            AppLifecycleObserver.isForeground.drop(1).collect { foreground ->
                if (_remoteServer.value == null) {
                    return@collect
                }
                if (foreground) {
                    // A connection cannot survive while the app is in the
                    // background, so resuming always restores the retry budget.
                    reconnectAttempts = 0
                    sessionConnectedAt = 0L
                    monitorClient.connect()
                } else {
                    monitorClient.disconnect()
                }
            }
        }
    }

    fun enterRemoteControl(server: RemoteServer) {
        CommandTarget.setRemoteServer(server)
        resetSessionState()
        _remoteServer.value = server
        if (AppLifecycleObserver.isForeground.value) {
            monitorClient.connect()
        }
        scope.launch(Dispatchers.IO) {
            Settings.activeRemoteServerId = server.id
        }
    }

    fun exitRemoteControl() {
        if (_remoteServer.value == null) {
            return
        }
        CommandTarget.setRemoteServer(null)
        resetSessionState()
        _remoteServer.value = null
        monitorClient.disconnect()
        scope.launch(Dispatchers.IO) {
            Settings.activeRemoteServerId = 0L
        }
    }

    private fun resetSessionState() {
        sessionHadConnected = false
        sessionConnectedAt = 0L
        reconnectAttempts = 0
        _isConnected.value = false
        _startedAt.value = null
    }

    override fun onConnected() {
        scope.launch {
            if (_remoteServer.value == null) {
                return@launch
            }
            sessionHadConnected = true
            sessionConnectedAt = SystemClock.elapsedRealtime()
            _isConnected.value = true
            val serviceStartedAt =
                withContext(Dispatchers.IO) {
                    runCatching { CommandTarget.standaloneClient().startedAt }.getOrNull()
                }
            if (_isConnected.value) {
                _startedAt.value = serviceStartedAt?.takeIf { it > 0 }
            }
        }
    }

    override fun onDisconnected() {
        scope.launch {
            _isConnected.value = false
            _startedAt.value = null
        }
    }

    override fun onConnectionError(kind: CommandClient.ConnectionErrorKind, message: String) {
        scope.launch {
            handleConnectionError(kind, message)
        }
    }

    // A remote session that cannot connect falls back to the local device
    // immediately: leaving the app in remote mode would just make every command
    // call fail at the point of use. A drop of an established session (app
    // suspension, network change, server restart) is recoverable instead, so it
    // reconnects silently and only surfaces the error once reconnecting fails too.
    private suspend fun handleConnectionError(kind: CommandClient.ConnectionErrorKind, message: String) {
        val server = _remoteServer.value ?: return
        if (!AppLifecycleObserver.isForeground.value) {
            // An alert shown now would be invisible and the connection is torn
            // down anyway; recovery happens on the next foreground transition.
            return
        }
        if (kind == CommandClient.ConnectionErrorKind.ConnectionLost) {
            if (sessionConnectedAt != 0L &&
                SystemClock.elapsedRealtime() - sessionConnectedAt >= STABLE_CONNECTION_INTERVAL_MS
            ) {
                reconnectAttempts = 0
            }
            sessionConnectedAt = 0L
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts++
                monitorClient.connect()
                return
            }
        }
        // A non-retryable connect failure, or a dropped session whose retry
        // budget is spent: fall back to the local device, then surface the
        // failure once.
        val description =
            if (sessionHadConnected) {
                Application.application.getString(R.string.remote_disconnected_from, server.displayName)
            } else {
                Application.application.getString(R.string.remote_connect_failed, server.displayName)
            }
        exitRemoteControl()
        GlobalEventBus.emit(UiEvent.ErrorMessage("$description\n$message"))
    }
}
