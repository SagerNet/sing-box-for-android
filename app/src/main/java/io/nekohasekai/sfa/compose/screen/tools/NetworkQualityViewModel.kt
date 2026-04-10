package io.nekohasekai.sfa.compose.screen.tools

import android.content.Context
import android.net.ConnectivityManager
import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.NetworkQualityProgress
import io.nekohasekai.libbox.NetworkQualityResult
import io.nekohasekai.libbox.NetworkQualityTestHandler
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MaxRuntimeOption(val seconds: Int, val labelRes: Int) {
    THIRTY(30, R.string.network_quality_max_runtime_30s),
    SIXTY(60, R.string.network_quality_max_runtime_60s),
}

data class NetworkQualityState(
    val phase: Int = -1,
    val idleLatencyMs: Int = 0,
    val downloadCapacity: Long = 0,
    val uploadCapacity: Long = 0,
    val downloadRPM: Int = 0,
    val uploadRPM: Int = 0,
    val downloadCapacityAccuracy: Int = 0,
    val uploadCapacityAccuracy: Int = 0,
    val downloadRPMAccuracy: Int = 0,
    val uploadRPMAccuracy: Int = 0,
    val isRunning: Boolean = false,
    val configURL: String = Libbox.NetworkQualityDefaultConfigURL,
    val serial: Boolean = false,
    val http3: Boolean = false,
    val maxRuntime: MaxRuntimeOption = MaxRuntimeOption.THIRTY,
    val selectedOutbound: String = "",
    val showMeteredWarning: Boolean = false,
)

class NetworkQualityViewModel : BaseViewModel<NetworkQualityState, Nothing>() {
    private var standaloneTest: io.nekohasekai.libbox.NetworkQualityTest? = null
    private var grpcJob: Job? = null

    override fun createInitialState() = NetworkQualityState()

    fun updateConfigURL(url: String) {
        updateState { copy(configURL = url) }
    }

    fun selectOutbound(tag: String) {
        updateState { copy(selectedOutbound = tag) }
    }

    fun setSerial(value: Boolean) {
        updateState { copy(serial = value) }
    }

    fun setHttp3(value: Boolean) {
        updateState { copy(http3 = value) }
    }

    fun setMaxRuntime(option: MaxRuntimeOption) {
        updateState { copy(maxRuntime = option) }
    }

    fun onVpnDisconnected() {
        cancelTest()
        updateState { copy(selectedOutbound = "") }
    }

    fun requestStartTest(context: Context, vpnRunning: Boolean) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (connectivityManager.isActiveNetworkMetered) {
            updateState { copy(showMeteredWarning = true) }
        } else {
            startTest(vpnRunning)
        }
    }

    fun dismissMeteredWarning() {
        updateState { copy(showMeteredWarning = false) }
    }

    fun confirmMeteredStart(vpnRunning: Boolean) {
        updateState { copy(showMeteredWarning = false) }
        startTest(vpnRunning)
    }

    private fun startTest(vpnRunning: Boolean) {
        updateState {
            copy(
                phase = -1,
                idleLatencyMs = 0,
                downloadCapacity = 0,
                uploadCapacity = 0,
                downloadRPM = 0,
                uploadRPM = 0,
                downloadCapacityAccuracy = 0,
                uploadCapacityAccuracy = 0,
                downloadRPMAccuracy = 0,
                uploadRPMAccuracy = 0,
                isRunning = true,
            )
        }

        val configURL = currentState.configURL
        val outboundTag = currentState.selectedOutbound
        val serial = currentState.serial
        val http3 = currentState.http3
        val maxRuntimeSeconds = currentState.maxRuntime.seconds
        val handler = createHandler()

        if (vpnRunning) {
            grpcJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    Libbox.newStandaloneCommandClient()
                        .startNetworkQualityTest(
                            configURL,
                            outboundTag,
                            serial,
                            maxRuntimeSeconds,
                            http3,
                            handler,
                        )
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
            val test = Libbox.newNetworkQualityTest()
            standaloneTest = test
            launch {
                withContext(Dispatchers.IO) {
                    test.start(configURL, serial, maxRuntimeSeconds, http3, handler)
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

    private fun createHandler(): NetworkQualityTestHandler {
        return object : NetworkQualityTestHandler {
            override fun onProgress(progress: NetworkQualityProgress?) {
                progress ?: return
                viewModelScope.launch {
                    if (!currentState.isRunning) return@launch
                    updateState {
                        copy(
                            phase = progress.phase.toInt(),
                            idleLatencyMs = progress.idleLatencyMs.toInt(),
                            downloadCapacity = progress.downloadCapacity,
                            uploadCapacity = progress.uploadCapacity,
                            downloadRPM = progress.downloadRPM.toInt(),
                            uploadRPM = progress.uploadRPM.toInt(),
                            downloadCapacityAccuracy = progress.downloadCapacityAccuracy.toInt(),
                            uploadCapacityAccuracy = progress.uploadCapacityAccuracy.toInt(),
                            downloadRPMAccuracy = progress.downloadRPMAccuracy.toInt(),
                            uploadRPMAccuracy = progress.uploadRPMAccuracy.toInt(),
                        )
                    }
                }
            }

            override fun onResult(result: NetworkQualityResult?) {
                result ?: return
                viewModelScope.launch {
                    if (!currentState.isRunning) return@launch
                    updateState {
                        copy(
                            phase = Libbox.NetworkQualityPhaseDone.toInt(),
                            idleLatencyMs = result.idleLatencyMs.toInt(),
                            downloadCapacity = result.downloadCapacity,
                            uploadCapacity = result.uploadCapacity,
                            downloadRPM = result.downloadRPM.toInt(),
                            uploadRPM = result.uploadRPM.toInt(),
                            downloadCapacityAccuracy = result.downloadCapacityAccuracy.toInt(),
                            uploadCapacityAccuracy = result.uploadCapacityAccuracy.toInt(),
                            downloadRPMAccuracy = result.downloadRPMAccuracy.toInt(),
                            uploadRPMAccuracy = result.uploadRPMAccuracy.toInt(),
                            isRunning = false,
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
