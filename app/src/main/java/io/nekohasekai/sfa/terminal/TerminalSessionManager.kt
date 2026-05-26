package io.nekohasekai.sfa.terminal

import java.util.UUID

data class ManagedSession(
    val id: String = UUID.randomUUID().toString(),
    val terminalSession: TailscaleSSHTerminalSession,
    val presentedSession: TailscaleSSHPresentedSession,
)
