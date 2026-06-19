package io.nekohasekai.sfa.compose.screen.tools

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TailscaleSSHHandler
import io.nekohasekai.libbox.TailscaleSSHOptions
import io.nekohasekai.sfa.compose.base.BaseViewModel
import io.nekohasekai.sfa.terminal.ManagedSession
import io.nekohasekai.sfa.terminal.TailscaleSSHPresentedSession
import io.nekohasekai.sfa.terminal.TailscaleSSHTerminalSession
import io.nekohasekai.sfa.utils.CommandTarget
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

data class TailscaleSSHTerminalState(
    val sessions: List<ManagedSession> = emptyList(),
    val activeSessionId: String? = null,
    val version: Int = 0,
) {
    val activeSession: ManagedSession?
        get() = sessions.firstOrNull { it.id == activeSessionId }
}

class TailscaleSSHTerminalViewModel : BaseViewModel<TailscaleSSHTerminalState, Nothing>() {
    override fun createInitialState() = TailscaleSSHTerminalState()

    var sessionClient: TerminalSessionClient? = null

    fun addSession(presented: TailscaleSSHPresentedSession) {
        val client = sessionClient ?: return
        val terminalSession = TailscaleSSHTerminalSession(client)
        val managed = ManagedSession(
            terminalSession = terminalSession,
            presentedSession = presented,
        )

        terminalSession.setPhaseCallback(object : TailscaleSSHTerminalSession.PhaseCallback {
            override fun onPhaseChanged(phase: TailscaleSSHTerminalSession.Phase) {
                updateState { copy(sessions = sessions.toList(), version = version + 1) }
            }

            override fun onAuthBanner(message: String) {
                updateState { copy(sessions = sessions.toList(), version = version + 1) }
            }
        })

        updateState {
            copy(
                sessions = sessions + managed,
                activeSessionId = managed.id,
            )
        }

        startSSHConnection(managed)
    }

    fun removeSession(id: String) {
        val session = currentState.sessions.firstOrNull { it.id == id } ?: return
        session.terminalSession.close()
        disconnectClient(session)
        val remaining = currentState.sessions.filter { it.id != id }
        val newActiveId = if (currentState.activeSessionId == id) {
            remaining.lastOrNull()?.id
        } else {
            currentState.activeSessionId
        }
        updateState { copy(sessions = remaining, activeSessionId = newActiveId) }
    }

    fun switchSession(id: String) {
        updateState { copy(activeSessionId = id) }
    }

    fun duplicateCurrentSession() {
        val current = currentState.activeSession ?: return
        addSession(current.presentedSession)
    }

    fun onTitleChanged() {
        updateState { copy(version = version + 1) }
    }

    private fun startSSHConnection(managed: ManagedSession) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val options = TailscaleSSHOptions().apply {
                    endpointTag = managed.presentedSession.endpointTag
                    peerAddress = managed.presentedSession.peerAddress
                    username = managed.presentedSession.username
                    terminalType = "xterm-256color"
                    columns = 80
                    rows = 24
                    widthPixels = 0
                    heightPixels = 0
                    hostKeys = object : StringIterator {
                        private var index = 0
                        override fun hasNext(): Boolean = index < managed.presentedSession.hostKeys.size
                        override fun next(): String = managed.presentedSession.hostKeys[index++]
                        override fun len(): Int = managed.presentedSession.hostKeys.size
                    }
                    forwardAgent = false
                }

                val commandClient = CommandTarget.ownedStandaloneClient()
                managed.commandClient = commandClient
                val sshSession = commandClient.startTailscaleSSHSession(
                    options,
                    object : TailscaleSSHHandler {
                        override fun onReady() {
                            managed.terminalSession.onReady()
                        }

                        override fun onOutput(data: ByteArray) {
                            managed.terminalSession.feedOutput(data)
                        }

                        override fun onAuthBanner(message: String) {
                            managed.terminalSession.onAuthBanner(message)
                        }

                        override fun onExit(exitCode: Int, signal: String, errorMessage: String) {
                            managed.terminalSession.onExit(exitCode, signal, errorMessage)
                        }

                        override fun onError(message: String) {
                            managed.terminalSession.onError(message)
                        }
                    },
                )
                managed.terminalSession.setSshSession(sshSession)
            } catch (e: Exception) {
                managed.terminalSession.onError(e.message ?: "SSH connection failed")
            }
        }
    }

    private fun disconnectClient(session: ManagedSession) {
        val commandClient = session.commandClient ?: return
        session.commandClient = null
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            runCatching {
                commandClient.disconnect()
            }
        }
    }

    override fun onCleared() {
        for (session in currentState.sessions) {
            session.terminalSession.close()
            disconnectClient(session)
        }
        super.onCleared()
    }
}
