package io.nekohasekai.sfa.compose.util.icons

import io.nekohasekai.sfa.compose.util.ProfileIcon

/**
 * Represents a category of Material Icons following Google's official taxonomy
 */
data class IconCategory(val name: String, val icons: List<ProfileIcon>) {
    val size: Int get() = icons.size
}
