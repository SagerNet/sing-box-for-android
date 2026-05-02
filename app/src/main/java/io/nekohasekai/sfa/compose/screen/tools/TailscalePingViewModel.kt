package io.nekohasekai.sfa.compose.screen.tools

import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.TailscalePingHandler
import io.nekohasekai.libbox.TailscalePingResult
import io.nekohasekai.sfa.compose.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TailscalePingState(
    val isRunning: Boolean = false,
    val hasResult: Boolean = false,
    val latencyMs: Double = 0.0,
    val isDirect: Boolean = false,
    val derpRegionCode: String = "",
    val endpoint: String = "",
    val latencyHistory: List<Float> = emptyList(),
)

class TailscalePingViewModel : BaseViewModel<TailscalePingState, Nothing>() {
    private val maxHistorySize = 30
    private var commandClient: CommandClient? = null
    private var grpcJob: Job? = null

    override fun createInitialState() = TailscalePingState()

    fun startPing(endpointTag: String, peerIP: String) {
        updateState {
            copy(
                isRunning = true,
                hasResult = false,
                latencyHistory = emptyList(),
            )
        }

        val client = Libbox.newStandaloneCommandClient()
        commandClient = client

        grpcJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                client.startTailscalePing(
                    endpointTag,
                    peerIP,
                    object : TailscalePingHandler {
                        override fun onPingResult(result: TailscalePingResult?) {
                            result ?: return
                            viewModelScope.launch {
                                if (!currentState.isRunning) return@launch
                                if (result.error.isNotEmpty()) return@launch
                                val newHistory = currentState.latencyHistory.toMutableList()
                                newHistory.add(result.latencyMs.toFloat())
                                if (newHistory.size > maxHistorySize) {
                                    newHistory.removeFirst()
                                }
                                updateState {
                                    copy(
                                        hasResult = true,
                                        latencyMs = result.latencyMs,
                                        isDirect = result.isDirect,
                                        derpRegionCode = result.derpRegionCode,
                                        endpoint = result.endpoint,
                                        latencyHistory = newHistory,
                                    )
                                }
                            }
                        }

                        override fun onError(message: String?) {
                            viewModelScope.launch {
                                if (!currentState.isRunning) return@launch
                                updateState { copy(isRunning = false) }
                                commandClient = null
                                grpcJob = null
                            }
                        }
                    },
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!currentState.isRunning) return@withContext
                    updateState { copy(isRunning = false) }
                    commandClient = null
                    grpcJob = null
                }
            }
        }
    }

    fun stopPing() {
        grpcJob?.cancel()
        grpcJob = null
        try {
            commandClient?.disconnect()
        } catch (_: Exception) {
        }
        commandClient = null
        updateState { copy(isRunning = false) }
    }

    override fun onCleared() {
        super.onCleared()
        stopPing()
    }
}
