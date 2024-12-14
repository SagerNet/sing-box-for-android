package io.nekohasekai.sfa.constant

import android.content.Context
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.Settings

enum class PerAppProxyUpdateType {
    Disabled, Select, Deselect;

    fun value() = when (this) {
        Disabled -> Settings.PER_APP_PROXY_DISABLED
        Select -> Settings.PER_APP_PROXY_INCLUDE
        Deselect -> Settings.PER_APP_PROXY_EXCLUDE
    }

    fun getString(context: Context): String {
        return when (this) {
            Disabled -> context.getString(R.string.disabled)
            Select -> context.getString(R.string.action_select)
            Deselect -> context.getString(R.string.action_deselect)
        }
    }

    companion object {
        fun valueOf(value: Int): PerAppProxyUpdateType = when (value) {
            Settings.PER_APP_PROXY_DISABLED -> Disabled
            Settings.PER_APP_PROXY_INCLUDE -> Select
            Settings.PER_APP_PROXY_EXCLUDE -> Deselect
            else -> throw IllegalArgumentException()
        }

        fun valueOf(context: Context, value: String): PerAppProxyUpdateType {
            return when (value) {
                context.getString(R.string.disabled) -> Disabled
                context.getString(R.string.action_select) -> Select
                context.getString(R.string.action_deselect) -> Deselect
                else -> Disabled
            }
        }
    }
}