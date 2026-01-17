package io.nekohasekai.sfa.update

enum class UpdateTrack {
    STABLE,
    BETA,
    ;

    companion object {
        fun fromString(value: String): UpdateTrack = when (value.lowercase()) {
            "beta" -> BETA
            else -> STABLE
        }
    }
}
