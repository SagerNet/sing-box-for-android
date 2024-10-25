package io.nekohasekai.sfa.vendor

import android.util.Log
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
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )

    @Volatile
    private var failureOccurred = false
    private var failureTimestamp = 0L

    @ExperimentalGetImage
    override fun analyze(image: ImageProxy) {
        if (image.image == null) return

        val nowMills = System.currentTimeMillis()
        if (failureOccurred && nowMills - failureTimestamp < 5000L) {
            failureTimestamp = nowMills
            Log.d("MLKitQRCodeAnalyzer", "throttled analysis since error occurred in previous pass")
            image.close()
            return
        }

        failureOccurred = false
        barcodeScanner.process(image.toInputImage())
            .addOnSuccessListener { codes ->
                if (codes.isNotEmpty()) {
                    val rawValue = codes.firstOrNull()?.rawValue
                    if (rawValue != null) {
                        Log.d("MLKitQRCodeAnalyzer", "barcode decode success: $rawValue")
                        onSuccess(rawValue)
                    }
                }
            }
            .addOnFailureListener {
                failureOccurred = true
                failureTimestamp = System.currentTimeMillis()
                onFailure(it)
            }
            .addOnCompleteListener {
                image.close()
            }
    }

    @ExperimentalGetImage
    @Suppress("UnsafeCallOnNullableType")
    private fun ImageProxy.toInputImage() =
        InputImage.fromMediaImage(image!!, imageInfo.rotationDegrees)
}