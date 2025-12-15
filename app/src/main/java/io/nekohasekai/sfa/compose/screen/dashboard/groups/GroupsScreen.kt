package io.nekohasekai.sfa.compose.screen.dashboard.groups

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
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.ui.dashboard.Group
import io.nekohasekai.sfa.ui.dashboard.GroupItem

@Composable
fun GroupsScreen(
    serviceStatus: Status,
    viewModel: GroupsViewModel = viewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onToggleAllGroups: () -> Unit = { viewModel.toggleAllGroups() },
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Stable callbacks to prevent recomposition
    val onToggleExpanded =
        remember<(String) -> Unit> {
            { groupTag -> viewModel.toggleGroupExpand(groupTag) }
        }
    val onItemSelected =
        remember<(String, String) -> Unit> {
            { groupTag, itemTag -> viewModel.selectGroupItem(groupTag, itemTag) }
        }
    val onUrlTest =
        remember<(String) -> Unit> {
            { groupTag -> viewModel.urlTest(groupTag) }
        }

    LaunchedEffect(serviceStatus, viewModel) {
        viewModel.updateServiceStatus(serviceStatus)
    }

    // Show snackbar when needed
    LaunchedEffect(uiState.showCloseConnectionsSnackbar) {
        if (uiState.showCloseConnectionsSnackbar) {
            val message = context.getString(R.string.close_connections_confirm)
            val actionLabel = context.getString(R.string.close)
            val result =
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = actionLabel,
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

    if (uiState.isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
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
                ProxyGroupCard(
                    group = group,
                    isExpanded = uiState.expandedGroups.contains(group.tag),
                    onToggleExpanded = remember { { onToggleExpanded(group.tag) } },
                    onItemSelected = remember { { itemTag -> onItemSelected(group.tag, itemTag) } },
                    onUrlTest = remember { { onUrlTest(group.tag) } },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxyGroupCard(
    group: Group,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onItemSelected: (String) -> Unit,
    onUrlTest: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
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
                                        // Don't toggle expansion when clicking URL test
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

                            val expandContentDescription = stringResource(R.string.expand)
                            val collapseContentDescription = stringResource(R.string.collapse)
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) collapseContentDescription else expandContentDescription,
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
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)),
            ) {
                Column {
                    androidx.compose.material3.HorizontalDivider(
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
}

@Composable
private fun ProxyItemsList(
    items: List<GroupItem>,
    selectedTag: String,
    isSelectable: Boolean,
    onItemSelected: (String) -> Unit,
) {
    // Cache the chunked items to avoid re-chunking on every recomposition
    val itemsPerRow = 2
    val chunkedItems =
        remember(items) {
            items.chunked(itemsPerRow)
        }

    // Use Column with Rows for better control over item sizing
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
                    Box(
                        modifier = Modifier.weight(1f),
                    ) {
                        ProxyChip(
                            item = item,
                            isSelected = item.tag == selectedTag,
                            isSelectable = isSelectable,
                            onClick = remember { { onItemSelected(item.tag) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                // Add empty boxes for incomplete rows to maintain equal sizing
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
        remember(delay, isSelected, colorScheme) {
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
