package io.nekohasekai.sfa.compose.screen.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.compose.component.UpdateAvailableDialog
import io.nekohasekai.sfa.update.UpdateCheckException
import io.nekohasekai.sfa.update.UpdateState
import io.nekohasekai.sfa.update.UpdateTrack
import io.nekohasekai.sfa.vendor.Vendor
import io.nekohasekai.sfa.utils.HookStatusClient
import io.nekohasekai.sfa.xposed.XposedActivation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(navController: NavController) {
    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.title_app_settings)) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back),
                    )
                }
            },
        )
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasUpdate by UpdateState.hasUpdate
    val updateInfo by UpdateState.updateInfo
    val isChecking by UpdateState.isChecking
    var showTrackDialog by remember { mutableStateOf(false) }
    var currentTrack by remember { mutableStateOf(Settings.updateTrack) }
    var checkUpdateEnabled by remember { mutableStateOf(Settings.checkUpdateEnabled) }
    var showErrorDialog by remember { mutableStateOf<Int?>(null) }

    var silentInstallEnabled by remember { mutableStateOf(Settings.silentInstallEnabled) }
    var silentInstallMethod by remember { mutableStateOf(Settings.silentInstallMethod) }
    val systemHookStatus by HookStatusClient.status.collectAsState()
    val xposedActivated = systemHookStatus?.active == true || XposedActivation.isActivated(context)
    var isMethodAvailable by remember { mutableStateOf(true) }
    var autoUpdateEnabled by remember { mutableStateOf(Settings.autoUpdateEnabled) }
    var showInstallMethodMenu by remember { mutableStateOf(false) }
    var isVerifyingMethod by remember { mutableStateOf(false) }
    var silentInstallError by remember { mutableStateOf<String?>(null) }

    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var showUpdateAvailableDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        HookStatusClient.refresh()
    }

    // Re-check method availability when returning from background (e.g., after granting permission)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        HookStatusClient.refresh()
        if (silentInstallEnabled) {
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    Vendor.verifySilentInstallMethod(silentInstallMethod)
                }
                isMethodAvailable = success
                silentInstallError = if (success) {
                    null
                } else when (silentInstallMethod) {
                    "PACKAGE_INSTALLER" -> context.getString(R.string.package_installer_not_available)
                    "SHIZUKU" -> context.getString(R.string.shizuku_not_available)
                    else -> context.getString(R.string.silent_install_verify_failed, silentInstallMethod)
                }
            }
        }
    }

    if (showTrackDialog) {
        UpdateTrackDialog(
            currentTrack = currentTrack,
            onTrackSelected = { track ->
                currentTrack = track
                scope.launch(Dispatchers.IO) {
                    Settings.updateTrack = track
                }
                showTrackDialog = false
            },
            onDismiss = { showTrackDialog = false },
        )
    }

    showErrorDialog?.let { messageRes ->
        AlertDialog(
            onDismissRequest = { showErrorDialog = null },
            title = { Text(stringResource(R.string.check_update)) },
            text = { Text(stringResource(messageRes)) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = null }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update)) },
            text = {
                Column {
                    if (downloadError != null) {
                        Text(
                            downloadError!!,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.downloading))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadJob?.cancel()
                        downloadJob = null
                        showDownloadDialog = false
                        downloadError = null
                    },
                ) {
                    Text(stringResource(if (downloadError != null) R.string.ok else android.R.string.cancel))
                }
            },
        )
    }

    if (showInstallMethodMenu) {
        InstallMethodDialog(
            currentMethod = silentInstallMethod,
            onMethodSelected = { method ->
                showInstallMethodMenu = false
                if (silentInstallMethod == method) return@InstallMethodDialog
                silentInstallMethod = method
                Settings.silentInstallMethod = method
                isVerifyingMethod = true
                scope.launch {
                    val success = withContext(Dispatchers.IO) {
                        Vendor.verifySilentInstallMethod(method)
                    }
                    isVerifyingMethod = false
                    isMethodAvailable = success
                    silentInstallError = if (success) {
                        null
                    } else when (method) {
                        "PACKAGE_INSTALLER" -> context.getString(R.string.package_installer_not_available)
                        "SHIZUKU" -> context.getString(R.string.shizuku_not_available)
                        else -> context.getString(R.string.silent_install_verify_failed, method)
                    }
                }
            },
            onDismiss = { showInstallMethodMenu = false },
        )
    }

    if (showUpdateAvailableDialog && updateInfo != null) {
        UpdateAvailableDialog(
            updateInfo = updateInfo!!,
            onDismiss = { showUpdateAvailableDialog = false },
            onUpdate = {
                showDownloadDialog = true
                downloadError = null
                downloadJob = scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            Vendor.downloadAndInstall(context, updateInfo!!.downloadUrl)
                        }
                        showDownloadDialog = false
                    } catch (e: Exception) {
                        downloadError = e.message
                    }
                }
            },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
    ) {
        // Info Card
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
        ) {
            Column {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.app_version_title),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        Text(
                            BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        if (hasUpdate) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("New") }
                        }
                    },
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(12.dp)),
                    colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.update_settings),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
        ) {
            Column {
                val updateItemCount =
                    run {
                        var count = 0
                        if (Vendor.supportsTrackSelection()) {
                            count += 1
                        }
                        count += 1
                        if (Vendor.supportsSilentInstall()) {
                            count += 1
                            if (silentInstallEnabled) {
                                count += 1
                                if (silentInstallMethod == "SHIZUKU" && !isMethodAvailable) {
                                    count += 1
                                }
                                if (silentInstallMethod == "PACKAGE_INSTALLER" && !isMethodAvailable) {
                                    count += 1
                                }
                            }
                        }
                        if (Vendor.supportsAutoUpdate()) {
                            count += 1
                        }
                        count
                    }

                var updateItemIndex = 0
                fun updateItemModifier(): Modifier {
                    val index = updateItemIndex++
                    return when {
                        updateItemCount == 1 -> Modifier.clip(RoundedCornerShape(12.dp))
                        index == 0 -> Modifier.clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        index == updateItemCount - 1 ->
                            Modifier.clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                        else -> Modifier
                    }
                }

                if (Vendor.supportsTrackSelection()) {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.update_track),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            val trackName = when (UpdateTrack.fromString(currentTrack)) {
                                UpdateTrack.STABLE -> stringResource(R.string.update_track_stable)
                                UpdateTrack.BETA -> stringResource(R.string.update_track_beta)
                            }
                            Text(trackName, style = MaterialTheme.typography.bodyMedium)
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.NewReleases,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier =
                            updateItemModifier()
                                .clickable { showTrackDialog = true },
                        colors =
                            ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                    )
                }

                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.check_update_automatic),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Autorenew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = checkUpdateEnabled,
                            onCheckedChange = { checked ->
                                checkUpdateEnabled = checked
                                scope.launch(Dispatchers.IO) {
                                    Settings.checkUpdateEnabled = checked
                                }
                            },
                        )
                    },
                    modifier = updateItemModifier(),
                    colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                )

                if (Vendor.supportsSilentInstall()) {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.silent_install),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                silentInstallError ?: stringResource(R.string.silent_install_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (silentInstallError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            if (isVerifyingMethod) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Switch(
                                    checked = silentInstallEnabled,
                                    onCheckedChange = { checked ->
                                        silentInstallEnabled = checked
                                        Settings.silentInstallEnabled = checked
                                        if (checked) {
                                            isVerifyingMethod = true
                                            scope.launch {
                                                val success = withContext(Dispatchers.IO) {
                                                    Vendor.verifySilentInstallMethod(silentInstallMethod)
                                                }
                                                isVerifyingMethod = false
                                                isMethodAvailable = success
                                                silentInstallError = if (success) {
                                                    null
                                                } else when (silentInstallMethod) {
                                                    "PACKAGE_INSTALLER" -> context.getString(R.string.package_installer_not_available)
                                                    "SHIZUKU" -> context.getString(R.string.shizuku_not_available)
                                                    else -> context.getString(R.string.silent_install_verify_failed, silentInstallMethod)
                                                }
                                            }
                                        } else {
                                            silentInstallError = null
                                        }
                                    },
                                )
                            }
                        },
                        modifier = updateItemModifier(),
                        colors =
                            ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                    )

                    if (silentInstallEnabled && !xposedActivated) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(R.string.silent_install_method),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            supportingContent = {
                                Text(
                                    when (silentInstallMethod) {
                                        "PACKAGE_INSTALLER" -> stringResource(R.string.install_method_package_installer)
                                        "SHIZUKU" -> stringResource(R.string.install_method_shizuku)
                                        "ROOT" -> stringResource(R.string.install_method_root)
                                        else -> silentInstallMethod
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            modifier =
                                updateItemModifier()
                                    .clickable { showInstallMethodMenu = true },
                            colors =
                                ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                ),
                        )

                        if (silentInstallMethod == "SHIZUKU" && !isMethodAvailable) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        stringResource(R.string.get_shizuku),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        stringResource(R.string.shizuku_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.Download,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                modifier =
                                    updateItemModifier()
                                        .clickable {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))
                                            context.startActivity(intent)
                                        },
                                colors =
                                    ListItemDefaults.colors(
                                        containerColor = Color.Transparent,
                                    ),
                            )
                        }

                        if (silentInstallMethod == "PACKAGE_INSTALLER" && !isMethodAvailable) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        stringResource(R.string.grant_install_permission),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        stringResource(R.string.grant_install_permission_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                modifier =
                                    updateItemModifier()
                                        .clickable {
                                            val intent = Intent(
                                                AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            context.startActivity(intent)
                                        },
                                colors =
                                    ListItemDefaults.colors(
                                        containerColor = Color.Transparent,
                                    ),
                            )
                        }
                    }
                }

                if (Vendor.supportsAutoUpdate()) {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.auto_update),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.auto_update_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.SystemUpdateAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = autoUpdateEnabled,
                                onCheckedChange = { checked ->
                                    autoUpdateEnabled = checked
                                    scope.launch(Dispatchers.IO) {
                                        Settings.autoUpdateEnabled = checked
                                        Vendor.scheduleAutoUpdate()
                                    }
                                },
                            )
                        },
                        modifier = updateItemModifier(),
                        colors =
                            ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Section
        Text(
            text = stringResource(R.string.action),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
        ) {
            Column {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.check_update),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .clip(
                                if (hasUpdate) {
                                    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                                } else {
                                    RoundedCornerShape(12.dp)
                                },
                            )
                            .clickable(enabled = !isChecking) {
                                if (hasUpdate && updateInfo != null) {
                                    showUpdateAvailableDialog = true
                                } else {
                                    scope.launch {
                                        UpdateState.isChecking.value = true
                                        withContext(Dispatchers.IO) {
                                            try {
                                                val result = Vendor.checkUpdateAsync()
                                                UpdateState.setUpdate(result)
                                                if (result == null) {
                                                    showErrorDialog = R.string.no_updates_available
                                                }
                                            } catch (_: UpdateCheckException.TrackNotSupported) {
                                                showErrorDialog = R.string.update_track_not_supported
                                            } catch (_: Exception) {
                                            }
                                        }
                                        UpdateState.isChecking.value = false
                                    }
                                }
                            },
                    colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                )

                if (BuildConfig.DEBUG && Vendor.supportsTrackSelection()) {
                    var isForceDownloading by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.force_download_install),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.SystemUpdateAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        trailingContent = {
                            if (isForceDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .clip(
                                    if (hasUpdate) {
                                        RoundedCornerShape(0.dp)
                                    } else {
                                        RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                                    },
                                )
                                .clickable(enabled = !isForceDownloading) {
                                    isForceDownloading = true
                                    scope.launch {
                                        try {
                                            val latestUpdate = withContext(Dispatchers.IO) {
                                                Vendor.forceGetLatestUpdate()
                                            }
                                            if (latestUpdate != null) {
                                                showDownloadDialog = true
                                                downloadError = null
                                                downloadJob = scope.launch {
                                                    try {
                                                        withContext(Dispatchers.IO) {
                                                            Vendor.downloadAndInstall(context, latestUpdate.downloadUrl)
                                                        }
                                                        showDownloadDialog = false
                                                    } catch (e: Exception) {
                                                        downloadError = e.message
                                                    }
                                                }
                                            } else {
                                                showErrorDialog = R.string.no_updates_available
                                            }
                                        } catch (_: UpdateCheckException.TrackNotSupported) {
                                            showErrorDialog = R.string.update_track_not_supported
                                        } catch (_: Exception) {
                                        }
                                        isForceDownloading = false
                                    }
                                },
                        colors =
                            ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                    )
                }

                if (hasUpdate && updateInfo != null) {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.update),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                updateInfo!!.versionName,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                                .clickable {
                                    showUpdateAvailableDialog = true
                                },
                        colors =
                            ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateTrackDialog(
    currentTrack: String,
    onTrackSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tracks = listOf(
        "stable" to stringResource(R.string.update_track_stable),
        "beta" to stringResource(R.string.update_track_beta),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_track)) },
        text = {
            Column {
                tracks.forEach { (value, label) ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onTrackSelected(value) }
                                .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentTrack == value,
                            onClick = { onTrackSelected(value) },
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun InstallMethodDialog(
    currentMethod: String,
    onMethodSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val methods = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add("PACKAGE_INSTALLER" to stringResource(R.string.install_method_package_installer))
        }
        add("SHIZUKU" to stringResource(R.string.install_method_shizuku))
        add("ROOT" to stringResource(R.string.install_method_root))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.silent_install_method)) },
        text = {
            Column {
                methods.forEach { (value, label) ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onMethodSelected(value) }
                                .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentMethod == value,
                            onClick = { onMethodSelected(value) },
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
