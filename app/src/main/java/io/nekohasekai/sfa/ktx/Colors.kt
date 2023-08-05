package io.nekohasekai.sfa.ktx

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors


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
fun colorForURLTestDelay(context: Context, urlTestDelay: Int): Int {
    if (urlTestDelay <= 0) {
        return Color.GRAY
    }
    val colorRes =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && context.resources.configuration.isNightModeActive) {
            if (urlTestDelay <= 800) {
                android.R.color.holo_green_dark
            } else if (urlTestDelay <= 1500) {
                android.R.color.holo_orange_dark
            } else {
                android.R.color.holo_red_dark
            }
        } else {
            if (urlTestDelay <= 800) {
                android.R.color.holo_green_light
            } else if (urlTestDelay <= 1500) {
                android.R.color.holo_orange_light
            } else {
                android.R.color.holo_red_light
            }
        }
    return MaterialColors.harmonizeWithPrimary(context, ContextCompat.getColor(context, colorRes))
}