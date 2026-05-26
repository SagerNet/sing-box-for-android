package io.nekohasekai.sfa.compose.screen.settings

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.database.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TailscaleTerminalConfigScreen(navController: NavController) {
    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.tailscale_terminal_config)) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                }
            },
        )
    }

    var lightTheme by remember { mutableStateOf(Settings.tailscaleSSHLightTheme) }
    var darkTheme by remember { mutableStateOf(Settings.tailscaleSSHDarkTheme) }
    var fontFamily by remember { mutableStateOf(Settings.tailscaleSSHFontFamily) }
    var customFontPath by remember { mutableStateOf(Settings.tailscaleSSHCustomFontPath) }
    var fontSize by remember { mutableIntStateOf(Settings.tailscaleSSHFontSize) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.tailscale_terminal_color_theme),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.tailscale_terminal_light_theme)) },
                    trailingContent = {
                        Text(
                            lightTheme.ifBlank { "Default" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .clickable {
                            navController.navigate("settings/tailscale/theme_picker/${Uri.encode("false")}")
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.tailscale_terminal_dark_theme)) },
                    trailingContent = {
                        Text(
                            darkTheme.ifBlank { "Default" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                        .clickable {
                            navController.navigate("settings/tailscale/theme_picker/${Uri.encode("true")}")
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.tailscale_terminal_font_config),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.tailscale_terminal_font)) },
                    trailingContent = {
                        val fontDisplayName = when {
                            customFontPath.isNotBlank() -> java.io.File(customFontPath).nameWithoutExtension
                            fontFamily.isNotBlank() -> fontFamily
                            else -> stringResource(R.string.tailscale_terminal_font_default)
                        }
                        Text(
                            fontDisplayName,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .clickable {
                            navController.navigate("settings/tailscale/font_picker")
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.tailscale_terminal_font_size)) },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                onClick = {
                                    if (fontSize > 8) {
                                        fontSize--
                                        Settings.tailscaleSSHFontSize = fontSize
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(32.dp),
                            ) {
                                IconButton(onClick = {
                                    if (fontSize > 8) {
                                        fontSize--
                                        Settings.tailscaleSSHFontSize = fontSize
                                    }
                                }) {
                                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                            Text(
                                "$fontSize",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Surface(
                                onClick = {
                                    if (fontSize < 32) {
                                        fontSize++
                                        Settings.tailscaleSSHFontSize = fontSize
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(32.dp),
                            ) {
                                IconButton(onClick = {
                                    if (fontSize < 32) {
                                        fontSize++
                                        Settings.tailscaleSSHFontSize = fontSize
                                    }
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
