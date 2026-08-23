package io.nekohasekai.sfa.compose.screen.tools

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.PowerReportManager
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.base.rememberApplyServiceChangeNotifier
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerReportListScreen(
    navController: NavController,
    serviceStatus: Status = Status.Stopped,
) {
    val reports by PowerReportManager.reports.collectAsState()
    var isLoading by remember { mutableStateOf(true) }
    var menuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val notifyApplyChange = rememberApplyServiceChangeNotifier(serviceStatus)

    var powerReportEnabled by remember { mutableStateOf(Settings.powerReportEnabled) }

    LaunchedEffect(Unit) {
        PowerReportManager.refresh()
        isLoading = false
    }

    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.power_report)) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
            actions = {
                if (reports.isNotEmpty()) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
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
                                scope.launch { PowerReportManager.deleteAll() }
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
                // Settings section
                Text(
                    stringResource(R.string.title_settings),
                    style = MaterialTheme.typography.labelLarge,
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
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.power_report_enable),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(stringResource(R.string.power_report_enable_description))
                        },
                        trailingContent = {
                            Switch(
                                checked = powerReportEnabled,
                                onCheckedChange = { checked ->
                                    powerReportEnabled = checked
                                    scope.launch(Dispatchers.IO) {
                                        Settings.powerReportEnabled = checked
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

                // Reports section
                Text(
                    stringResource(R.string.report_section_reports),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 8.dp),
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
                                            formatPowerReportDate(report.date),
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
                                            navController.navigate("tools/power_report/${report.id}")
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
}

internal fun formatPowerReportDate(date: Date): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(date)
