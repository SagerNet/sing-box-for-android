package io.nekohasekai.sfa.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import androidx.preference.Preference
import io.nekohasekai.sfa.ktx.getAttrColor

class Preference : Preference {

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        icon?.setTint(context.getAttrColor(com.google.android.material.R.attr.colorOnSurface))
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(
        context,
        attrs,
        defStyleAttr,
        0
    )

    constructor(context: Context, attrs: AttributeSet?) : this(
        context, attrs, getAttr(
            context, androidx.preference.R.attr.preferenceStyle,
            android.R.attr.preferenceStyle
        )
    )

    companion object {
        private fun getAttr(context: Context, attr: Int, fallbackAttr: Int): Int {
            val value = TypedValue()
            context.theme.resolveAttribute(attr, value, true)
            if (value.resourceId != 0) {
                return attr
            }
            return fallbackAttr
        }
    }

}