package io.nekohasekai.sfa.terminal

const val DEFAULT_SSH_TERMINAL_TYPE = "xterm-256color"

data class TailscaleSSHPresentedSession(
    val endpointTag: String,
    val peerHostName: String,
    val peerAddress: String,
    val username: String,
    val terminalType: String,
    val hostKeys: List<String>,
)
