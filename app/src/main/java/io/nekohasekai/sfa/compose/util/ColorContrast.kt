package io.nekohasekai.sfa.compose.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

fun Color.ensureContrast(background: Color, minRatio: Double = 4.5): Color {
    val foreground = toArgb()
    val backgroundArgb = background.toArgb()
    if (ColorUtils.calculateContrast(foreground, backgroundArgb) >= minRatio) {
        return this
    }
    val target = maxOf(Color.Black.toArgb(), Color.White.toArgb(), compareBy { ColorUtils.calculateContrast(it, backgroundArgb) })
    var low = 0f
    var high = 1f
    repeat(10) {
        val mid = (low + high) / 2
        if (ColorUtils.calculateContrast(ColorUtils.blendARGB(foreground, target, mid), backgroundArgb) >= minRatio) {
            high = mid
        } else {
            low = mid
        }
    }
    return Color(ColorUtils.blendARGB(foreground, target, high))
}
