package io.nekohasekai.sfa.constant

import android.content.Context
import io.nekohasekai.sfa.R

enum class EnabledType(val boolValue: Boolean) {
    Enabled(true), Disabled(false);

    fun getString(context: Context): String {
        return when (this) {
            Enabled -> context.getString(R.string.enabled)
            Disabled -> context.getString(R.string.disabled)
        }
    }


    companion object {
        fun from(value: Boolean): EnabledType {
            return if (value) Enabled else Disabled
        }

        fun valueOf(context: Context, value: String): EnabledType {
            return when (value) {
                context.getString(R.string.enabled) -> Enabled
                context.getString(R.string.disabled) -> Disabled
                else -> Disabled
            }
        }
    }
}