package io.nekohasekai.sfa.compose.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.ui.graphics.Color as ComposeColor

object QRCodeGenerator {

    @Composable
    fun rememberBitmap(content: String, size: Int = 512): Bitmap {
        val isDarkTheme = isSystemInDarkTheme()
        return remember(content, isDarkTheme) {
            generate(
                content = content,
                size = size,
                foregroundColor = if (isDarkTheme) Color.WHITE else Color.BLACK,
                backgroundColor = Color.TRANSPARENT,
            )
        }
    }

    @Composable
    fun rememberPrimaryBitmap(content: String, size: Int = 512, backgroundColor: ComposeColor): Bitmap {
        val primaryColor = MaterialTheme.colorScheme.primary
        val safeColor = remember(primaryColor, backgroundColor) {
            primaryColor.ensureContrast(backgroundColor).toArgb()
        }
        return remember(content, safeColor) {
            generate(
                content = content,
                size = size,
                foregroundColor = safeColor,
                backgroundColor = Color.TRANSPARENT,
            )
        }
    }

    fun generate(content: String, size: Int = 512, foregroundColor: Int = Color.BLACK, backgroundColor: Int = Color.WHITE): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)

        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] =
                    if (bitMatrix.get(x, y)) {
                        foregroundColor
                    } else {
                        backgroundColor
                    }
            }
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
}
