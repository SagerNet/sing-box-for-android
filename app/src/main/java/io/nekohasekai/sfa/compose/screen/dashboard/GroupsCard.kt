package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsViewModel
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.ui.dashboard.Group
import io.nekohasekai.sfa.ui.dashboard.GroupItem
import io.nekohasekai.sfa.utils.CommandClient

@Composable
fun GroupsCard(
    serviceStatus: Status,
    isCardMode: Boolean = true,
    commandClient: CommandClient? = null,
    modifier: Modifier = Modifier,
) {
    val viewModel: GroupsViewModel =
        viewModel(
            factory =
                object : ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return GroupsViewModel(commandClient) as T
                    }
                },
        )
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()

    // Stable callbacks to prevent recomposition - use remember with viewModel as key
    val onToggleExpanded =
        remember(viewModel) {
            { groupTag: String -> viewModel.toggleGroupExpand(groupTag) }
        }
    val onItemSelected =
        remember(viewModel) {
            { groupTag: String, itemTag: String -> viewModel.selectGroupItem(groupTag, itemTag) }
        }
    val onUrlTest =
        remember(viewModel) {
            { groupTag: String -> viewModel.urlTest(groupTag) }
        }

    // Only update service status when it actually changes
    LaunchedEffect(serviceStatus) {
        viewModel.updateServiceStatus(serviceStatus)
    }

    // Show snackbar when needed
    LaunchedEffect(uiState.showCloseConnectionsSnackbar) {
        if (uiState.showCloseConnectionsSnackbar) {
            val result =
                snackbarHostState.showSnackbar(
                    message = "Close all connections?",
                    actionLabel = "Close",
                    duration = androidx.compose.material3.SnackbarDuration.Indefinite,
                    withDismissAction = true,
                )
            when (result) {
                androidx.compose.material3.SnackbarResult.ActionPerformed -> {
                    viewModel.closeConnections()
                }

                androidx.compose.material3.SnackbarResult.Dismissed -> {
                    viewModel.dismissCloseConnectionsSnackbar()
                }
            }
        }
    }

    if (isCardMode) {
        // Card mode - wrapped in a card with header
        Card(
            modifier = modifier.fillMaxWidth(),
        ) {
            GroupsCardContent(
                uiState = uiState,
                isCardMode = true,
                onToggleAllGroups = { viewModel.toggleAllGroups() },
                onToggleExpanded = onToggleExpanded,
                onItemSelected = onItemSelected,
                onUrlTest = onUrlTest,
            )
        }
    } else {
        // Standalone mode - direct content without card wrapper
        GroupsCardContent(
            uiState = uiState,
            isCardMode = false,
            onToggleAllGroups = { viewModel.toggleAllGroups() },
            onToggleExpanded = onToggleExpanded,
            onItemSelected = onItemSelected,
            onUrlTest = onUrlTest,
            modifier = modifier,
        )
    }
}

@Composable
private fun GroupsCardContent(
    uiState: io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsUiState,
    isCardMode: Boolean,
    onToggleAllGroups: () -> Unit,
    onToggleExpanded: (String) -> Unit,
    onItemSelected: (String, String) -> Unit,
    onUrlTest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (isCardMode) {
            // Card header with title and collapse/expand all button
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.title_groups),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Collapse/Expand all button in the top right
                if (uiState.groups.isNotEmpty()) {
                    val allCollapsed = uiState.expandedGroups.isEmpty()
                    IconButton(
                        onClick = onToggleAllGroups,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector =
                                if (allCollapsed) {
                                    Icons.Default.UnfoldMore
                                } else {
                                    Icons.Default.UnfoldLess
                                },
                            contentDescription = if (allCollapsed) "Expand All" else "Collapse All",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 1.dp,
            )
        }

        // Groups content
        if (uiState.isLoading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.groups.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No groups available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            if (isCardMode) {
                // In card mode, show groups directly without LazyColumn
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                ) {
                    uiState.groups.forEachIndexed { index, group ->
                        // Add divider above each group (not for the first one in card mode)
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                thickness = 1.dp,
                            )
                        }
                        ProxyGroupItem(
                            group = group,
                            isExpanded = uiState.expandedGroups.contains(group.tag),
                            onToggleExpanded = { onToggleExpanded(group.tag) },
                            onItemSelected = { itemTag -> onItemSelected(group.tag, itemTag) },
                            onUrlTest = { onUrlTest(group.tag) },
                            showCard = false,
                        )
                    }
                }
            } else {
                // In standalone mode, use LazyColumn for scrolling
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 16.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = uiState.groups,
                        key = { it.tag },
                        contentType = { "GroupCard" },
                    ) { group ->
                        ProxyGroupItem(
                            group = group,
                            isExpanded = uiState.expandedGroups.contains(group.tag),
                            onToggleExpanded = { onToggleExpanded(group.tag) },
                            onItemSelected = { itemTag -> onItemSelected(group.tag, itemTag) },
                            onUrlTest = { onUrlTest(group.tag) },
                            showCard = true,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxyGroupItem(
    group: Group,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onItemSelected: (String) -> Unit,
    onUrlTest: () -> Unit,
    showCard: Boolean,
) {
    val content = @Composable {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Header (clickable to expand/collapse)
            Surface(
                onClick = onToggleExpanded,
                color = Color.Transparent,
            ) {
                ListItem(
                    headlineContent = {
                        Column {
                            Text(
                                text = group.tag,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = Libbox.proxyDisplayType(group.type),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                // Show selected item when collapsed
                                AnimatedVisibility(
                                    visible = !isExpanded && group.selected.isNotEmpty(),
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = group.selected,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    trailingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // URL Test button
                            AnimatedVisibility(
                                visible = group.selectable,
                                enter = slideInVertically() + fadeIn(),
                                exit = slideOutVertically() + fadeOut(),
                            ) {
                                IconButton(
                                    onClick = {
                                        onUrlTest()
                                    },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = stringResource(R.string.url_test),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            // Expand/Collapse indicator
                            val rotationAngle by animateFloatAsState(
                                targetValue = if (isExpanded) 180f else 0f,
                                animationSpec = tween(300),
                                label = "ExpandIcon",
                            )

                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .graphicsLayer { rotationZ = rotationAngle },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                )
            }

            // Expandable content
            AnimatedVisibility(
                visible = isExpanded && group.items.isNotEmpty(),
                enter =
                    expandVertically(animationSpec = tween(300)) +
                        fadeIn(
                            animationSpec =
                                tween(
                                    300,
                                ),
                        ),
                exit =
                    shrinkVertically(animationSpec = tween(300)) +
                        fadeOut(
                            animationSpec =
                                tween(
                                    300,
                                ),
                        ),
            ) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        thickness = 1.dp,
                    )

                    // Proxy Items
                    ProxyItemsList(
                        items = group.items,
                        selectedTag = group.selected,
                        isSelectable = group.selectable,
                        onItemSelected = onItemSelected,
                    )
                }
            }
        }
    }

    if (showCard) {
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
private fun ProxyItemsList(
    items: List<GroupItem>,
    selectedTag: String,
    isSelectable: Boolean,
    onItemSelected: (String) -> Unit,
) {
    val itemsPerRow = 2
    val chunkedItems =
        remember(items) {
            items.chunked(itemsPerRow)
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        chunkedItems.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { item ->
                    key(item.tag) {
                        Box(
                            modifier = Modifier.weight(1f),
                        ) {
                            ProxyChip(
                                item = item,
                                isSelected = item.tag == selectedTag,
                                isSelectable = isSelectable,
                                onClick = { onItemSelected(item.tag) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                repeat(itemsPerRow - rowItems.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxyChip(
    item: GroupItem,
    isSelected: Boolean,
    isSelectable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Use simpler, faster animations
    val animatedElevation by animateFloatAsState(
        targetValue = if (isSelected) 6.dp.value else 1.dp.value,
        animationSpec = tween(150),
        label = "Elevation",
    )

    val surfaceModifier = modifier
    val surfaceShape = RoundedCornerShape(8.dp)
    val surfaceColor =
        when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        }
    val surfaceBorder =
        androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color =
                when {
                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                },
        )

    val content: @Composable () -> Unit = {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // First line: Name
                Text(
                    text = item.tag,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Second line: Type on left, Latency on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Type
                    Text(
                        text = Libbox.proxyDisplayType(item.type),
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                    )

                    // Latency
                    AnimatedVisibility(
                        visible = item.urlTestTime > 0,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        ProxyLatencyBadge(
                            delay = item.urlTestDelay,
                            isSelected = isSelected,
                        )
                    }
                }
            }
        }
    }

    if (isSelectable) {
        Surface(
            onClick = onClick,
            modifier = surfaceModifier,
            shape = surfaceShape,
            color = surfaceColor,
            tonalElevation = animatedElevation.dp,
            border = surfaceBorder,
            content = content,
        )
    } else {
        Surface(
            modifier = surfaceModifier,
            shape = surfaceShape,
            color = surfaceColor,
            tonalElevation = animatedElevation.dp,
            border = surfaceBorder,
            content = content,
        )
    }
}

@Composable
private fun ProxyLatencyBadge(
    delay: Int,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    // Direct color calculation without animation for better performance
    val colorScheme = MaterialTheme.colorScheme
    val latencyColor =
        remember(delay, isSelected) {
            when {
                delay < 100 -> {
                    // Excellent - green/tertiary
                    if (isSelected) {
                        colorScheme.tertiary
                    } else {
                        colorScheme.tertiary.copy(alpha = 0.9f)
                    }
                }

                delay < 300 -> {
                    // Good - primary
                    if (isSelected) {
                        colorScheme.primary
                    } else {
                        colorScheme.primary.copy(alpha = 0.9f)
                    }
                }

                delay < 500 -> {
                    // Fair - secondary/warning
                    if (isSelected) {
                        colorScheme.secondary
                    } else {
                        colorScheme.secondary.copy(alpha = 0.9f)
                    }
                }

                else -> {
                    // Poor - error
                    if (isSelected) {
                        colorScheme.error
                    } else {
                        colorScheme.error.copy(alpha = 0.9f)
                    }
                }
            }
        }

    Text(
        text = "${delay}ms",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = latencyColor,
        modifier = modifier,
    )
}
