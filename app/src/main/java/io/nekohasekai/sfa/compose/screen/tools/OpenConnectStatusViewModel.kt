package io.nekohasekai.sfa.compose.screen.tools

import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OpenConnectStatusHandler
import io.nekohasekai.libbox.OpenConnectStatusSubscription
import io.nekohasekai.libbox.OpenConnectStatusUpdate
import io.nekohasekai.sfa.compose.base.BaseViewModel
import io.nekohasekai.sfa.utils.CommandTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OpenConnectAuthFormChoiceData(
    val value: String,
    val label: String,
)

data class OpenConnectAuthFormFieldData(
    val submissionKey: String,
    val name: String,
    val label: String,
    val kind: String,
    val value: String,
    val options: List<OpenConnectAuthFormChoiceData>,
)

data class OpenConnectAuthFormData(
    val fields: List<OpenConnectAuthFormFieldData>,
)

data class OpenConnectBrowserRequestData(
    val url: String,
    val finalURL: String,
    val cookieNames: List<String>,
    val headerNames: List<String>,
)

data class OpenConnectAuthChallengeData(
    val id: String,
    val banner: String,
    val message: String,
    val error: String,
    val form: OpenConnectAuthFormData?,
    val browser: OpenConnectBrowserRequestData?,
)

data class OpenConnectBrowserCookieData(
    val name: String,
    val value: String,
)

data class OpenConnectBrowserResultData(
    val finalURL: String,
    val cookies: List<OpenConnectBrowserCookieData>,
)

data class OpenConnectTunnelInfoData(
    val server: String,
    val flavor: String,
    val transport: String,
    val ipv4: List<String>,
    val ipv6: List<String>,
    val dns: List<String>,
    val mtu: Int,
    val connectedSince: Long,
)

data class OpenConnectEndpointData(
    val endpointTag: String,
    val state: String,
    val stateText: String,
    val authChallenge: OpenConnectAuthChallengeData?,
    val error: String,
    val tunnelInfo: OpenConnectTunnelInfoData?,
)

data class OpenConnectStatusState(
    val endpoints: List<OpenConnectEndpointData> = emptyList(),
    val isSubscribed: Boolean = false,
)

class OpenConnectStatusViewModel : BaseViewModel<OpenConnectStatusState, Nothing>() {
    companion object {
        private const val MIN_API_VERSION_OPENCONNECT = 3
    }

    private var subscription: OpenConnectStatusSubscription? = null

    override fun createInitialState() = OpenConnectStatusState()

    fun subscribe() {
        if (currentState.isSubscribed) return
        updateState { copy(isSubscribed = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = CommandTarget.standaloneClient()
                val apiVersion = client.getAPIVersion()
                if (apiVersion < MIN_API_VERSION_OPENCONNECT) {
                    viewModelScope.launch {
                        updateState { copy(endpoints = emptyList(), isSubscribed = false) }
                        subscription = null
                    }
                    return@launch
                }
                subscription =
                    client.subscribeOpenConnectStatus(
                        object : OpenConnectStatusHandler {
                            override fun onStatusUpdate(status: OpenConnectStatusUpdate) {
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

    fun endpoint(tag: String): OpenConnectEndpointData? = currentState.endpoints.firstOrNull { it.endpointTag == tag }

    suspend fun submitAuthFormResponse(endpointTag: String, challengeID: String, values: Map<String, String>): String? = withContext(Dispatchers.IO) {
        try {
            val formValues = Libbox.newOpenConnectFormValues()
            for ((key, value) in values) {
                formValues.add(key, value)
            }
            val response = Libbox.newOpenConnectAuthFormResponse(formValues)
            CommandTarget.standaloneClient().submitOpenConnectAuthResponse(endpointTag, challengeID, response)
            null
        } catch (e: Exception) {
            e.message ?: "submit auth form failed"
        }
    }

    suspend fun submitBrowserResponse(endpointTag: String, challengeID: String, result: OpenConnectBrowserResultData): String? = withContext(Dispatchers.IO) {
        try {
            val browserResult = Libbox.newOpenConnectBrowserResult(result.finalURL)
            for (cookie in result.cookies) {
                browserResult.addCookie(cookie.name, cookie.value)
            }
            val response = Libbox.newOpenConnectBrowserAuthResponse(browserResult)
            CommandTarget.standaloneClient().submitOpenConnectAuthResponse(endpointTag, challengeID, response)
            null
        } catch (exception: Exception) {
            exception.message ?: "submit browser authentication result failed"
        }
    }

    override fun onCleared() {
        cancel()
        super.onCleared()
    }

    private fun convertUpdate(status: OpenConnectStatusUpdate): List<OpenConnectEndpointData> {
        val endpoints = mutableListOf<OpenConnectEndpointData>()
        val iterator = status.endpoints()
        while (iterator.hasNext()) {
            endpoints.add(convertEndpoint(iterator.next()))
        }
        return endpoints
    }

    private fun convertEndpoint(
        endpoint: io.nekohasekai.libbox.OpenConnectEndpointStatus,
    ): OpenConnectEndpointData {
        val authChallenge = endpoint.authChallenge
        val tunnelInfo = endpoint.tunnelInfo
        return OpenConnectEndpointData(
            endpointTag = endpoint.endpointTag,
            state = endpoint.state,
            stateText = endpoint.stateText,
            authChallenge = if (authChallenge != null) convertAuthChallenge(authChallenge) else null,
            error = endpoint.error,
            tunnelInfo = if (tunnelInfo != null) convertTunnelInfo(tunnelInfo) else null,
        )
    }

    private fun convertAuthChallenge(
        challenge: io.nekohasekai.libbox.OpenConnectAuthChallenge,
    ): OpenConnectAuthChallengeData {
        val form = challenge.form
        val fields = mutableListOf<OpenConnectAuthFormFieldData>()
        if (form != null) {
            val fieldIterator = form.fields()
            while (fieldIterator.hasNext()) {
                val field = fieldIterator.next()
                val options = mutableListOf<OpenConnectAuthFormChoiceData>()
                val optionIterator = field.options()
                while (optionIterator.hasNext()) {
                    val option = optionIterator.next()
                    options.add(OpenConnectAuthFormChoiceData(value = option.value, label = option.label))
                }
                fields.add(
                    OpenConnectAuthFormFieldData(
                        submissionKey = field.submissionKey,
                        name = field.name,
                        label = field.label,
                        kind = field.kind,
                        value = field.value,
                        options = options,
                    ),
                )
            }
        }
        val browser = challenge.browser
        return OpenConnectAuthChallengeData(
            id = challenge.id,
            banner = challenge.banner,
            message = challenge.message,
            error = challenge.error,
            form = if (form != null) OpenConnectAuthFormData(fields) else null,
            browser = if (browser != null) {
                OpenConnectBrowserRequestData(
                    url = browser.url,
                    finalURL = browser.finalURL,
                    cookieNames = browser.cookieNames().toList(),
                    headerNames = browser.headerNames().toList(),
                )
            } else {
                null
            },
        )
    }

    private fun convertTunnelInfo(
        info: io.nekohasekai.libbox.OpenConnectTunnelInfo,
    ): OpenConnectTunnelInfoData = OpenConnectTunnelInfoData(
        server = info.server,
        flavor = info.flavor,
        transport = info.transport,
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
