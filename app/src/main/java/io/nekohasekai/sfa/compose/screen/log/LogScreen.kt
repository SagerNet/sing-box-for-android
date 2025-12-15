package io.nekohasekai.sfa.compose.screen.log

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.compose.ComposeActivity
import io.nekohasekai.sfa.constant.Status
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    serviceStatus: Status = Status.Stopped,
    viewModel: LogViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Handle back press in selection mode
    androidx.activity.compose.BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.clearSelection()
    }

    // Track if user is at the bottom of the list
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index == layoutInfo.totalItemsCount - 1
        }
    }

    // Re-enable auto-scroll when user reaches bottom
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) {
            viewModel.setAutoScrollEnabled(true)
        }
    }

    // Detect user manual scroll to disable auto-scroll
    LaunchedEffect(listState) {
        var dragStartIndex: Int? = null
        var dragStartOffset: Int? = null

        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    dragStartIndex = listState.firstVisibleItemIndex
                    dragStartOffset = listState.firstVisibleItemScrollOffset
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    if (dragStartIndex != null && dragStartOffset != null) {
                        val currentIndex = listState.firstVisibleItemIndex
                        val currentOffset = listState.firstVisibleItemScrollOffset

                        val scrolledUp =
                            if (dragStartIndex != currentIndex) {
                                dragStartIndex!! > currentIndex
                            } else {
                                dragStartOffset!! > currentOffset
                            }

                        if (scrolledUp) {
                            viewModel.setAutoScrollEnabled(false)
                        }

                        dragStartIndex = null
                        dragStartOffset = null
                    }
                }
            }
        }
    }

    // Handle scroll to bottom requests from ViewModel
    val scrollToBottomTrigger by viewModel.scrollToBottomTrigger.collectAsState()
    LaunchedEffect(scrollToBottomTrigger) {
        if (scrollToBottomTrigger > 0 && uiState.logs.isNotEmpty()) {
            listState.animateScrollToItem(uiState.logs.size - 1)
        }
    }

    // Update service status in ViewModel
    LaunchedEffect(serviceStatus) {
        viewModel.updateServiceStatus(serviceStatus)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Show selection mode bar
            if (uiState.isSelectionMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 2.dp,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.content_description_exit_selection_mode),
                                )
                            }
                            Text(
                                text =
                                    stringResource(
                                        R.string.selected_count,
                                        uiState.selectedLogIndices.size,
                                    ),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        Row {
                            IconButton(
                                onClick = {
                                    val selectedText = viewModel.getSelectedLogsText()
                                    if (selectedText.isNotEmpty()) {
                                        val clipLabel = context.getString(R.string.title_log)
                                        val clip = ClipData.newPlainText(clipLabel, selectedText)
                                        Application.clipboard.setPrimaryClip(clip)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.copied_to_clipboard),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        viewModel.clearSelection()
                                    }
                                },
                                enabled = uiState.selectedLogIndices.isNotEmpty(),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.content_description_copy_selected),
                                )
                            }
                        }
                    }
                }
            }

            // Show active filter indicator
            if (uiState.filterLogLevel != LogLevel.Default && !uiState.isSelectionMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string.filter_label,
                                    uiState.filterLogLevel.label,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(
                            onClick = { viewModel.setLogLevel(LogLevel.Default) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.clear_filter),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // Show search bar with animation
            AnimatedVisibility(
                visible = uiState.isSearchActive,
                enter =
                    expandVertically(
                        animationSpec = tween(300),
                    ) +
                        fadeIn(
                            animationSpec = tween(300),
                        ),
                exit =
                    shrinkVertically(
                        animationSpec = tween(300),
                    ) +
                        fadeOut(
                            animationSpec = tween(300),
                        ),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                ) {
                    val focusRequester = remember { FocusRequester() }
                    val focusManager = LocalFocusManager.current

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }

                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                                .focusRequester(focusRequester),
                        placeholder = { Text(stringResource(R.string.search_logs_placeholder)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.search),
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.content_description_clear_search),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions =
                            KeyboardActions(
                                onSearch = {
                                    focusManager.clearFocus()
                                },
                            ),
                    )
                }
            }

            if (uiState.logs.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text =
                                when (serviceStatus) {
                                    Status.Started -> stringResource(R.string.status_started)
                                    Status.Starting -> stringResource(R.string.status_starting)
                                    Status.Stopping -> stringResource(R.string.status_stopping)
                                    else -> stringResource(R.string.status_default)
                                },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                // Log list
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = 88.dp, // Space for FAB
                        ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(
                        items = uiState.logs,
                        key = { _, log -> log.id },
                    ) { index, log ->
                        LogItem(
                            annotatedString = log.annotatedString,
                            index = index,
                            isSelected = uiState.selectedLogIndices.contains(index),
                            isSelectionMode = uiState.isSelectionMode,
                            onLongClick = {
                                if (!uiState.isSelectionMode) {
                                    viewModel.toggleSelectionMode()
                                    viewModel.toggleLogSelection(index)
                                }
                            },
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.toggleLogSelection(index)
                                }
                            },
                        )
                    }
                }
            }
        } // Close Column

        // Options Menu - Material 3 style
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp),
        ) {
            var expandedLogLevel by remember { mutableStateOf(false) }
            var expandedSave by remember { mutableStateOf(false) }

            // File save launcher (must be outside DropdownMenu)
            val saveFileLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("text/plain"),
                    onResult = { uri ->
                        uri?.let {
                            try {
                                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                                    val logsText = viewModel.getAllLogsText()
                                    outputStream.write(logsText.toByteArray())
                                    outputStream.flush()
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.logs_saved_successfully),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.failed_to_save_logs, e.message),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                )

            DropdownMenu(
                expanded = uiState.isOptionsMenuOpen,
                onDismissRequest = {
                    viewModel.toggleOptionsMenu()
                    expandedLogLevel = false
                    expandedSave = false
                },
                modifier = Modifier.widthIn(min = 200.dp),
            ) {
                // Log Level section with nested items
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.log_level),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    onClick = { expandedLogLevel = !expandedLogLevel },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector =
                                if (expandedLogLevel) {
                                    Icons.Default.ExpandLess
                                } else {
                                    Icons.Default.ExpandMore
                                },
                            contentDescription = null,
                        )
                    },
                )

                // Show log levels inline when expanded
                if (expandedLogLevel) {
                    LogLevel.entries.filter { it.priority > 1 }.forEach { level ->
                        DropdownMenuItem(
                            text = {
                                Text(text = level.label)
                            },
                            onClick = {
                                viewModel.setLogLevel(level)
                                viewModel.toggleOptionsMenu()
                                expandedLogLevel = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector =
                                        if (uiState.filterLogLevel == level) {
                                            Icons.Default.RadioButtonChecked
                                        } else {
                                            Icons.Default.RadioButtonUnchecked
                                        },
                                    contentDescription =
                                        if (uiState.filterLogLevel == level) {
                                            stringResource(R.string.group_selected_title)
                                        } else {
                                            null
                                        },
                                    tint =
                                        if (uiState.filterLogLevel == level) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    modifier = Modifier.padding(start = 24.dp),
                                )
                            },
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Save section with nested items
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.save),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    onClick = { expandedSave = !expandedSave },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector =
                                if (expandedSave) {
                                    Icons.Default.ExpandLess
                                } else {
                                    Icons.Default.ExpandMore
                                },
                            contentDescription = null,
                        )
                    },
                )

                // Show save options inline when expanded
                if (expandedSave) {
                    // Copy to Clipboard
                    DropdownMenuItem(
                        text = {
                            Text(text = stringResource(R.string.save_to_clipboard))
                        },
                        onClick = {
                            val logsText = viewModel.getAllLogsText()
                            if (logsText.isNotEmpty()) {
                                val clip =
                                    ClipData.newPlainText(
                                        context.getString(R.string.title_log),
                                        logsText,
                                    )
                                Application.clipboard.setPrimaryClip(clip)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.logs_copied_to_clipboard),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.no_logs_to_copy),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            viewModel.toggleOptionsMenu()
                            expandedSave = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 24.dp),
                            )
                        },
                    )

                    // Save to File
                    DropdownMenuItem(
                        text = {
                            Text(text = stringResource(R.string.save_to_file))
                        },
                        onClick = {
                            val timestamp =
                                SimpleDateFormat(
                                    "yyyyMMdd_HHmmss",
                                    Locale.getDefault(),
                                ).format(Date())
                            saveFileLauncher.launch("logs_$timestamp.txt")
                            viewModel.toggleOptionsMenu()
                            expandedSave = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 24.dp),
                            )
                        },
                    )

                    // Share as File
                    DropdownMenuItem(
                        text = {
                            Text(text = stringResource(R.string.menu_share))
                        },
                        onClick = {
                            val logsText = viewModel.getAllLogsText()
                            if (logsText.isNotEmpty()) {
                                try {
                                    val logsDir =
                                        File(context.cacheDir, "logs").also { it.mkdirs() }
                                    val timestamp =
                                        SimpleDateFormat(
                                            "yyyyMMdd_HHmmss",
                                            Locale.getDefault(),
                                        ).format(Date())
                                    val logFile = File(logsDir, "logs_$timestamp.txt")
                                    logFile.writeText(logsText)

                                    val uri =
                                        FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.cache",
                                            logFile,
                                        )
                                    val shareIntent =
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            context.getString(R.string.intent_share_logs),
                                        ),
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.failed_to_share_logs, e.message),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.no_logs_to_share),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            viewModel.toggleOptionsMenu()
                            expandedSave = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 24.dp),
                            )
                        },
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Clear logs option
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.clear_logs),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        viewModel.requestClearLogs()
                        viewModel.toggleOptionsMenu()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                )
            }
        }

        // FABs - Hide during selection mode
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Scroll to bottom FAB
            AnimatedVisibility(
                visible = !isAtBottom && !uiState.isSelectionMode && uiState.logs.isNotEmpty(),
                enter = androidx.compose.animation.scaleIn(),
                exit = androidx.compose.animation.scaleOut(),
            ) {
                FloatingActionButton(
                    onClick = { viewModel.scrollToBottom() },
                    containerColor = MaterialTheme.colorScheme.secondary,
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.content_description_scroll_to_bottom),
                    )
                }
            }

            // Start/Stop Service FAB
            AnimatedVisibility(
                visible = serviceStatus != Status.Stopping && !uiState.isSelectionMode,
                enter = androidx.compose.animation.scaleIn(),
                exit = androidx.compose.animation.scaleOut(),
            ) {
                FloatingActionButton(
                    onClick = {
                        when (serviceStatus) {
                            Status.Started, Status.Starting -> BoxService.stop()
                            Status.Stopped -> (context as ComposeActivity).startService()
                            else -> {}
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        imageVector =
                            when (serviceStatus) {
                                Status.Started, Status.Starting -> Icons.Default.Stop
                                else -> Icons.Default.PlayArrow
                            },
                        contentDescription =
                            when (serviceStatus) {
                                Status.Started, Status.Starting -> stringResource(R.string.stop)
                                else -> stringResource(R.string.action_start)
                            },
                    )
                }
            }
        }
    } // Close Box that contains Column, Options Menu and FAB
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogItem(
    annotatedString: androidx.compose.ui.text.AnnotatedString,
    index: Int,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        shape = RoundedCornerShape(4.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    },
            ),
        border =
            if (isSelected) {
                CardDefaults.outlinedCardBorder().copy(
                    width = 2.dp,
                    brush =
                        androidx.compose.ui.graphics.SolidColor(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        ),
                )
            } else {
                null
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription =
                        if (isSelected) {
                            stringResource(R.string.group_selected_title)
                        } else {
                            stringResource(
                                R.string.not_selected,
                            )
                        },
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = annotatedString,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(
                            start = if (isSelectionMode) 4.dp else 12.dp,
                            end = 12.dp,
                            top = 8.dp,
                            bottom = 8.dp,
                        ),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
            )
        }
    }
}
