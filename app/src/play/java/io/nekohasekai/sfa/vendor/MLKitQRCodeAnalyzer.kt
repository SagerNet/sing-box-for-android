package io.nekohasekai.sfa.vendor

import android.graphics.Bitmap
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.nekohasekai.sfa.ui.profile.QRCodeCropArea
import io.nekohasekai.sfa.ui.profile.QRCodeSmartCrop

// kanged from: https://github.com/G00fY2/quickie/blob/main/quickie/src/main/kotlin/io/github/g00fy2/quickie/QRCodeAnalyzer.kt

class MLKitQRCodeAnalyzer(
    private val onSuccess: ((String) -> Unit),
    private val onFailure: ((Exception) -> Unit),
    private val onCropArea: ((QRCodeCropArea?) -> Unit)? = null,
) : ImageAnalysis.Analyzer {
    private val barcodeScanner =
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )

    @Volatile
    private var failureOccurred = false
    private var failureTimestamp = 0L

    private var yDataBuffer: ByteArray? = null
    private var pixelBuffer: IntArray? = null

    @ExperimentalGetImage
    override fun analyze(image: ImageProxy) {
        if (image.image == null) {
            image.close()
            return
        }

        val nowMills = System.currentTimeMillis()
        if (failureOccurred && nowMills - failureTimestamp < 5000L) {
            failureTimestamp = nowMills
            onCropArea?.invoke(null)
            image.close()
            return
        }

        failureOccurred = false
        barcodeScanner.process(image.toInputImage())
            .addOnSuccessListener { codes ->
                val rawValue = codes.firstOrNull()?.rawValue
                if (rawValue != null) {
                    onCropArea?.invoke(null)
                    onSuccess(rawValue)
                    image.close()
                } else {
                    val yData = image.toYData().copyOf()
                    val rotation = image.imageInfo.rotationDegrees
                    val cropArea = QRCodeSmartCrop.findCropArea(yData, image.width, image.height, rotation)
                    onCropArea?.invoke(cropArea)
                    if (cropArea == null) {
                        tryInvertedScan(yData, image.width, image.height, rotation) { image.close() }
                    } else {
                        val cropWidth = cropArea.right - cropArea.left
                        val cropHeight = cropArea.bottom - cropArea.top
                        val bitmap = toLumaBitmap(
                            yData,
                            image.width,
                            cropArea.left,
                            cropArea.top,
                            cropWidth,
                            cropHeight,
                            invert = false,
                        )
                        barcodeScanner.process(InputImage.fromBitmap(bitmap, rotation))
                            .addOnSuccessListener { cropCodes ->
                                val cropValue = cropCodes.firstOrNull()?.rawValue
                                if (cropValue != null) {
                                    onSuccess(cropValue)
                                    image.close()
                                } else {
                                    tryInvertedScan(yData, image.width, image.height, rotation) { image.close() }
                                }
                            }
                            .addOnFailureListener {
                                tryInvertedScan(yData, image.width, image.height, rotation) { image.close() }
                            }
                            .addOnCompleteListener {
                                bitmap.recycle()
                            }
                    }
                }
            }
            .addOnFailureListener {
                failureOccurred = true
                failureTimestamp = System.currentTimeMillis()
                onCropArea?.invoke(null)
                onFailure(it)
                image.close()
            }
    }

    private fun tryInvertedScan(
        yData: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        onComplete: () -> Unit,
    ) {
        val inverted = toLumaBitmap(yData, width, 0, 0, width, height, invert = true)
        barcodeScanner.process(InputImage.fromBitmap(inverted, rotationDegrees))
            .addOnSuccessListener { codes ->
                codes.firstOrNull()?.rawValue?.let { onSuccess(it) }
            }
            .addOnCompleteListener {
                inverted.recycle()
                onComplete()
            }
    }

    private fun ImageProxy.toYData(): ByteArray {
        val yPlane = planes[0]
        val yBuffer = yPlane.buffer.duplicate()
        val rowStride = yPlane.rowStride
        val width = width
        val height = height
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

    private fun toLumaBitmap(
        yData: ByteArray,
        srcWidth: Int,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        invert: Boolean,
    ): Bitmap {
        val size = width * height
        val pixels = pixelBuffer?.takeIf { it.size >= size } ?: IntArray(size).also { pixelBuffer = it }

        var index = 0
        for (row in 0 until height) {
            val rowOffset = (top + row) * srcWidth + left
            for (col in 0 until width) {
                val y = yData[rowOffset + col].toInt() and 0xFF
                val luma = if (invert) 255 - y else y
                pixels[index++] = (0xFF shl 24) or (luma shl 16) or (luma shl 8) or luma
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    @ExperimentalGetImage
    @Suppress("UnsafeCallOnNullableType")
    private fun ImageProxy.toInputImage() = InputImage.fromMediaImage(image!!, imageInfo.rotationDegrees)
}
