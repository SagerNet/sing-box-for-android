package io.nekohasekai.sfa.compose.screen.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.OOMReportManager
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.base.rememberApplyServiceChangeNotifier
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private val memoryLimitOptions = listOf(50, 100, 200, 300, 500, 750, 1024)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OOMReportListScreen(
    navController: NavController,
    serviceStatus: Status = Status.Stopped,
) {
    val reports by OOMReportManager.reports.collectAsState()
    var isLoading by remember { mutableStateOf(true) }
    var menuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val notifyApplyChange = rememberApplyServiceChangeNotifier(serviceStatus)

    var oomKillerEnabled by remember { mutableStateOf(Settings.oomKillerEnabled) }
    var oomMemoryLimitMB by remember { mutableIntStateOf(Settings.oomMemoryLimitMB) }
    var oomKillerKillConnections by remember { mutableStateOf(!Settings.oomKillerDisabled) }
    var showMemoryLimitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        OOMReportManager.refresh()
        val storedLimit = Settings.oomMemoryLimitMB
        if (!memoryLimitOptions.contains(storedLimit)) {
            oomMemoryLimitMB = memoryLimitOptions.first()
            Settings.oomMemoryLimitMB = oomMemoryLimitMB
        }
        isLoading = false
    }

    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.oom_report)) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
            actions = {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.oom_report_fetch)) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Memory, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            if (serviceStatus != Status.Started) {
                                errorMessage =
                                    Application.application.getString(R.string.service_not_started)
                            } else {
                                scope.launch {
                                    val failure =
                                        withContext(Dispatchers.IO) {
                                            runCatching {
                                                Libbox.newStandaloneCommandClient().triggerOOMReport()
                                            }.exceptionOrNull()
                                        }
                                    if (failure != null) {
                                        errorMessage = failure.message ?: failure.toString()
                                    } else {
                                        delay(1000)
                                        withContext(Dispatchers.IO) {
                                            OOMReportManager.refresh()
                                        }
                                    }
                                }
                            }
                        },
                    )
                    if (reports.isNotEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.report_delete_all),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.DeleteSweep,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                scope.launch { OOMReportManager.deleteAll() }
                            },
                        )
                    }
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                // Reports section
                Text(
                    stringResource(R.string.report_section_reports),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                )
                if (reports.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(R.string.report_empty),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Column {
                            reports.forEachIndexed { index, report ->
                                val shape = when {
                                    reports.size == 1 -> RoundedCornerShape(12.dp)
                                    index == 0 -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                                    index == reports.lastIndex -> RoundedCornerShape(
                                        bottomStart = 12.dp,
                                        bottomEnd = 12.dp,
                                    )
                                    else -> RoundedCornerShape(0.dp)
                                }
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            formatDate(report.date),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (report.isRead) FontWeight.Normal else FontWeight.SemiBold,
                                        )
                                    },
                                    leadingContent = if (!report.isRead) {
                                        {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier
                                        .clip(shape)
                                        .clickable {
                                            navController.navigate("tools/oom_report/${report.id}")
                                        },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.oom_report_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                )

                // Settings section
                Text(
                    stringResource(R.string.title_settings),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 8.dp),
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
                            headlineContent = {
                                Text(
                                    stringResource(R.string.oom_report_enable_memory_limit),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            supportingContent = {
                                Text(stringResource(R.string.oom_report_enable_memory_limit_description))
                            },
                            trailingContent = {
                                Switch(
                                    checked = oomKillerEnabled,
                                    onCheckedChange = { checked ->
                                        oomKillerEnabled = checked
                                        scope.launch(Dispatchers.IO) {
                                            Settings.oomKillerEnabled = checked
                                            Application.application.reloadSetupOptions()
                                            withContext(Dispatchers.Main) {
                                                notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Restart)
                                            }
                                        }
                                    },
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        AnimatedVisibility(visible = oomKillerEnabled) {
                            Column {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            stringResource(R.string.oom_report_memory_limit),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    },
                                    supportingContent = {
                                        Text(Libbox.formatMemoryBytes(oomMemoryLimitMB.toLong() * 1024L * 1024L))
                                    },
                                    modifier = Modifier.clickable { showMemoryLimitDialog = true },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            stringResource(R.string.oom_report_kill_connections),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    },
                                    supportingContent = {
                                        Text(stringResource(R.string.oom_report_kill_connections_description))
                                    },
                                    trailingContent = {
                                        Switch(
                                            checked = oomKillerKillConnections,
                                            onCheckedChange = { checked ->
                                                oomKillerKillConnections = checked
                                                scope.launch(Dispatchers.IO) {
                                                    Settings.oomKillerDisabled = !checked
                                                    Application.application.reloadSetupOptions()
                                                    withContext(Dispatchers.Main) {
                                                        notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Restart)
                                                    }
                                                }
                                            },
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            text = { Text(message) },
        )
    }

    if (showMemoryLimitDialog) {
        AlertDialog(
            onDismissRequest = { showMemoryLimitDialog = false },
            title = { Text(stringResource(R.string.oom_report_memory_limit)) },
            text = {
                Column {
                    memoryLimitOptions.forEach { value ->
                        ListItem(
                            headlineContent = {
                                Text(Libbox.formatMemoryBytes(value.toLong() * 1024L * 1024L))
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = value == oomMemoryLimitMB,
                                    onClick = null,
                                )
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    oomMemoryLimitMB = value
                                    showMemoryLimitDialog = false
                                    scope.launch(Dispatchers.IO) {
                                        Settings.oomMemoryLimitMB = value
                                        Application.application.reloadSetupOptions()
                                        withContext(Dispatchers.Main) {
                                            notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Restart)
                                        }
                                    }
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            },
            confirmButton = {},
        )
    }
}

private fun formatDate(date: Date): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(date)
