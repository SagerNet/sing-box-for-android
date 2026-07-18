package io.nekohasekai.sfa.compose.screen.tools

import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.OpenVPNChallengeResponse
import io.nekohasekai.libbox.OpenVPNStatusHandler
import io.nekohasekai.libbox.OpenVPNStatusSubscription
import io.nekohasekai.libbox.OpenVPNStatusUpdate
import io.nekohasekai.sfa.compose.base.BaseViewModel
import io.nekohasekai.sfa.utils.CommandTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OpenVPNChallengeData(
    val id: String,
    val kind: String,
    val username: String,
    val message: String,
    val url: String,
    val secretMessage: String,
    val echo: Boolean,
    val previousError: String,
    val deadline: Long,
)

data class OpenVPNTunnelInfoData(
    val server: String,
    val network: String,
    val cipher: String,
    val ipv4: List<String>,
    val ipv6: List<String>,
    val dns: List<String>,
    val mtu: Int,
    val connectedSince: Long,
)

data class OpenVPNEndpointData(
    val endpointTag: String,
    val state: String,
    val stateText: String,
    val challenge: OpenVPNChallengeData?,
    val error: String,
    val tunnelInfo: OpenVPNTunnelInfoData?,
)

data class OpenVPNStatusState(
    val endpoints: List<OpenVPNEndpointData> = emptyList(),
    val isSubscribed: Boolean = false,
)

class OpenVPNStatusViewModel : BaseViewModel<OpenVPNStatusState, Nothing>() {
    companion object {
        private const val MIN_API_VERSION_OPENVPN = 3
    }

    private var subscription: OpenVPNStatusSubscription? = null

    override fun createInitialState() = OpenVPNStatusState()

    fun subscribe() {
        if (currentState.isSubscribed) return
        updateState { copy(isSubscribed = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = CommandTarget.standaloneClient()
                if (client.getAPIVersion() < MIN_API_VERSION_OPENVPN) {
                    viewModelScope.launch {
                        updateState { copy(endpoints = emptyList(), isSubscribed = false) }
                        subscription = null
                    }
                    return@launch
                }
                subscription =
                    client.subscribeOpenVPNStatus(
                        object : OpenVPNStatusHandler {
                            override fun onStatusUpdate(status: OpenVPNStatusUpdate) {
                                val endpoints = convertUpdate(status)
                                viewModelScope.launch {
                                    if (!currentState.isSubscribed) return@launch
                                    updateState { copy(endpoints = endpoints) }
                                }
                            }

                            override fun onError(message: String) {
                                viewModelScope.launch {
                                    if (!currentState.isSubscribed) return@launch
                                    updateState { copy(endpoints = emptyList(), isSubscribed = false) }
                                    subscription = null
                                    sendErrorMessage(message)
                                }
                            }
                        },
                    )
            } catch (_: Exception) {
                viewModelScope.launch {
                    updateState { copy(endpoints = emptyList(), isSubscribed = false) }
                    subscription = null
                }
            }
        }
    }

    fun cancel() {
        try {
            subscription?.close()
        } catch (_: Exception) {
        }
        subscription = null
        updateState { copy(endpoints = emptyList(), isSubscribed = false) }
    }

    fun endpoint(tag: String): OpenVPNEndpointData? = currentState.endpoints.firstOrNull { it.endpointTag == tag }

    suspend fun submitChallengeResponse(
        endpointTag: String,
        challengeID: String,
        username: String,
        password: String,
        secret: String,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val response = OpenVPNChallengeResponse()
            response.username = username
            response.password = password
            response.secret = secret
            CommandTarget.standaloneClient().submitOpenVPNChallengeResponse(endpointTag, challengeID, response)
            null
        } catch (e: Exception) {
            e.message ?: "submit challenge response failed"
        }
    }

    override fun onCleared() {
        cancel()
        super.onCleared()
    }

    private fun convertUpdate(status: OpenVPNStatusUpdate): List<OpenVPNEndpointData> {
        val endpoints = mutableListOf<OpenVPNEndpointData>()
        val iterator = status.endpoints()
        while (iterator.hasNext()) {
            endpoints.add(convertEndpoint(iterator.next()))
        }
        return endpoints
    }

    private fun convertEndpoint(
        endpoint: io.nekohasekai.libbox.OpenVPNEndpointStatus,
    ): OpenVPNEndpointData {
        val challenge = endpoint.challenge
        val tunnelInfo = endpoint.tunnelInfo
        return OpenVPNEndpointData(
            endpointTag = endpoint.endpointTag,
            state = endpoint.state,
            stateText = endpoint.stateText,
            challenge = if (challenge != null) convertChallenge(challenge) else null,
            error = endpoint.error,
            tunnelInfo = if (tunnelInfo != null) convertTunnelInfo(tunnelInfo) else null,
        )
    }

    private fun convertChallenge(
        challenge: io.nekohasekai.libbox.OpenVPNChallenge,
    ): OpenVPNChallengeData = OpenVPNChallengeData(
        id = challenge.id,
        kind = challenge.kind,
        username = challenge.username,
        message = challenge.message,
        url = challenge.url,
        secretMessage = challenge.secretMessage,
        echo = challenge.echo,
        previousError = challenge.previousError,
        deadline = challenge.deadline,
    )

    private fun convertTunnelInfo(
        info: io.nekohasekai.libbox.OpenVPNTunnelInfo,
    ): OpenVPNTunnelInfoData = OpenVPNTunnelInfoData(
        server = info.server,
        network = info.network,
        cipher = info.cipher,
        ipv4 = info.iPv4().toList(),
        ipv6 = info.iPv6().toList(),
        dns = info.dns().toList(),
        mtu = info.mtu,
        connectedSince = info.connectedSince,
    )

    private fun io.nekohasekai.libbox.StringIterator.toList(): List<String> {
        val values = mutableListOf<String>()
        while (hasNext()) {
            values.add(next())
        }
        return values
    }
}
