package io.nekohasekai.sfa.constant

enum class EnabledType(val boolValue: Boolean) {
    Enabled(true), Disabled(false);

    companion object {
        fun from(value: Boolean): EnabledType {
            return if (value) Enabled else Disabled
        }
    }
}