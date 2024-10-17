package io.nekohasekai.sfa.constant

import android.content.Context
import androidx.annotation.StringRes
import io.nekohasekai.sfa.R

enum class EnabledType(val boolValue: Boolean, @StringRes var stringId: Int) {
    Enabled(true, R.string.enabled),
    Disabled(false, R.string.disabled);

    companion object {
        fun from(value: Boolean): EnabledType {
            return if (value) Enabled else Disabled
        }

        fun valueOf(value: String, context: Context): EnabledType = when (value) {
            context.getString(Enabled.stringId) -> Enabled
            context.getString(Disabled.stringId) -> Disabled
            else -> throw IllegalArgumentException()
        }
    }
}