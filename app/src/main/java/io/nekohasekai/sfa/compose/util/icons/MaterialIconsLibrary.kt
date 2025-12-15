package io.nekohasekai.sfa.compose.util.icons

import androidx.compose.ui.graphics.vector.ImageVector
import io.nekohasekai.sfa.compose.util.ProfileIcon

/**
 * Complete Material Icons Library following Google's official taxonomy
 * Icons are organized into categories as defined by Material Design guidelines
 *
 * Categories based on https://fonts.google.com/icons taxonomy:
 * - Action: User actions and common UI operations
 * - Alert: Warnings, errors, and notifications
 * - AV (Audio/Video): Media controls and playback
 * - Communication: Messaging, calls, emails
 * - Content: Content creation and management
 * - Device: Device-specific icons and features
 * - Editor: Text and content editing
 * - File: File types and operations
 * - Hardware: Physical hardware and peripherals
 * - Image: Image editing and gallery
 * - Maps: Location and navigation
 * - Navigation: App navigation and menus
 * - Notification: Alerts and status updates
 * - Places: Locations and venues
 * - Social: Social media and sharing
 * - Toggle: Switches and toggles
 */
object MaterialIconsLibrary {
    /**
     * All icon categories following Google's Material Design taxonomy
     */
    val categories: List<IconCategory> =
        listOf(
            IconCategory("Action", ActionIcons.icons),
            IconCategory("Alert", AlertIcons.icons),
            IconCategory("Audio & Video", AVIcons.icons),
            IconCategory("Communication", CommunicationIcons.icons),
            IconCategory("Content", ContentIcons.icons),
            IconCategory("Device", DeviceIcons.icons),
            IconCategory("Editor", EditorIcons.icons),
            IconCategory("File", FileIcons.icons),
            IconCategory("Hardware", HardwareIcons.icons),
            IconCategory("Image", ImageIcons.icons),
            IconCategory("Maps", MapsIcons.icons),
            IconCategory("Navigation", NavigationIcons.icons),
            IconCategory("Notification", NotificationIcons.icons),
            IconCategory("Places", PlacesIcons.icons),
            IconCategory("Social", SocialIcons.icons),
            IconCategory("Toggle", ToggleIcons.icons),
        )

    /**
     * Get all icons from all categories
     */
    fun getAllIcons(): List<ProfileIcon> {
        return categories.flatMap { it.icons }
    }

    /**
     * Get an icon by its ID
     */
    fun getIconById(id: String): ImageVector? {
        return getAllIcons().find { it.id == id }?.icon
    }

    /**
     * Get the category name for a given icon ID
     */
    fun getCategoryForIcon(iconId: String): String? {
        categories.forEach { category ->
            if (category.icons.any { it.id == iconId }) {
                return category.name
            }
        }
        return null
    }

    /**
     * Search icons by query (searches in both ID and label)
     */
    fun searchIcons(query: String): List<ProfileIcon> {
        if (query.isBlank()) return getAllIcons()

        val lowercaseQuery = query.lowercase()
        return getAllIcons().filter {
            it.id.contains(lowercaseQuery) ||
                it.label.lowercase().contains(lowercaseQuery)
        }
    }

    /**
     * Get icons by category name
     */
    fun getIconsByCategory(categoryName: String): List<ProfileIcon> {
        return categories.find { it.name.equals(categoryName, ignoreCase = true) }?.icons
            ?: emptyList()
    }

    /**
     * Get total number of icons in the library
     */
    fun getTotalIconCount(): Int {
        return categories.sumOf { it.icons.size }
    }

    /**
     * Get category names
     */
    fun getCategoryNames(): List<String> {
        return categories.map { it.name }
    }
}
