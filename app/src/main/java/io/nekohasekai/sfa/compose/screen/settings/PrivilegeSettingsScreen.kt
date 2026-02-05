package io.nekohasekai.sfa.compose.screen.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.AppShortcut
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.base.GlobalEventBus
import io.nekohasekai.sfa.compose.base.SelectableMessageDialog
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.utils.DetectionResult
import io.nekohasekai.sfa.utils.HookModuleUpdateNotifier
import io.nekohasekai.sfa.utils.HookStatusClient
import io.nekohasekai.sfa.utils.PrivilegeSettingsClient
import io.nekohasekai.sfa.utils.VpnDetectionTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivilegeSettingsScreen(navController: NavController, serviceStatus: Status = Status.Stopped) {
    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.privilege_settings)) },
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
    val systemHookStatus by HookStatusClient.status.collectAsState()
    var privilegeSettingsEnabled by remember { mutableStateOf(Settings.privilegeSettingsEnabled) }

    var showTestDialog by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<DetectionResult?>(null) }
    var isTestRunning by remember { mutableStateOf(false) }
    var interfaceRenameEnabled by remember { mutableStateOf(Settings.privilegeSettingsInterfaceRenameEnabled) }
    var interfacePrefix by remember { mutableStateOf(Settings.privilegeSettingsInterfacePrefix) }
    var showInterfacePrefixDialog by remember { mutableStateOf(false) }
    var interfacePrefixInput by remember { mutableStateOf(interfacePrefix) }
    var showExportProgressDialog by remember { mutableStateOf(false) }
    var exportCancelled by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var showExportSuccessDialog by remember { mutableStateOf(false) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var showMessageDialog by remember { mutableStateOf(false) }
    var messageDialogTitle by remember { mutableStateOf("") }
    var messageDialogMessage by remember { mutableStateOf("") }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val file = exportedFile
        if (uri != null && file != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        FileInputStream(file).use { input ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("PrivilegeSettings", "Failed to save file", e)
                }
            }
        }
        showExportSuccessDialog = false
        exportedFile = null
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        HookStatusClient.refresh()
    }

    val hasPendingDowngrade = HookModuleUpdateNotifier.isDowngrade(systemHookStatus)
    val hasPendingUpdate = HookModuleUpdateNotifier.isUpgrade(systemHookStatus)
    val hasPendingChange = hasPendingDowngrade || hasPendingUpdate
    androidx.compose.runtime.LaunchedEffect(systemHookStatus) {
        HookModuleUpdateNotifier.maybeNotify(context, systemHookStatus)
    }

    if (showTestDialog) {
        SelfTestDialog(
            isRunning = isTestRunning,
            result = testResult,
            onDismiss = {
                showTestDialog = false
                testResult = null
            },
        )
    }
    if (showInterfacePrefixDialog) {
        AlertDialog(
            onDismissRequest = { showInterfacePrefixDialog = false },
            title = { Text(stringResource(R.string.privilege_settings_interface_rename_title)) },
            text = {
                OutlinedTextField(
                    value = interfacePrefixInput,
                    onValueChange = { interfacePrefixInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.privilege_settings_interface_prefix)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = interfacePrefixInput.trim()
                        val filtered = buildString(trimmed.length) {
                            for (ch in trimmed) {
                                if (ch.isLetterOrDigit() || ch == '_') {
                                    append(ch)
                                }
                            }
                        }
                        val normalized = if (filtered.isEmpty()) "en" else filtered
                        interfacePrefix = normalized
                        Settings.privilegeSettingsInterfacePrefix = normalized
                        showInterfacePrefixDialog = false
                        scope.launch {
                            val failure =
                                withContext(Dispatchers.IO) {
                                    PrivilegeSettingsClient.sync()
                                }
                            if (failure != null) {
                                messageDialogTitle = context.getString(R.string.error_title)
                                messageDialogMessage = failure.message ?: failure.toString()
                                showMessageDialog = true
                            } else if (serviceStatus == Status.Started) {
                                GlobalEventBus.tryEmit(UiEvent.RestartToTakeEffect)
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showInterfacePrefixDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (showMessageDialog) {
        SelectableMessageDialog(
            title = messageDialogTitle,
            message = messageDialogMessage,
            onDismiss = { showMessageDialog = false },
        )
    }
    if (showExportProgressDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.privilege_settings_export_debug)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        if (exportError != null) {
                            exportError!!
                        } else {
                            stringResource(R.string.exporting)
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (exportError != null) {
                            showExportProgressDialog = false
                            exportError = null
                        } else {
                            exportCancelled = true
                            showExportProgressDialog = false
                        }
                    },
                ) {
                    Text(stringResource(if (exportError != null) R.string.ok else android.R.string.cancel))
                }
            },
        )
    }
    if (showExportSuccessDialog && exportedFile != null) {
        AlertDialog(
            onDismissRequest = {
                showExportSuccessDialog = false
                exportedFile = null
            },
            title = { Text(stringResource(R.string.privilege_settings_export_debug_complete)) },
            text = {
                val file = exportedFile
                if (file != null) {
                    Text(stringResource(R.string.privilege_settings_export_debug_message, Libbox.formatBytes(file.length())))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val file = exportedFile ?: return@TextButton
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.cache",
                            file,
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                        showExportSuccessDialog = false
                        exportedFile = null
                    },
                ) {
                    Text(stringResource(R.string.menu_share))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val file = exportedFile ?: return@TextButton
                        saveFileLauncher.launch(file.name)
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        val isLsposedActivated = systemHookStatus?.active == true
        val showLogs = isLsposedActivated && !hasPendingChange
        val showExportDebug = showLogs
        val statusShape =
            if (showLogs || hasPendingChange) {
                RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            } else {
                RoundedCornerShape(12.dp)
            }
        val logItemShape =
            if (showExportDebug) {
                RoundedCornerShape(0.dp)
            } else {
                RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
            }
        val statusLabel =
            when {
                hasPendingDowngrade -> stringResource(R.string.lsposed_module_pending_downgrade)
                hasPendingUpdate -> stringResource(R.string.lsposed_module_pending_update)
                isLsposedActivated -> stringResource(R.string.lsposed_module_activated)
                else -> stringResource(R.string.lsposed_module_not_activated)
            }
        val statusIcon =
            when {
                hasPendingDowngrade -> Icons.Outlined.WarningAmber
                hasPendingUpdate -> Icons.Outlined.WarningAmber
                isLsposedActivated -> Icons.Outlined.CheckBox
                else -> Icons.Outlined.WarningAmber
            }
        val statusIconTint =
            when {
                hasPendingDowngrade -> MaterialTheme.colorScheme.error
                hasPendingUpdate -> Color(0xFFFFC107)
                isLsposedActivated -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            }

        Text(
            text = stringResource(R.string.privilege_module_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp),
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            statusLabel,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = null,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusIconTint,
                        )
                    },
                    modifier = Modifier.clip(statusShape),
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )
                if (showLogs) {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.privilege_settings_view_logs),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.ViewModule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier =
                        Modifier
                            .clip(logItemShape)
                            .clickable {
                                navController.navigate("settings/privilege/logs")
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                }
                if (showExportDebug) {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.privilege_settings_export_debug),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier =
                        Modifier
                            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                            .clickable {
                                val exportBase = File(context.cacheDir, "debug")
                                if (!exportBase.exists()) {
                                    exportBase.mkdirs()
                                }
                                val timestamp =
                                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                val outZip = File(exportBase, "sing-box-lsposed-debug-$timestamp.zip")
                                exportCancelled = false
                                exportError = null
                                showExportProgressDialog = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        PrivilegeSettingsClient.exportDebugInfo(outZip.absolutePath)
                                    }
                                    if (exportCancelled) {
                                        outZip.delete()
                                        return@launch
                                    }
                                    showExportProgressDialog = false
                                    val failure = result.error
                                    if (failure == null) {
                                        exportedFile = outZip
                                        showExportSuccessDialog = true
                                    } else {
                                        messageDialogTitle = context.getString(R.string.error_title)
                                        messageDialogMessage = context.getString(
                                            R.string.privilege_settings_export_debug_failed,
                                            failure,
                                        )
                                        showMessageDialog = true
                                    }
                                }
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                }
                if (hasPendingChange) {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.privilege_module_restart_action),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier =
                        Modifier
                            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                            .clickable {
                                scope.launch {
                                    val failure = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val process = Runtime.getRuntime().exec(
                                                arrayOf(
                                                    "su",
                                                    "-c",
                                                    "/system/bin/svc power reboot || /system/bin/reboot",
                                                ),
                                            )
                                            val error = process.errorStream.bufferedReader().use { it.readText().trim() }
                                            process.inputStream.close()
                                            process.outputStream.close()
                                            process.errorStream.close()
                                            val code = process.waitFor()
                                            if (code == 0) {
                                                null
                                            } else {
                                                error.ifBlank { "exit=$code" }
                                            }
                                        }.getOrElse { it.message ?: "unknown" }
                                    }
                                    if (failure != null) {
                                        val message =
                                            if (failure == "unknown" || failure.startsWith("exit=")) {
                                                context.getString(R.string.root_access_required)
                                            } else {
                                                context.getString(R.string.privilege_module_restart_failed, failure)
                                            }
                                        messageDialogTitle = context.getString(R.string.error_title)
                                        messageDialogMessage = message
                                        showMessageDialog = true
                                    }
                                }
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.privilege_settings_hide_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 8.dp),
        )

        val privilegeControlsEnabled = isLsposedActivated && !hasPendingChange
        val hasManageItem = privilegeSettingsEnabled

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val disabledAlpha = 0.38f
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.enabled),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        Text(
                            stringResource(R.string.privilege_settings_hide_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.FilterAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = privilegeSettingsEnabled,
                            onCheckedChange = { checked ->
                                privilegeSettingsEnabled = checked
                                if (checked && !interfaceRenameEnabled) {
                                    interfaceRenameEnabled = true
                                }
                                scope.launch {
                                    val failure =
                                        withContext(Dispatchers.IO) {
                                            Settings.privilegeSettingsEnabled = checked
                                            if (checked) {
                                                Settings.privilegeSettingsInterfaceRenameEnabled = true
                                            }
                                            PrivilegeSettingsClient.sync()
                                        }
                                    if (failure != null) {
                                        messageDialogTitle = context.getString(R.string.error_title)
                                        messageDialogMessage = failure.message ?: failure.toString()
                                        showMessageDialog = true
                                    } else if (checked && serviceStatus == Status.Started) {
                                        GlobalEventBus.tryEmit(UiEvent.RestartToTakeEffect)
                                    }
                                }
                            },
                            enabled = privilegeControlsEnabled,
                        )
                    },
                    modifier = Modifier
                        .alpha(if (privilegeControlsEnabled) 1f else disabledAlpha)
                        .clip(
                            if (hasManageItem) {
                                RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                            } else {
                                RoundedCornerShape(12.dp)
                            },
                        ),
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )

                val manageEnabled = privilegeControlsEnabled && privilegeSettingsEnabled
                if (hasManageItem) {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.privilege_settings_hide_manage),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.AppShortcut,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier
                            .alpha(if (manageEnabled) 1f else disabledAlpha)
                            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                            .clickable(enabled = manageEnabled) {
                                navController.navigate("settings/privilege/manage")
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.privilege_settings_interface_rename_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 8.dp),
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val renameControlsEnabled = isLsposedActivated && !hasPendingChange
                val disabledAlpha = 0.38f
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.enabled),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.FilterAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = interfaceRenameEnabled,
                            onCheckedChange = { checked ->
                                interfaceRenameEnabled = checked
                                scope.launch {
                                    val failure =
                                        withContext(Dispatchers.IO) {
                                            Settings.privilegeSettingsInterfaceRenameEnabled = checked
                                            PrivilegeSettingsClient.sync()
                                        }
                                    if (failure != null) {
                                        messageDialogTitle = context.getString(R.string.error_title)
                                        messageDialogMessage = failure.message ?: failure.toString()
                                        showMessageDialog = true
                                    } else if (serviceStatus == Status.Started) {
                                        GlobalEventBus.tryEmit(UiEvent.RestartToTakeEffect)
                                    }
                                }
                            },
                            enabled = renameControlsEnabled,
                        )
                    },
                    modifier = Modifier
                        .alpha(if (renameControlsEnabled) 1f else disabledAlpha)
                        .clip(
                            if (interfaceRenameEnabled) {
                                RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                            } else {
                                RoundedCornerShape(12.dp)
                            },
                        ),
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )

                if (interfaceRenameEnabled) {
                    val prefixEnabled = renameControlsEnabled
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.privilege_settings_interface_prefix),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                interfacePrefix,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier
                            .alpha(if (prefixEnabled) 1f else disabledAlpha)
                            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                            .clickable(enabled = prefixEnabled) {
                                interfacePrefixInput = interfacePrefix
                                showInterfacePrefixDialog = true
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.privilege_settings_vpn_detection_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 8.dp),
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val testEnabled = !hasPendingChange
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.privilege_settings_hide_test),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier
                        .alpha(if (testEnabled) 1f else 0.38f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = testEnabled) {
                            showTestDialog = true
                            isTestRunning = true
                            testResult = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    VpnDetectionTest.runDetection(context)
                                }
                                testResult = result
                                isTestRunning = false
                            }
                        },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SelfTestDialog(isRunning: Boolean, result: DetectionResult?, onDismiss: () -> Unit) {
    val notDetectedText = stringResource(R.string.privilege_settings_hide_test_not_detected)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.privilege_settings_hide_test_result))
        },
        text = {
            if (isRunning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.privilege_settings_hide_test_running),
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            } else if (result != null) {
                val frameworkInterfacesText = result.frameworkInterfaces
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", ")
                val frameworkProxyText = result.httpProxy?.takeIf { it.isNotBlank() }
                val frameworkExtraLines = listOfNotNull(frameworkInterfacesText, frameworkProxyText)
                val nativeInterfacesText = result.nativeInterfaces
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", ")
                val nativeExtraLines = listOfNotNull(nativeInterfacesText)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        Text(
                            text = "Framework",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (result.frameworkDetected.isEmpty()) {
                            Text(
                                text = notDetectedText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF4CAF50),
                            )
                        } else {
                            Text(
                                text = result.frameworkDetected.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            if (frameworkExtraLines.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    frameworkExtraLines.forEach { line ->
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Column {
                        Text(
                            text = "Native",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!result.nativeDetected) {
                            Text(
                                text = notDetectedText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF4CAF50),
                            )
                        } else {
                            Text(
                                text = "getifaddrs()",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            if (nativeExtraLines.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    nativeExtraLines.forEach { line ->
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
