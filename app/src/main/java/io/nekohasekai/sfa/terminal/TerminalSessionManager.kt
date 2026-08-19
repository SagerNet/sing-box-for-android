package io.nekohasekai.sfa.terminal

import io.github.sagernet.libghostty.GhosttyTerminalSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

enum class TerminalSessionPhase { CONNECTING, RUNNING, FINISHED }

data class ManagedSession(
    val id: String = UUID.randomUUID().toString(),
    val terminalSession: GhosttyTerminalSession,
    val presentedSession: TailscaleSSHPresentedSession,
) {
    // The session owns its command client (a dedicated connection in remote
    // control mode) and must disconnect it when the session ends.
    var commandClient: io.nekohasekai.libbox.CommandClient? = null

    val phase = MutableStateFlow(TerminalSessionPhase.CONNECTING)

    val banner = MutableStateFlow<String?>(null)

    var exitWatcher: Job? = null

    // Exit detail for the notice written into the terminal; both stay null when
    // the session ends through a transport failure inside libghostty.
    var exitSignal: String? = null

    var exitError: String? = null
}
