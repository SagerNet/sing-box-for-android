package io.nekohasekai.sfa.compose.screen.tools

import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.STUNTestHandler
import io.nekohasekai.libbox.STUNTestProgress
import io.nekohasekai.libbox.STUNTestResult
import io.nekohasekai.sfa.compose.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class STUNTestState(
    val phase: Int = -1,
    val externalAddr: String = "",
    val latencyMs: Int = 0,
    val natMapping: Int = 0,
    val natFiltering: Int = 0,
    val natTypeSupported: Boolean = false,
    val isRunning: Boolean = false,
    val server: String = Libbox.STUNDefaultServer,
    val selectedOutbound: String = "",
)

class STUNTestViewModel : BaseViewModel<STUNTestState, Nothing>() {
    private var standaloneTest: io.nekohasekai.libbox.STUNTest? = null
    private var grpcJob: Job? = null

    override fun createInitialState() = STUNTestState()

    fun updateServer(server: String) {
        updateState { copy(server = server) }
    }

    fun selectOutbound(tag: String) {
        updateState { copy(selectedOutbound = tag) }
    }

    fun onVpnDisconnected() {
        cancelTest()
        updateState { copy(selectedOutbound = "") }
    }

    fun startTest(vpnRunning: Boolean) {
        updateState {
            copy(
                phase = -1,
                externalAddr = "",
                latencyMs = 0,
                natMapping = 0,
                natFiltering = 0,
                natTypeSupported = false,
                isRunning = true,
            )
        }

        val server = currentState.server
        val outboundTag = currentState.selectedOutbound
        val handler = createHandler()

        if (vpnRunning) {
            grpcJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    Libbox.newStandaloneCommandClient()
                        .startSTUNTest(server, outboundTag, handler)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        if (!currentState.isRunning) return@withContext
                        updateState { copy(isRunning = false) }
                        grpcJob = null
                        sendError(e)
                    }
                }
            }
        } else {
            val test = Libbox.newSTUNTest()
            standaloneTest = test
            launch {
                withContext(Dispatchers.IO) {
                    test.start(server, handler)
                }
            }
        }
    }

    fun cancelTest() {
        grpcJob?.cancel()
        grpcJob = null
        standaloneTest?.cancel()
        standaloneTest = null
        updateState { copy(isRunning = false) }
    }

    private fun createHandler(): STUNTestHandler {
        return object : STUNTestHandler {
            override fun onProgress(progress: STUNTestProgress?) {
                progress ?: return
                viewModelScope.launch {
                    if (!currentState.isRunning) return@launch
                    updateState {
                        copy(
                            phase = progress.phase.toInt(),
                            externalAddr = progress.externalAddr,
                            latencyMs = progress.latencyMs.toInt(),
                            natMapping = progress.natMapping.toInt(),
                            natFiltering = progress.natFiltering.toInt(),
                        )
                    }
                }
            }

            override fun onResult(result: STUNTestResult?) {
                result ?: return
                viewModelScope.launch {
                    if (!currentState.isRunning) return@launch
                    updateState {
                        copy(
                            phase = Libbox.STUNPhaseDone.toInt(),
                            isRunning = false,
                            externalAddr = result.externalAddr,
                            latencyMs = result.latencyMs.toInt(),
                            natMapping = result.natMapping.toInt(),
                            natFiltering = result.natFiltering.toInt(),
                            natTypeSupported = result.natTypeSupported,
                        )
                    }
                    standaloneTest = null
                    grpcJob = null
                }
            }

            override fun onError(message: String?) {
                viewModelScope.launch {
                    if (!currentState.isRunning) return@launch
                    updateState { copy(isRunning = false) }
                    standaloneTest = null
                    grpcJob = null
                    if (message != null) {
                        sendErrorMessage(message)
                    }
                }
            }
        }
    }
}
