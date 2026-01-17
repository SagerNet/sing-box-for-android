package io.nekohasekai.sfa.compose.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.ui.graphics.vector.ImageVector
import io.nekohasekai.sfa.compose.util.icons.MaterialIconsLibrary

data class ProfileIcon(val id: String, val icon: ImageVector, val label: String)

object ProfileIcons {
    // Use the complete Material Icons library with all available icons
    val availableIcons: List<ProfileIcon>
        get() = MaterialIconsLibrary.getAllIcons()

    fun getIconById(id: String?): ImageVector? {
        if (id == null) return null
        return MaterialIconsLibrary.getIconById(id)
    }

    fun getDefaultIconForType(isRemote: Boolean): ImageVector {
        // Use the same default icon for all profile types
        return Icons.AutoMirrored.Default.InsertDriveFile
    }

    fun getCategoryForIcon(iconId: String): String? = MaterialIconsLibrary.getCategoryForIcon(iconId)

    fun searchIcons(query: String): List<ProfileIcon> = MaterialIconsLibrary.searchIcons(query)

    fun getCategories() = MaterialIconsLibrary.categories
}
