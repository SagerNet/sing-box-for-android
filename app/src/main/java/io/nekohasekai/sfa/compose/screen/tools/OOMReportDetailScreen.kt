package io.nekohasekai.sfa.compose.screen.tools

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.OOMReport
import io.nekohasekai.sfa.bg.OOMReportFile
import io.nekohasekai.sfa.bg.OOMReportManager
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OOMReportDetailScreen(navController: NavController, reportId: String) {
    val reports by OOMReportManager.reports.collectAsState()
    val report = reports.find { it.id == reportId }
    var files by remember { mutableStateOf<List<OOMReportFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var shareMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(report) {
        if (report != null) {
            withContext(Dispatchers.IO) {
                files = OOMReportManager.availableFiles(report)
            }
            OOMReportManager.markAsRead(report)
        }
        isLoading = false
    }

    val title = if (report != null) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(report.date)
    } else {
        reportId
    }

    val hasConfig = report != null && OOMReportManager.hasConfigFile(report)

    fun shareReport(includeConfig: Boolean) {
        val currentReport = report ?: return
        scope.launch {
            val zipFile = OOMReportManager.createZipArchive(currentReport, includeConfig)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.cache", zipFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, null))
        }
    }

    OverrideTopBar {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
            actions = {
                if (!isLoading && files.isNotEmpty()) {
                    if (hasConfig) {
                        IconButton(onClick = { shareMenuExpanded = true }) {
                            Icon(Icons.Default.Share, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = shareMenuExpanded,
                            onDismissRequest = { shareMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.report_share)) },
                                onClick = {
                                    shareMenuExpanded = false
                                    shareReport(includeConfig = false)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.report_share_with_config)) },
                                onClick = {
                                    shareMenuExpanded = false
                                    shareReport(includeConfig = true)
                                },
                            )
                        }
                    } else {
                        IconButton(onClick = { shareReport(includeConfig = false) }) {
                            Icon(Icons.Default.Share, contentDescription = null)
                        }
                    }
                    IconButton(onClick = {
                        scope.launch {
                            if (report != null) {
                                OOMReportManager.delete(report)
                            }
                            navController.navigateUp()
                        }
                    }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
        )
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else if (files.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.report_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.report_section_files),
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
                Column {
                    files.forEachIndexed { index, file ->
                        val shape = when {
                            files.size == 1 -> RoundedCornerShape(12.dp)
                            index == 0 -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                            index == files.lastIndex -> RoundedCornerShape(
                                bottomStart = 12.dp,
                                bottomEnd = 12.dp,
                            )
                            else -> RoundedCornerShape(0.dp)
                        }
                        val icon = when (file.kind) {
                            OOMReportFile.Kind.METADATA -> Icons.Default.DataObject
                            OOMReportFile.Kind.CONFIG -> Icons.Outlined.Settings
                            OOMReportFile.Kind.PROFILE -> Icons.Default.Terminal
                        }
                        ListItem(
                            headlineContent = {
                                Text(
                                    file.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            modifier = Modifier
                                .clip(shape)
                                .then(
                                    if (file.kind != OOMReportFile.Kind.PROFILE) {
                                        Modifier.clickable {
                                            if (file.kind == OOMReportFile.Kind.METADATA) {
                                                navController.navigate("tools/oom_report/$reportId/metadata")
                                            } else {
                                                navController.navigate("tools/oom_report/$reportId/file/${file.kind.name}")
                                            }
                                        }
                                    } else {
                                        Modifier
                                    },
                                ),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OOMReportMetadataScreen(navController: NavController, reportId: String) {
    val reports by OOMReportManager.reports.collectAsState()
    val report = reports.find { it.id == reportId }
    var entries by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(report) {
        if (report != null) {
            withContext(Dispatchers.IO) {
                entries = loadOOMMetadataEntries(report)
            }
        }
        isLoading = false
    }

    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.report_metadata)) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
        )
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.report_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column {
                        entries.forEachIndexed { index, (key, value) ->
                            val shape = when {
                                entries.size == 1 -> RoundedCornerShape(12.dp)
                                index == 0 -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                                index == entries.lastIndex -> RoundedCornerShape(
                                    bottomStart = 12.dp,
                                    bottomEnd = 12.dp,
                                )
                                else -> RoundedCornerShape(0.dp)
                            }
                            ListItem(
                                headlineContent = {
                                    Text(key, style = MaterialTheme.typography.bodyLarge)
                                },
                                supportingContent = { Text(value) },
                                modifier = Modifier.clip(shape),
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun loadOOMMetadataEntries(report: OOMReport): List<Pair<String, String>> {
    val metadataFile = OOMReportManager.availableFiles(report)
        .find { it.kind == OOMReportFile.Kind.METADATA } ?: return emptyList()
    val content = metadataFile.file.readText()
    val json = runCatching { JSONObject(content) }.getOrNull() ?: return emptyList()
    return json.keys().asSequence()
        .mapNotNull { key ->
            val value = json.optString(key, "")
            if (value.isNotBlank()) key to value else null
        }
        .toList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OOMReportFileContentScreen(navController: NavController, reportId: String, fileKind: String) {
    val reports by OOMReportManager.reports.collectAsState()
    val report = reports.find { it.id == reportId }
    var content by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf(fileKind) }
    var isLoading by remember { mutableStateOf(true) }

    val kind = runCatching { OOMReportFile.Kind.valueOf(fileKind) }.getOrNull()

    LaunchedEffect(report, kind) {
        if (report != null && kind != null) {
            withContext(Dispatchers.IO) {
                val file = OOMReportManager.availableFiles(report).find { it.kind == kind }
                if (file != null) {
                    displayName = file.displayName
                    content = OOMReportManager.loadFileContent(file)
                }
            }
        }
        isLoading = false
    }

    OverrideTopBar {
        TopAppBar(
            title = { Text(displayName) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
        )
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else if (content.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.report_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Text(
                        text = content,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}
