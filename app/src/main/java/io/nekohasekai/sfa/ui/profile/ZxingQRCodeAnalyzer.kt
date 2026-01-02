package io.nekohasekai.sfa.ui.profile

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

class ZxingQRCodeAnalyzer(
    private val onSuccess: ((String) -> Unit),
    private val onFailure: ((Exception) -> Unit),
    private val onCropArea: ((QRCodeCropArea?) -> Unit)? = null,
) : ImageAnalysis.Analyzer {
    private val qrCodeReader = QRCodeReader()
    private var yDataBuffer: ByteArray? = null

    var qrsMode: Boolean = false

    override fun analyze(image: ImageProxy) {
        try {
            val yData = image.toYUVData()
            val width = image.width
            val height = image.height
            val rotationDegrees = image.imageInfo.rotationDegrees
            val source = PlanarYUVLuminanceSource(yData, width, height, 0, 0, width, height, false)

            // Fast path: HybridBinarizer
            tryDecode(BinaryBitmap(HybridBinarizer(source)))?.let {
                onSuccess(it.text)
                return
            }

            val cropArea = QRCodeSmartCrop.findCropArea(yData, width, height, rotationDegrees)
            onCropArea?.invoke(cropArea)
            if (cropArea != null) {
                val cropWidth = cropArea.right - cropArea.left
                val cropHeight = cropArea.bottom - cropArea.top
                val smartSource = PlanarYUVLuminanceSource(
                    yData,
                    width,
                    height,
                    cropArea.left,
                    cropArea.top,
                    cropWidth,
                    cropHeight,
                    false,
                )
                tryDecode(BinaryBitmap(HybridBinarizer(smartSource)))?.let {
                    onSuccess(it.text)
                    return
                }
            }

            // In QRS mode, skip additional binarizer attempts for performance
            if (qrsMode) return

            // Inverted HybridBinarizer (uses ZXing's native invert)
            tryDecode(BinaryBitmap(HybridBinarizer(source.invert())))?.let {
                onSuccess(it.text)
                return
            }

            // GlobalHistogramBinarizer (normal)
            tryDecode(BinaryBitmap(GlobalHistogramBinarizer(source)))?.let {
                onSuccess(it.text)
                return
            }

            // GlobalHistogramBinarizer (inverted)
            tryDecode(BinaryBitmap(GlobalHistogramBinarizer(source.invert())))?.let {
                onSuccess(it.text)
                return
            }
        } catch (e: NotFoundException) {
            // No QR code found in frame, ignore
        } catch (e: ChecksumException) {
            // Checksum error, ignore
        } catch (e: FormatException) {
            // Format error, ignore
        } catch (e: Exception) {
            onFailure(e)
        } finally {
            qrCodeReader.reset()
            image.close()
        }
    }

    private fun ImageProxy.toYUVData(): ByteArray {
        val yPlane = planes[0]
        val yBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val size = width * height

        val yData = yDataBuffer?.takeIf { it.size >= size } ?: ByteArray(size).also { yDataBuffer = it }
        if (rowStride == width) {
            yBuffer.get(yData, 0, size)
        } else {
            for (row in 0 until height) {
                yBuffer.position(row * rowStride)
                yBuffer.get(yData, row * width, width)
            }
        }
        return yData
    }

    private fun tryDecode(bitmap: BinaryBitmap): Result? {
        return try {
            qrCodeReader.decode(bitmap)
        } catch (_: NotFoundException) {
            qrCodeReader.reset()
            null
        }
    }
}
