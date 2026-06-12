package io.nekohasekai.sfa.terminal

import java.util.UUID

data class ManagedSession(
    val id: String = UUID.randomUUID().toString(),
    val terminalSession: TailscaleSSHTerminalSession,
    val presentedSession: TailscaleSSHPresentedSession,
) {
    // The session owns its command client (a dedicated connection in remote
    // control mode) and must disconnect it when the session ends.
    var commandClient: io.nekohasekai.libbox.CommandClient? = null
}
