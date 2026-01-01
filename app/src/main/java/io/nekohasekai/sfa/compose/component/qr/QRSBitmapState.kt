package io.nekohasekai.sfa.compose.component.qr

import android.graphics.Bitmap
import io.nekohasekai.sfa.compose.util.QRCodeGenerator
import io.nekohasekai.sfa.qrs.QRSEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

data class QRSGenerationState(
    val currentBitmap: Bitmap? = null,
    val currentFrameIndex: Int = 0,
    val generatedCount: Int = 0,
    val totalFrames: Int = 0,
    val isGenerating: Boolean = true,
)

class QRSBitmapGenerator(
    private val scope: CoroutineScope,
    private val frames: List<QRSEncoder.QRSFrame>,
    private val foregroundColor: Int,
    private val backgroundColor: Int,
    bufferSize: Int = 30,
) {
    private val _state = MutableStateFlow(QRSGenerationState(totalFrames = frames.size))
    val state: StateFlow<QRSGenerationState> = _state

    private val actualBufferSize = bufferSize.coerceAtMost(frames.size)
    private val bitmapBuffer = arrayOfNulls<Bitmap>(actualBufferSize)
    private var generationJob: Job? = null
    @Volatile
    private var currentFrameIndex = 0
    private var generatedUpTo = -1

    fun start() {
        if (frames.isEmpty()) {
            _state.value = _state.value.copy(isGenerating = false)
            return
        }

        generationJob = scope.launch {
            val firstBitmap = withContext(Dispatchers.Default) {
                QRCodeGenerator.generate(
                    content = frames[0].content,
                    foregroundColor = foregroundColor,
                    backgroundColor = backgroundColor,
                )
            }
            bitmapBuffer[0] = firstBitmap
            generatedUpTo = 0
            _state.value = _state.value.copy(
                currentBitmap = firstBitmap,
                generatedCount = 1,
                isGenerating = frames.size > 1,
            )

            for (i in 1 until frames.size) {
                yield()
                val bitmap = withContext(Dispatchers.Default) {
                    QRCodeGenerator.generate(
                        content = frames[i].content,
                        foregroundColor = foregroundColor,
                        backgroundColor = backgroundColor,
                    )
                }

                val bufferIndex = i % actualBufferSize
                val currentDisplayBufferIndex = currentFrameIndex % actualBufferSize
                if (bufferIndex != currentDisplayBufferIndex) {
                    bitmapBuffer[bufferIndex]?.recycle()
                }
                bitmapBuffer[bufferIndex] = bitmap
                generatedUpTo = i

                _state.value = _state.value.copy(
                    generatedCount = i + 1,
                    isGenerating = i < frames.size - 1,
                )
            }
        }
    }

    fun advanceFrame() {
        if (generatedUpTo < 0) return

        val nextIndex = (currentFrameIndex + 1) % frames.size
        if (nextIndex <= generatedUpTo || generatedUpTo == frames.size - 1) {
            currentFrameIndex = nextIndex
        }

        val bufferIndex = currentFrameIndex % actualBufferSize
        val bitmap = bitmapBuffer[bufferIndex]
        _state.value = _state.value.copy(
            currentBitmap = bitmap,
            currentFrameIndex = currentFrameIndex,
        )
    }

    fun cancel() {
        generationJob?.cancel()
    }
}
