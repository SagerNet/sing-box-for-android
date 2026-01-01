package io.nekohasekai.sfa.vendor

import android.graphics.Bitmap
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

// kanged from: https://github.com/G00fY2/quickie/blob/main/quickie/src/main/kotlin/io/github/g00fy2/quickie/QRCodeAnalyzer.kt

class MLKitQRCodeAnalyzer(
    private val onSuccess: ((String) -> Unit),
    private val onFailure: ((Exception) -> Unit),
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
            image.close()
            return
        }

        failureOccurred = false
        barcodeScanner.process(image.toInputImage())
            .addOnSuccessListener { codes ->
                val rawValue = codes.firstOrNull()?.rawValue
                if (rawValue != null) {
                    onSuccess(rawValue)
                    image.close()
                } else {
                    tryInvertedScan(image)
                }
            }
            .addOnFailureListener {
                failureOccurred = true
                failureTimestamp = System.currentTimeMillis()
                onFailure(it)
                image.close()
            }
    }

    private fun tryInvertedScan(image: ImageProxy) {
        val inverted = image.toInvertedBitmap()
        barcodeScanner.process(InputImage.fromBitmap(inverted, image.imageInfo.rotationDegrees))
            .addOnSuccessListener { codes ->
                codes.firstOrNull()?.rawValue?.let { onSuccess(it) }
            }
            .addOnCompleteListener {
                inverted.recycle()
                image.close()
            }
    }

    private fun ImageProxy.toInvertedBitmap(): Bitmap {
        val yPlane = planes[0]
        val yBuffer = yPlane.buffer.duplicate()
        val rowStride = yPlane.rowStride
        val width = width
        val height = height
        val size = width * height

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = pixelBuffer?.takeIf { it.size >= size } ?: IntArray(size).also { pixelBuffer = it }

        for (row in 0 until height) {
            yBuffer.position(row * rowStride)
            for (col in 0 until width) {
                val y = 255 - (yBuffer.get().toInt() and 0xFF)
                pixels[row * width + col] = (0xFF shl 24) or (y shl 16) or (y shl 8) or y
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    @ExperimentalGetImage
    @Suppress("UnsafeCallOnNullableType")
    private fun ImageProxy.toInputImage() = InputImage.fromMediaImage(image!!, imageInfo.rotationDegrees)
}
