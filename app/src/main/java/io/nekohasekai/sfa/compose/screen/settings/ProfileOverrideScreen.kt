package io.nekohasekai.sfa.compose.screen.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.ui.profileoverride.PerAppProxyActivity
import io.nekohasekai.sfa.vendor.Vendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileOverrideScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var autoRedirect by remember { mutableStateOf(Settings.autoRedirect) }
    var perAppProxyEnabled by remember { mutableStateOf(Settings.perAppProxyEnabled) }
    var showPerAppProxyDialog by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
        ) {
            Column {
                // Auto Redirect
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.auto_redirect),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        Text(
                            stringResource(R.string.auto_redirect_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Route,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = autoRedirect,
                            onCheckedChange = { checked ->
                                if (checked && !autoRedirect) {
                                    scope.launch {
                                        val hasRoot =
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    val process = Runtime.getRuntime().exec("su -c id")
                                                    process.inputStream.close()
                                                    process.outputStream.close()
                                                    process.errorStream.close()
                                                    process.waitFor() == 0
                                                } catch (e: Exception) {
                                                    false
                                                }
                                            }
                                        if (hasRoot) {
                                            autoRedirect = true
                                            withContext(Dispatchers.IO) {
                                                Settings.autoRedirect = true
                                            }
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.root_access_required),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    }
                                } else if (!checked) {
                                    // Disabling doesn't need root check
                                    autoRedirect = false
                                    scope.launch(Dispatchers.IO) {
                                        Settings.autoRedirect = false
                                    }
                                }
                            },
                        )
                    },
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                )

                // Per-App Proxy
                val isPerAppProxyAvailable = Vendor.isPerAppProxyAvailable()
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.per_app_proxy),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent =
                        if (!isPerAppProxyAvailable) {
                            {
                                Text(
                                    text = context.getString(R.string.per_app_proxy_disabled_play_store),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        } else {
                            null
                        },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        if (isPerAppProxyAvailable) {
                            Switch(
                                checked = perAppProxyEnabled,
                                onCheckedChange = { checked ->
                                    perAppProxyEnabled = checked
                                    scope.launch(Dispatchers.IO) {
                                        Settings.perAppProxyEnabled = checked
                                    }
                                },
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                            .clickable {
                                if (isPerAppProxyAvailable) {
                                    // Launch the PerAppProxyActivity
                                    val intent = Intent(context, PerAppProxyActivity::class.java)
                                    context.startActivity(intent)
                                } else {
                                    // Show dialog explaining why it's disabled
                                    showPerAppProxyDialog = true
                                }
                            },
                    colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                )
            }
        }

        // Dialog for Per-app Proxy disabled message
        if (showPerAppProxyDialog) {
            AlertDialog(
                onDismissRequest = { showPerAppProxyDialog = false },
                title = {
                    Text(stringResource(R.string.unavailable))
                },
                text = {
                    Text(context.getString(R.string.per_app_proxy_disabled_message))
                },
                confirmButton = {
                    TextButton(
                        onClick = { showPerAppProxyDialog = false },
                    ) {
                        Text(context.getString(R.string.ok))
                    }
                },
            )
        }
    }
}
