package io.nekohasekai.sfa.compose.util.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import io.nekohasekai.sfa.compose.util.ProfileIcon

/**
 * Alert category icons - Warnings, errors, and notifications
 * Based on Google's Material Design Icons taxonomy
 */
object AlertIcons {
    val icons =
        listOf(
            ProfileIcon("add_alert", Icons.Filled.AddAlert, "Add Alert"),
            ProfileIcon("auto_delete", Icons.Filled.AutoDelete, "Auto Delete"),
            ProfileIcon("error", Icons.Filled.Error, "Error"),
            ProfileIcon("error_outline", Icons.Filled.ErrorOutline, "Error Outline"),
            ProfileIcon("notification_important", Icons.Filled.NotificationImportant, "Important"),
            ProfileIcon("warning", Icons.Filled.Warning, "Warning"),
            ProfileIcon("warning_amber", Icons.Filled.WarningAmber, "Warning Amber"),
        )
}
