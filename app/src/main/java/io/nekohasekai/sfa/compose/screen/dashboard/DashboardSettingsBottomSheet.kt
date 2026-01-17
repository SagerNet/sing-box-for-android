package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compat.animateItemCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardSettingsBottomSheet(
    sheetState: SheetState,
    visibleCards: Set<CardGroup>,
    cardOrder: List<CardGroup>,
    onToggleCard: (CardGroup) -> Unit,
    onReorderCards: (List<CardGroup>) -> Unit,
    onResetOrder: () -> Unit,
    onDismiss: () -> Unit,
) {
    var reorderedList by remember(cardOrder) { mutableStateOf(cardOrder) }
    var currentVisibleCards by remember(visibleCards) { mutableStateOf(visibleCards) }

    // Update local state when props change (e.g., after reset)
    LaunchedEffect(cardOrder, visibleCards) {
        reorderedList = cardOrder
        currentVisibleCards = visibleCards
    }

    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Dragging state
    var draggedItem by remember { mutableStateOf<CardGroup?>(null) }
    var draggedIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    val density = LocalDensity.current

    fun onMove(fromIndex: Int, toIndex: Int) {
        if (fromIndex != toIndex &&
            fromIndex >= 0 &&
            toIndex >= 0 &&
            fromIndex < reorderedList.size &&
            toIndex < reorderedList.size
        ) {
            val newList = reorderedList.toMutableList()
            val item = newList.removeAt(fromIndex)
            newList.add(toIndex, item)
            reorderedList = newList
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (reorderedList != cardOrder) {
                onReorderCards(reorderedList)
            }
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Box(
                    modifier = Modifier.size(width = 48.dp, height = 4.dp),
                )
            }
        },
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
        ) {
            // Header with reset button
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_items),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(
                    onClick = {
                        val defaultOrder =
                            listOfNotNull(
                                CardGroup.UploadTraffic,
                                CardGroup.DownloadTraffic,
                                CardGroup.Debug,
                                CardGroup.Connections,
                                CardGroup.SystemProxy,
                                CardGroup.ClashMode,
                                CardGroup.Profiles,
                            )
                        val allCardsEnabled =
                            setOfNotNull(
                                CardGroup.ClashMode,
                                CardGroup.UploadTraffic,
                                CardGroup.DownloadTraffic,
                                CardGroup.Debug,
                                CardGroup.Connections,
                                CardGroup.SystemProxy,
                                CardGroup.Profiles,
                            )
                        reorderedList = defaultOrder
                        currentVisibleCards = allCardsEnabled
                        onResetOrder()
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = stringResource(R.string.reset_order),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.reset))
                }
            }

            // Instruction text
            Text(
                text = stringResource(R.string.drag_handle_to_reorder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp),
            )

            // Reorderable list
            LazyColumn(
                state = listState,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    items = reorderedList,
                    key = { _, item -> item },
                ) { index, cardGroup ->
                    val isVisible = currentVisibleCards.contains(cardGroup)
                    val isDragging = draggedIndex == index

                    DashboardItemCard(
                        cardGroup = cardGroup,
                        isVisible = isVisible,
                        isDragging = isDragging,
                        dragOffset = if (isDragging) dragOffset else 0f,
                        onToggleVisibility = {
                            currentVisibleCards =
                                if (currentVisibleCards.contains(cardGroup)) {
                                    currentVisibleCards - cardGroup
                                } else {
                                    currentVisibleCards + cardGroup
                                }
                            onToggleCard(cardGroup)
                        },
                        onDragStart = {
                            draggedItem = cardGroup
                            draggedIndex = index
                            dragOffset = 0f
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { delta ->
                            if (draggedIndex == index) {
                                dragOffset += delta

                                // Calculate target index based on drag offset
                                val itemHeight = with(density) { 80.dp.toPx() }
                                val threshold = itemHeight * 0.5f

                                when {
                                    dragOffset < -threshold && draggedIndex > 0 -> {
                                        // Moving up
                                        onMove(draggedIndex, draggedIndex - 1)
                                        draggedIndex -= 1
                                        dragOffset += itemHeight
                                    }

                                    dragOffset > threshold && draggedIndex < reorderedList.size - 1 -> {
                                        // Moving down
                                        onMove(draggedIndex, draggedIndex + 1)
                                        draggedIndex += 1
                                        dragOffset -= itemHeight
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            if (reorderedList != cardOrder) {
                                onReorderCards(reorderedList)
                            }
                            draggedItem = null
                            draggedIndex = -1
                            dragOffset = 0f
                        },
                        modifier =
                        animateItemCompat(
                            placementSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardItemCard(
    cardGroup: CardGroup,
    isVisible: Boolean,
    isDragging: Boolean,
    dragOffset: Float,
    onToggleVisibility: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offsetY = remember { mutableStateOf(0f) }

    LaunchedEffect(dragOffset) {
        offsetY.value = dragOffset
    }

    val cardElevation by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 1.dp,
        animationSpec = tween(durationMillis = 300),
        label = "elevation",
    )

    Card(
        modifier =
        modifier
            .fillMaxWidth()
            .offset(y = with(LocalDensity.current) { offsetY.value.toDp() })
            .zIndex(if (isDragging) 1f else 0f)
            .clip(RoundedCornerShape(12.dp)),
        elevation =
        CardDefaults.cardElevation(
            defaultElevation = cardElevation,
        ),
        colors =
        CardDefaults.cardColors(
            containerColor =
            if (isDragging) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border =
        BorderStroke(
            width = 1.dp,
            color =
            if (isVisible) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            },
        ),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Drag handle
            val draggableState =
                rememberDraggableState { delta ->
                    onDrag(delta)
                }

            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.drag_to_reorder),
                modifier =
                Modifier
                    .size(24.dp)
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Vertical,
                        onDragStarted = { onDragStart() },
                        onDragStopped = { onDragEnd() },
                    )
                    .padding(4.dp),
                tint =
                if (isDragging) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            // Card icon
            Icon(
                imageVector =
                when (cardGroup) {
                    CardGroup.Debug -> Icons.Outlined.BugReport
                    CardGroup.Connections -> Icons.Outlined.Cable
                    CardGroup.UploadTraffic -> Icons.Outlined.Upload
                    CardGroup.DownloadTraffic -> Icons.Outlined.Download
                    CardGroup.ClashMode -> Icons.Outlined.Route
                    CardGroup.SystemProxy -> Icons.Outlined.SettingsEthernet
                    CardGroup.Profiles -> Icons.Outlined.Person
                },
                contentDescription = null,
                modifier =
                Modifier
                    .size(24.dp)
                    .padding(horizontal = 4.dp),
                tint =
                if (isVisible) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            // Card info
            Column(
                modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text =
                    when (cardGroup) {
                        CardGroup.Debug -> stringResource(R.string.title_debug)
                        CardGroup.Connections -> stringResource(R.string.title_connections)
                        CardGroup.UploadTraffic -> stringResource(R.string.upload)
                        CardGroup.DownloadTraffic -> stringResource(R.string.download)
                        CardGroup.ClashMode -> stringResource(R.string.clash_mode)
                        CardGroup.SystemProxy -> stringResource(R.string.system_proxy)
                        CardGroup.Profiles -> stringResource(R.string.title_configuration)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Visibility toggle - Profiles card cannot be disabled
            Switch(
                checked = isVisible,
                onCheckedChange = { onToggleVisibility() },
                enabled = cardGroup != CardGroup.Profiles, // Disable switch for Profiles card
            )
        }
    }
}
