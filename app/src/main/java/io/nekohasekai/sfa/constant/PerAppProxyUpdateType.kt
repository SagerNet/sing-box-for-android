package io.nekohasekai.sfa.constant

import io.nekohasekai.sfa.database.Settings

enum class PerAppProxyUpdateType {
    Disabled, Select, Deselect;

    fun value() = when (this) {
        Disabled -> Settings.PER_APP_PROXY_DISABLED
        Select -> Settings.PER_APP_PROXY_INCLUDE
        Deselect -> Settings.PER_APP_PROXY_EXCLUDE
    }

    companion object {
        fun valueOf(value: Int): PerAppProxyUpdateType = when (value) {
            Settings.PER_APP_PROXY_DISABLED -> Disabled
            Settings.PER_APP_PROXY_INCLUDE -> Select
            Settings.PER_APP_PROXY_EXCLUDE -> Deselect
            else -> throw IllegalArgumentException()
        }
    }
}