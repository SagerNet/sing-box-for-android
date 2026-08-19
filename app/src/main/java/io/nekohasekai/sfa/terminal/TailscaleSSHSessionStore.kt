package io.nekohasekai.sfa.terminal

import io.github.sagernet.libghostty.GhosttyTerminalSession
import io.nekohasekai.libbox.TailscaleSSHHandler
import io.nekohasekai.libbox.TailscaleSSHOptions
import io.nekohasekai.libbox.TailscaleSSHSession
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.ktx.toStringIterator
import io.nekohasekai.sfa.utils.AppLifecycleObserver
import io.nekohasekai.sfa.utils.CommandTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class TailscaleSSHTerminalState(
    val sessions: List<ManagedSession> = emptyList(),
    val activeSessionId: String? = null,
) {
    val activeSession: ManagedSession?
        get() = sessions.firstOrNull { it.id == activeSessionId }
}

/**
 * Holds terminal sessions for the whole process so they survive navigating
 * away from the terminal screen; a session ends only when closed explicitly
 * or when the SSH connection ends.
 */
object TailscaleSSHSessionStore : GhosttyTerminalSession.EventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(TailscaleSSHTerminalState())
    val state: StateFlow<TailscaleSSHTerminalState> = _state.asStateFlow()

    fun addSession(presented: TailscaleSSHPresentedSession) {
        val managed = ManagedSession(
            terminalSession = GhosttyTerminalSession(
                Application.application,
                GhosttyTerminalSession.Options(
                    reportedVersion = "sing-box",
                    terminfoName = presented.terminalType,
                ),
            ),
            presentedSession = presented,
        )
        managed.terminalSession.eventListener = this
        // Covers exits libghostty reports on its own, such as a transport
        // failure, in addition to the ones the SSH handler reports.
        managed.exitWatcher = scope.launch {
            managed.terminalSession.exitStatus.filterNotNull().first()
            onSessionExited(managed)
        }

        _state.value = _state.value.let {
            it.copy(sessions = it.sessions + managed, activeSessionId = managed.id)
        }

        startSSHConnection(managed)
    }

    fun removeSession(id: String) {
        val current = _state.value
        val session = current.sessions.firstOrNull { it.id == id } ?: return
        session.exitWatcher?.cancel()
        session.terminalSession.close()
        disconnectClient(session)
        val remaining = current.sessions.filter { it.id != id }
        val newActiveId = if (current.activeSessionId == id) {
            remaining.lastOrNull()?.id
        } else {
            current.activeSessionId
        }
        _state.value = current.copy(sessions = remaining, activeSessionId = newActiveId)
    }

    fun switchSession(id: String) {
        _state.value = _state.value.copy(activeSessionId = id)
    }

    fun duplicateCurrentSession() {
        val current = _state.value.activeSession ?: return
        addSession(current.presentedSession)
    }

    override fun onNotification(session: GhosttyTerminalSession, title: String, body: String) {
        // The user is watching this session; a system notification is noise.
        if (AppLifecycleObserver.isForeground.value && session.hasAttachedView) return
        TerminalNotifier.notify(Application.application, session.title.value, title, body)
    }

    private fun startSSHConnection(managed: ManagedSession) {
        scope.launch {
            try {
                val options = TailscaleSSHOptions().apply {
                    endpointTag = managed.presentedSession.endpointTag
                    peerAddress = managed.presentedSession.peerAddress
                    username = managed.presentedSession.username
                    terminalType = managed.presentedSession.terminalType
                    columns = 80
                    rows = 24
                    widthPixels = 0
                    heightPixels = 0
                    hostKeys = managed.presentedSession.hostKeys.toStringIterator()
                    forwardAgent = false
                }

                val commandClient = CommandTarget.ownedStandaloneClient()
                managed.commandClient = commandClient
                val sshSession = commandClient.startTailscaleSSHSession(
                    options,
                    object : TailscaleSSHHandler {
                        override fun onReady() {
                            managed.phase.value = TerminalSessionPhase.RUNNING
                        }

                        override fun onOutput(data: ByteArray) {
                            managed.terminalSession.feedOutput(data)
                        }

                        override fun onAuthBanner(message: String) {
                            managed.banner.value = message
                        }

                        override fun onExit(exitCode: Int, signal: String, errorMessage: String) {
                            finishSession(managed, exitCode, signal.takeIf { it.isNotEmpty() }, errorMessage.takeIf { it.isNotEmpty() })
                        }

                        override fun onError(message: String) {
                            finishSession(managed, -1, null, message)
                        }
                    },
                )
                managed.terminalSession.transport = TailscaleSSHTransport(sshSession)
            } catch (e: Exception) {
                finishSession(managed, -1, null, e.message ?: "SSH connection failed")
            }
        }
    }

    private fun finishSession(managed: ManagedSession, exitCode: Int, signal: String?, errorMessage: String?) {
        managed.exitSignal = signal
        managed.exitError = errorMessage
        managed.terminalSession.finish(exitCode)
    }

    private fun onSessionExited(managed: ManagedSession) {
        managed.phase.value = TerminalSessionPhase.FINISHED
        managed.terminalSession.feedOutput(exitNotice(managed).toByteArray())

        val current = _state.value
        if (current.sessions.size <= 1) return
        if (managed.id == current.activeSessionId && managed.terminalSession.exitStatus.value != 0) {
            return
        }
        removeSession(managed.id)
    }

    private fun exitNotice(managed: ManagedSession): String {
        val context = Application.application
        val exitCode = managed.terminalSession.exitStatus.value ?: 0
        return buildString {
            append("\r\n[")
            append(context.getString(R.string.terminal_session_ended))
            val errorMessage = managed.exitError
            if (!errorMessage.isNullOrEmpty()) {
                append(": ").append(errorMessage)
            } else if (exitCode != 0) {
                append(" (exit ").append(exitCode).append(")")
            }
            val signal = managed.exitSignal
            if (!signal.isNullOrEmpty()) {
                append(" (signal ").append(signal).append(")")
            }
            append(" - ")
            append(context.getString(R.string.terminal_press_any_key))
            append("]")
        }
    }

    private fun disconnectClient(session: ManagedSession) {
        val commandClient = session.commandClient ?: return
        session.commandClient = null
        scope.launch {
            runCatching {
                commandClient.disconnect()
            }
        }
    }
}

private class TailscaleSSHTransport(private val session: TailscaleSSHSession) : GhosttyTerminalSession.Transport {
    override fun sendInput(data: ByteArray) {
        session.sendInput(data)
    }

    override fun sendResize(columns: Int, rows: Int, widthPixels: Int, heightPixels: Int) {
        session.sendResize(columns, rows, widthPixels, heightPixels)
    }

    override fun close() {
        session.close()
    }
}
