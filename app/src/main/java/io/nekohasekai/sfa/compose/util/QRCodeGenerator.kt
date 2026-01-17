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

object QRCodeGenerator {

    private fun luminance(color: Int): Float {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    private fun adjustBrightness(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    fun ensureContrast(foreground: Int, background: Int, minRatio: Float = 4.5f): Int {
        val bgLum = luminance(background)
        var fg = foreground
        var fgLum = luminance(fg)

        var ratio = if (fgLum > bgLum) {
            (fgLum + 0.05f) / (bgLum + 0.05f)
        } else {
            (bgLum + 0.05f) / (fgLum + 0.05f)
        }

        if (ratio >= minRatio) return fg

        val shouldDarken = bgLum > 0.5f
        repeat(10) {
            fg = if (shouldDarken) {
                adjustBrightness(fg, 0.8f)
            } else {
                adjustBrightness(fg, 1.25f)
            }
            fgLum = luminance(fg)
            ratio = if (fgLum > bgLum) {
                (fgLum + 0.05f) / (bgLum + 0.05f)
            } else {
                (bgLum + 0.05f) / (fgLum + 0.05f)
            }
            if (ratio >= minRatio) return fg
        }
        return fg
    }

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
    fun rememberPrimaryBitmap(content: String, size: Int = 512, backgroundColor: Int): Bitmap {
        val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
        val safeColor = remember(primaryColor, backgroundColor) {
            ensureContrast(primaryColor, backgroundColor)
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
