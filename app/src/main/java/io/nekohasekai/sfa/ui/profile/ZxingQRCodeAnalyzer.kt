package io.nekohasekai.sfa.ui.profile

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.qrcode.QRCodeReader

class ZxingQRCodeAnalyzer(
    private val onSuccess: ((String) -> Unit),
    private val onFailure: ((Exception) -> Unit),
) : ImageAnalysis.Analyzer {

    private val qrCodeReader = QRCodeReader()
    override fun analyze(image: ImageProxy) {
        try {
            val bitmap = image.toBitmap()
            val intArray = IntArray(bitmap.getWidth() * bitmap.getHeight())
            bitmap.getPixels(
                intArray,
                0,
                bitmap.getWidth(),
                0,
                0,
                bitmap.getWidth(),
                bitmap.getHeight()
            )
            val source = RGBLuminanceSource(bitmap.getWidth(), bitmap.getHeight(), intArray)
            val result = try {
                qrCodeReader.decode(BinaryBitmap(GlobalHistogramBinarizer(source)))
            } catch (e: NotFoundException) {
                try {
                    qrCodeReader.decode(BinaryBitmap(GlobalHistogramBinarizer(source.invert())))
                } catch (ignore: NotFoundException) {
                    return
                }
            }
            Log.d("ZxingQRCodeAnalyzer", "barcode decode success: ${result.text}")
            onSuccess(result.text)
        } catch (e: Exception) {
            onFailure(e)
        } finally {
            image.close()
        }
    }
}