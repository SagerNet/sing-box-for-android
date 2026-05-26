package io.nekohasekai.sfa.terminal

data class TailscaleSSHPresentedSession(
    val endpointTag: String,
    val peerHostName: String,
    val peerAddress: String,
    val username: String,
    val hostKeys: List<String>,
)
