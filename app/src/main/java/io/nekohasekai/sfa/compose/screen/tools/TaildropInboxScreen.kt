package io.nekohasekai.sfa.compose.screen.tools

import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.base.GlobalEventBus
import io.nekohasekai.sfa.compose.base.SelectableMessageDialog
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.appcompat.R as AppCompatR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaildropInboxScreen(
    navController: NavController,
    viewModel: TaildropViewModel,
    endpointTag: String,
) {
    val state by viewModel.uiState.collectAsState()
    val sendSessions by TaildropSendManager.sessions.collectAsState()
    val sending = sendSessions.filter { it.endpointTag == endpointTag }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val failedSession = sending.firstOrNull { it.errorMessage != null }
    if (failedSession != null) {
        SelectableMessageDialog(
            title = stringResource(R.string.error_title),
            message = failedSession.errorMessage.orEmpty(),
            onDismiss = { TaildropSendManager.dismiss(failedSession.id) },
        )
    }

    var menuExpanded by remember { mutableStateOf(false) }

    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.taildrop)) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back),
                    )
                }
            },
            actions = {
                if (state.files.isNotEmpty()) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.content_description_more_options),
                        )
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
                                viewModel.deleteAll(endpointTag, state.files.map { it.name })
                            },
                        )
                    }
                }
            },
        )
    }

    DisposableEffect(endpointTag) {
        viewModel.subscribe(endpointTag)
        onDispose { viewModel.cancel() }
    }

    LaunchedEffect(endpointTag, state.files) {
        if (state.hasUpdate && state.files.isNotEmpty()) {
            viewModel.markInboxRead(endpointTag)
        }
    }

    var pendingSaveName by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { destination ->
        val name = pendingSaveName
        pendingSaveName = null
        if (destination == null || name == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val source = TaildropFiles.materialize(endpointTag, name)
                    context.contentResolver.openOutputStream(destination)?.use { sink ->
                        source.inputStream().use { input -> input.copyTo(sink) }
                    } ?: error("failed to open ${destination.path}")
                }
            } catch (e: Exception) {
                GlobalEventBus.emit(UiEvent.ErrorMessage(e.message ?: e.toString()))
            }
        }
    }

    fun openFile(name: String, share: Boolean) {
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    val file = TaildropFiles.materialize(endpointTag, name)
                    FileProvider.getUriForFile(context, "${context.packageName}.cache", file)
                }
                val mimeType = TaildropFiles.mimeType(name)
                if (share) {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND)
                                .setType(mimeType)
                                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                .putExtra(Intent.EXTRA_STREAM, uri),
                            context.getString(AppCompatR.string.abc_shareactionprovider_share_with),
                        ),
                    )
                } else {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, mimeType)
                            .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            } catch (e: Exception) {
                GlobalEventBus.emit(UiEvent.ErrorMessage(e.message ?: e.toString()))
            }
        }
    }

    if (!state.hasUpdate) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val isEmpty = state.files.isEmpty() && state.receiving.isEmpty() && sending.isEmpty()
    LaunchedEffect(isEmpty) {
        if (isEmpty) {
            navController.navigateUp()
        }
    }
    if (isEmpty) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        )
        return
    }

    val scaffoldPadding = LocalScaffoldPadding.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(scaffoldPadding)
            .padding(vertical = 8.dp),
    ) {
        if (sending.isNotEmpty()) {
            SectionHeader(stringResource(R.string.taildrop_send))
            sending.forEachIndexed { sessionIndex, session ->
                if (sessionIndex > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column {
                        session.files.forEachIndexed { index, file ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                            TaildropTransferItem(
                                name = file.name,
                                peerLabel = stringResource(R.string.taildrop_to, session.peerName),
                                transferredBytes = file.sentBytes,
                                totalBytes = file.size,
                                onCancel = {
                                    if (session.finished) {
                                        TaildropSendManager.dismiss(session.id)
                                    } else {
                                        TaildropSendManager.cancel(session.id)
                                    }
                                },
                                finished = session.finished,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (state.receiving.isNotEmpty()) {
            SectionHeader(stringResource(R.string.taildrop_receive))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column {
                    state.receiving.forEachIndexed { index, file ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        TaildropTransferItem(
                            name = file.name,
                            peerLabel = if (file.senderName.isEmpty()) {
                                ""
                            } else {
                                stringResource(R.string.taildrop_from, file.senderName)
                            },
                            transferredBytes = file.receivedBytes,
                            totalBytes = file.size,
                            onCancel = { viewModel.cancelReceiving(endpointTag, file.senderID, file.name) },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (state.files.isNotEmpty()) {
            SectionHeader(stringResource(R.string.taildrop_files))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column {
                    state.files.forEachIndexed { index, file ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        FileItem(
                            file = file,
                            onSave = {
                                pendingSaveName = file.name
                                saveLauncher.launch(file.name)
                            },
                            onOpen = { openFile(file.name, share = false) },
                            onShare = { openFile(file.name, share = true) },
                            onDelete = { viewModel.delete(endpointTag, file.name) },
                            modifier = when {
                                state.files.size == 1 -> Modifier.clip(RoundedCornerShape(12.dp))
                                index == 0 -> Modifier.clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                index == state.files.lastIndex ->
                                    Modifier.clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                                else -> Modifier
                            },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
}

@Composable
private fun FileItem(
    file: TaildropFileData,
    onSave: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                fileSubtitle(file),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.content_description_more_options),
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.taildrop_save)) },
                    leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onSave()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.taildrop_share)) },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onShare()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.taildrop_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun fileSubtitle(file: TaildropFileData): String {
    val parts = mutableListOf(Libbox.formatBytes(file.size))
    if (file.senderName.isNotEmpty()) {
        parts.add(stringResource(R.string.taildrop_from, file.senderName))
    }
    if (file.modifiedAt > 0) {
        parts.add(
            DateUtils.getRelativeTimeSpanString(
                file.modifiedAt * 1000,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                0,
            ).toString(),
        )
    }
    return parts.joinToString(" · ")
}
