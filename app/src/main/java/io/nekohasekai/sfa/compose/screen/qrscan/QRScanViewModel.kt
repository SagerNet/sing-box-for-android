package io.nekohasekai.sfa.compose.screen.qrscan

import android.app.Application
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import libbox.Libbox
import io.nekohasekai.sfa.qrs.QRSDecoder
import io.nekohasekai.sfa.qrs.readIntLE
import io.nekohasekai.sfa.vendor.Vendor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

sealed class QRScanResult {
    data class RemoteProfile(val uri: Uri) : QRScanResult()
    data class QRSData(val data: ByteArray) : QRScanResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as QRSData
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }
}

data class QRScanUiState(
    val isLoading: Boolean = true,
    val useFrontCamera: Boolean = false,
    val torchEnabled: Boolean = false,
    val useVendorAnalyzer: Boolean = true,
    val vendorAnalyzerAvailable: Boolean = false,
    val qrsMode: Boolean = false,
    val qrsProgress: Pair<Int, Int>? = null,
    val cropArea: QRCodeCropArea? = null,
    val errorMessage: String? = null,
    val result: QRScanResult? = null,
    val zoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
)

class QRScanViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "QRScanViewModel"
    }

    private val _uiState = MutableStateFlow(QRScanUiState())
    val uiState: StateFlow<QRScanUiState> = _uiState.asStateFlow()

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageAnalyzer: ImageAnalysis.Analyzer? = null
    private var cameraPreview: Preview? = null

    private var qrsDecoder: QRSDecoder? = null
    private val showingError = AtomicBoolean(false)
    private val qrsLock = Any()

    private val vendorAnalyzer: ImageAnalysis.Analyzer? = Vendor.createQRCodeAnalyzer(
        onSuccess = { rawValue -> handleScanSuccess(rawValue) },
        onFailure = { exception -> handleScanFailure(exception) },
        onCropArea = ::updateCropArea,
    )

    init {
        _uiState.update {
            it.copy(
                vendorAnalyzerAvailable = vendorAnalyzer != null,
                useVendorAnalyzer = vendorAnalyzer != null,
            )
        }
    }

    private val onSuccess: (String) -> Unit = { rawValue: String ->
        handleScanSuccess(rawValue)
    }

    private val onFailure: (Exception) -> Unit = { exception ->
        handleScanFailure(exception)
    }

    private fun updateCropArea(area: QRCodeCropArea?) {
        _uiState.update { state ->
            if (state.cropArea == area) {
                state
            } else {
                state.copy(cropArea = area)
            }
        }
    }

    private fun handleScanSuccess(rawValue: String) {
        Log.d(TAG, "Scanned: ${rawValue.take(100)}...")
        val qrsPayload = extractQRSPayload(rawValue)
        Log.d(TAG, "extractQRSPayload result: ${qrsPayload?.size ?: "null"}")
        if (qrsPayload != null) {
            handleQRSFrame(qrsPayload)
        } else {
            updateCropArea(null)
            if (_uiState.value.qrsMode) {
                resetQRSState()
            }
            imageAnalysis?.clearAnalyzer()
            processQRCode(rawValue)
        }
    }

    private fun handleScanFailure(exception: Exception) {
        if (_uiState.value.qrsMode) {
            return
        }
        updateCropArea(null)
        imageAnalysis?.clearAnalyzer()
        if (showingError.compareAndSet(false, true)) {
            resetAnalyzer()
            _uiState.update { it.copy(errorMessage = exception.message) }
        }
    }

    private fun resetAnalyzer() {
        if (_uiState.value.useVendorAnalyzer && vendorAnalyzer != null) {
            _uiState.update { it.copy(useVendorAnalyzer = false) }
            imageAnalysis?.clearAnalyzer()
            imageAnalyzer = ZxingQRCodeAnalyzer(onSuccess, onFailure, onCropArea = ::updateCropArea)
            imageAnalysis?.setAnalyzer(analysisExecutor, imageAnalyzer!!)
        }
    }

    fun startCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val context = getApplication<Application>()
        val cameraProviderFuture = try {
            ProcessCameraProvider.getInstance(context)
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.message, isLoading = false) }
            return
        }

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                cameraPreview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalyzer = if (_uiState.value.useVendorAnalyzer && vendorAnalyzer != null) {
                    vendorAnalyzer
                } else {
                    ZxingQRCodeAnalyzer(onSuccess, onFailure, onCropArea = ::updateCropArea)
                }
                imageAnalysis?.setAnalyzer(analysisExecutor, imageAnalyzer!!)

                bindCamera(lifecycleOwner)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message, isLoading = false) }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCamera(lifecycleOwner: LifecycleOwner) {
        val provider = cameraProvider ?: return
        val preview = cameraPreview ?: return
        val analysis = imageAnalysis ?: return

        provider.unbindAll()

        val cameraSelector = if (_uiState.value.useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                analysis,
            )
            val maxZoom = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f
            _uiState.update { it.copy(maxZoomRatio = maxZoom, zoomRatio = 1f) }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.message, isLoading = false) }
        }
    }

    fun onPreviewStreamStateChanged(isStreaming: Boolean) {
        if (isStreaming) {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun toggleFrontCamera(lifecycleOwner: LifecycleOwner) {
        _uiState.update { it.copy(useFrontCamera = !it.useFrontCamera) }
        bindCamera(lifecycleOwner)
    }

    fun toggleTorch() {
        val newTorchState = !_uiState.value.torchEnabled
        camera?.cameraControl?.enableTorch(newTorchState)
        _uiState.update { it.copy(torchEnabled = newTorchState) }
    }

    fun setZoomRatio(ratio: Float) {
        val clampedRatio = ratio.coerceIn(1f, _uiState.value.maxZoomRatio)
        camera?.cameraControl?.setZoomRatio(clampedRatio)
        _uiState.update { it.copy(zoomRatio = clampedRatio) }
    }

    fun toggleVendorAnalyzer() {
        if (vendorAnalyzer == null) return

        val newState = !_uiState.value.useVendorAnalyzer
        _uiState.update { it.copy(useVendorAnalyzer = newState) }
        updateCropArea(null)

        imageAnalysis?.clearAnalyzer()
        imageAnalyzer = if (newState) {
            vendorAnalyzer
        } else {
            ZxingQRCodeAnalyzer(onSuccess, onFailure, onCropArea = ::updateCropArea)
        }
        imageAnalysis?.setAnalyzer(analysisExecutor, imageAnalyzer!!)
    }

    fun dismissError() {
        showingError.set(false)
        _uiState.update { it.copy(errorMessage = null) }
        imageAnalysis?.setAnalyzer(analysisExecutor, imageAnalyzer!!)
    }

    fun clearResult() {
        resetQRSState()
        _uiState.update { it.copy(result = null, cropArea = null) }
    }

    private fun extractQRSPayload(content: String): ByteArray? {
        val base64Data = when {
            content.startsWith("http") && content.contains("#") -> {
                content.substring(content.indexOf('#') + 1)
            }
            else -> content
        }

        val decoded = try {
            Base64.decode(base64Data, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.d(TAG, "Base64 decode failed: ${e.message}")
            return null
        }

        Log.d(TAG, "Decoded size: ${decoded.size}")
        if (decoded.size < 20) {
            Log.d(TAG, "Too small: ${decoded.size} < 20")
            return null
        }

        val degree = decoded.readIntLE(0)
        Log.d(TAG, "degree: $degree")
        if (degree <= 0 || degree > 1000) {
            Log.d(TAG, "Invalid degree: $degree")
            return null
        }

        val headerSize = 4 + 4 * degree + 12
        if (decoded.size < headerSize) {
            Log.d(TAG, "Too small for header: ${decoded.size} < $headerSize")
            return null
        }

        val k = decoded.readIntLE(4 + 4 * degree)
        Log.d(TAG, "k: $k")
        if (k <= 0 || k > 100000) {
            Log.d(TAG, "Invalid k: $k")
            return null
        }

        Log.d(TAG, "Valid QRS block detected!")
        return decoded
    }

    private fun handleQRSFrame(payload: ByteArray) = synchronized(qrsLock) {
        Log.d(TAG, "Processing QRS frame")
        if (qrsDecoder == null) {
            qrsDecoder = QRSDecoder()
            _uiState.update { it.copy(qrsMode = true) }
            (imageAnalyzer as? ZxingQRCodeAnalyzer)?.qrsMode = true
            Log.d(TAG, "Created new QRSDecoder, entered QRS mode")
        }

        val progress = qrsDecoder!!.processFrame(payload)
        Log.d(TAG, "processFrame result: $progress")
        if (progress == null) {
            Log.d(TAG, "processFrame returned null!")
            return@synchronized
        }

        _uiState.update {
            it.copy(qrsProgress = Pair(progress.decodedBlocks, progress.totalBlocks))
        }

        if (progress.isComplete) {
            if (progress.error != null) {
                Log.e(TAG, "QRS complete with error: ${progress.error}, retrying...")
                resetQRSState()
            } else if (progress.data != null) {
                imageAnalysis?.clearAnalyzer()
                Log.d(TAG, "QRS complete! Data size: ${progress.data.size}")
                importQRSProfile(progress.data)
            }
        }
    }

    fun resetQRSState() = synchronized(qrsLock) {
        qrsDecoder?.reset()
        qrsDecoder = null
        (imageAnalyzer as? ZxingQRCodeAnalyzer)?.qrsMode = false
        _uiState.update { it.copy(qrsMode = false, qrsProgress = null) }
    }

    private fun parseQRSFileFormat(data: ByteArray): ByteArray {
        var offset = 0

        val metaLength = ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
        offset += 4

        offset += metaLength

        val dataLength = ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
        offset += 4

        return data.copyOfRange(offset, offset + dataLength)
    }

    private fun importQRSProfile(data: ByteArray) {
        try {
            val actualData = try {
                parseQRSFileFormat(data)
            } catch (e: Exception) {
                Log.d(TAG, "Not official QRS format, using raw data")
                data
            }
            Log.d(TAG, "Decoding profile content, size: ${actualData.size}")
            Libbox.decodeProfileContent(actualData)
            _uiState.update { it.copy(result = QRScanResult.QRSData(actualData)) }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.message) }
            resetQRSState()
            imageAnalysis?.setAnalyzer(analysisExecutor, imageAnalyzer!!)
        }
    }

    private fun processQRCode(value: String): Boolean {
        try {
            val uri = Uri.parse(value)
            if (uri.scheme != "sing-box" || uri.host != "import-remote-profile") {
                _uiState.update { it.copy(errorMessage = "Not a valid sing-box remote profile URI") }
                imageAnalysis?.setAnalyzer(analysisExecutor, imageAnalyzer!!)
                return false
            }
            Libbox.parseRemoteProfileImportLink(uri.toString())
            _uiState.update { it.copy(result = QRScanResult.RemoteProfile(uri)) }
            return true
        } catch (e: Exception) {
            if (showingError.compareAndSet(false, true)) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
        return false
    }

    override fun onCleared() {
        super.onCleared()
        analysisExecutor.shutdown()
    }
}
