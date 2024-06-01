package io.nekohasekai.sfa.constant

import android.content.Context
import androidx.annotation.StringRes
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.Settings

enum class PerAppProxyUpdateType(@StringRes var stringId: Int) {
    Disabled(R.string.disabled),
    Select(R.string.action_select),
    Deselect(R.string.action_deselect);

    fun value() = when (this) {
        Disabled -> Settings.PER_APP_PROXY_DISABLED
        Select -> Settings.PER_APP_PROXY_INCLUDE
        Deselect -> Settings.PER_APP_PROXY_EXCLUDE
    }

    companion object {
        fun from(value: Int): PerAppProxyUpdateType = when (value) {
            Settings.PER_APP_PROXY_DISABLED -> Disabled
            Settings.PER_APP_PROXY_INCLUDE -> Select
            Settings.PER_APP_PROXY_EXCLUDE -> Deselect
            else -> throw IllegalArgumentException()
        }

        fun valueOf(value: String, context: Context): PerAppProxyUpdateType = when (value) {
            context.getString(Disabled.stringId) -> Disabled
            context.getString(Select.stringId) -> Select
            context.getString(Deselect.stringId) -> Deselect
            else -> throw IllegalArgumentException()
        }
    }
}