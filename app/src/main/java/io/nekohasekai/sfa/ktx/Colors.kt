package io.nekohasekai.sfa.ktx

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt


@ColorInt
fun Context.getAttrColor(
    @AttrRes attrColor: Int,
    typedValue: TypedValue = TypedValue(),
    resolveRefs: Boolean = true
): Int {
    theme.resolveAttribute(attrColor, typedValue, resolveRefs)
    return typedValue.data
}

@ColorInt
fun colorForURLTestDelay(urlTestDelay: Int): Int {
    return if (urlTestDelay <= 0) {
        Color.GRAY
    } else if (urlTestDelay <= 800) {
        Color.GREEN
    } else if (urlTestDelay <= 1500) {
        Color.YELLOW
    } else {
        Color.RED
    }
}