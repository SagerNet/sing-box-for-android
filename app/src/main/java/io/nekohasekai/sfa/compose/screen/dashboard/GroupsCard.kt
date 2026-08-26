package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compat.LazyColumnCompat
import io.nekohasekai.sfa.compat.rememberOverscrollEffectCompat
import io.nekohasekai.sfa.compose.component.SnackbarHost
import io.nekohasekai.sfa.compose.model.Group
import io.nekohasekai.sfa.compose.model.GroupItem
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsUiState
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsViewModel
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.compose.util.rememberSheetDismissFromContentOnlyIfGestureStartedAtTopModifier
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.utils.CommandClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsCard(
    serviceStatus: Status,
    commandClient: CommandClient? = null,
    viewModel: GroupsViewModel? = null,
    showTopBar: Boolean = false,
    listHeaderContent: (@Composable () -> Unit)? = null,
    asSheet: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val actualViewModel: GroupsViewModel = viewModel ?: viewModel(
        factory =
        object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return GroupsViewModel(commandClient) as T
            }
        },
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by actualViewModel.uiState.collectAsState()

    if (showTopBar) {
        val allCollapsed = uiState.expandedGroups.isEmpty()
        OverrideTopBar {
            TopAppBar(
                title = { Text(stringResource(R.string.title_groups)) },
                actions = {
                    if (uiState.groups.isNotEmpty()) {
                        IconButton(onClick = { actualViewModel.toggleAllGroups() }) {
                            Icon(
                                imageVector =
                                if (allCollapsed) {
                                    Icons.Default.UnfoldMore
                                } else {
                                    Icons.Default.UnfoldLess
                                },
                                contentDescription =
                                if (allCollapsed) {
                                    stringResource(R.string.expand_all)
                                } else {
                                    stringResource(R.string.collapse_all)
                                },
                            )
                        }
                    }
                },
            )
        }
    }

    val onToggleExpanded =
        remember(actualViewModel) {
            { groupTag: String -> actualViewModel.toggleGroupExpand(groupTag) }
        }
    val onItemSelected =
        remember(actualViewModel) {
            { groupTag: String, itemTag: String -> actualViewModel.selectGroupItem(groupTag, itemTag) }
        }
    val onUrlTest =
        remember(actualViewModel) {
            { groupTag: String -> actualViewModel.urlTestGroup(groupTag) }
        }
    val onItemUrlTest =
        remember(actualViewModel) {
            { itemTag: String -> actualViewModel.urlTest(itemTag) }
        }

    LaunchedEffect(serviceStatus) {
        actualViewModel.updateServiceStatus(serviceStatus)
    }

    val closeConnectionsMessage = stringResource(R.string.close_connections_confirm)
    val closeConnectionsAction = stringResource(R.string.close)
    LaunchedEffect(uiState.showCloseConnectionsSnackbar) {
        if (uiState.showCloseConnectionsSnackbar) {
            val result =
                snackbarHostState.showSnackbar(
                    message = closeConnectionsMessage,
                    actionLabel = closeConnectionsAction,
                    duration = SnackbarDuration.Indefinite,
                    withDismissAction = true,
                )
            when (result) {
                SnackbarResult.ActionPerformed -> {
                    actualViewModel.closeConnections()
                }

                SnackbarResult.Dismissed -> {
                    actualViewModel.dismissCloseConnectionsSnackbar()
                }
            }
        }
    }

    Box(modifier = modifier) {
        GroupsCardContent(
            uiState = uiState,
            onToggleExpanded = onToggleExpanded,
            onItemSelected = onItemSelected,
            onUrlTest = onUrlTest,
            onItemUrlTest = onItemUrlTest,
            listHeaderContent = listHeaderContent,
            asSheet = asSheet,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupsCardContent(
    uiState: GroupsUiState,
    onToggleExpanded: (String) -> Unit,
    onItemSelected: (String, String) -> Unit,
    onUrlTest: (String) -> Unit,
    onItemUrlTest: (String) -> Unit,
    listHeaderContent: (@Composable () -> Unit)? = null,
    asSheet: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val scrollModifier =
        if (asSheet) {
            rememberSheetDismissFromContentOnlyIfGestureStartedAtTopModifier {
                lazyListState.firstVisibleItemIndex == 0 &&
                    lazyListState.firstVisibleItemScrollOffset == 0
            }
        } else {
            Modifier.nestedScroll(rememberBounceBlockingNestedScrollConnection(lazyListState))
        }
    val scaffoldPadding = if (asSheet) PaddingValues(0.dp) else LocalScaffoldPadding.current
    val overscrollEffect = if (asSheet) null else rememberOverscrollEffectCompat()
    val palette = rememberUrlTestPalette()

    LazyColumnCompat(
        modifier =
        modifier
            .fillMaxSize()
            .then(scrollModifier)
            .padding(scaffoldPadding),
        state = lazyListState,
        contentPadding =
        PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 16.dp,
        ),
        overscrollEffect = overscrollEffect,
    ) {
        if (listHeaderContent != null) {
            item(key = "groups_list_header") {
                listHeaderContent()
            }
        }

        when {
            uiState.isLoading -> {
                item(key = "groups_loading") {
                    Box(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            else -> {
                uiState.groups.forEach { group ->
                    val isExpanded = uiState.expandedGroups.contains(group.tag)
                    val headerContent: @Composable (Modifier) -> Unit = { headerModifier ->
                        GroupHeader(
                            group = group,
                            isExpanded = isExpanded,
                            isTesting = uiState.testingGroups.contains(group.tag),
                            onToggleExpanded = { onToggleExpanded(group.tag) },
                            onUrlTest = { onUrlTest(group.tag) },
                            modifier = headerModifier,
                        )
                    }
                    if (isExpanded) {
                        stickyHeader(key = "header:${group.tag}", contentType = "GroupHeader") {
                            headerContent(Modifier.animateItem())
                        }
                    } else {
                        item(key = "header:${group.tag}", contentType = "GroupHeader") {
                            headerContent(Modifier.animateItem())
                        }
                    }
                    if (isExpanded) {
                        val rowItems = group.items.chunked(2)
                        rowItems.forEachIndexed { rowIndex, row ->
                            item(
                                key = "row:${group.tag}:${row.first().tag}",
                                contentType = "GroupItemRow",
                            ) {
                                GroupItemRow(
                                    row = row,
                                    selectedTag = group.selected,
                                    isSelectable = group.selectable,
                                    isLast = rowIndex == rowItems.lastIndex,
                                    palette = palette,
                                    onItemSelected = { itemTag -> onItemSelected(group.tag, itemTag) },
                                    onItemUrlTest = onItemUrlTest,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    } else {
                        item(key = "dots:${group.tag}", contentType = "GroupDots") {
                            GroupDotsGrid(
                                group = group,
                                palette = palette,
                                onClick = { onToggleExpanded(group.tag) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val GroupCardTopShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val GroupCardBottomShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

@Immutable
private data class UrlTestPalette(
    val good: Color,
    val medium: Color,
    val bad: Color,
    val neutral: Color,
) {
    fun forDelay(delay: Int): Color = when {
        delay <= 0 -> neutral
        delay < 800 -> good
        delay < 1500 -> medium
        else -> bad
    }
}

@Composable
private fun rememberUrlTestPalette(): UrlTestPalette {
    val darkTheme = isSystemInDarkTheme()
    val neutral = MaterialTheme.colorScheme.onSurface.copy(alpha = if (darkTheme) 0.09f else 0.07f)
    return remember(darkTheme, neutral) {
        if (darkTheme) {
            UrlTestPalette(
                good = Color(0xFF7CB89E),
                medium = Color(0xFFD3A45E),
                bad = Color(0xFFDB8A62),
                neutral = neutral,
            )
        } else {
            UrlTestPalette(
                good = Color(0xFF3D8168),
                medium = Color(0xFFA8742F),
                bad = Color(0xFFC25E32),
                neutral = neutral,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupHeader(
    group: Group,
    isExpanded: Boolean,
    isTesting: Boolean,
    onToggleExpanded: () -> Unit,
    onUrlTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onToggleExpanded,
        modifier =
        modifier
            .fillMaxWidth(),
        shape = GroupCardTopShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = group.tag,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = group.displayType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            ) {
                Text(
                    text = "${group.items.size}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            IconButton(
                onClick = onUrlTest,
                enabled = !isTesting,
                modifier = Modifier.size(40.dp),
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = stringResource(R.string.url_test),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val rotationAngle by animateFloatAsState(
                targetValue = if (isExpanded) 180f else 0f,
                animationSpec = tween(200),
                label = "ExpandIcon",
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription =
                if (isExpanded) {
                    stringResource(R.string.collapse)
                } else {
                    stringResource(R.string.expand)
                },
                modifier =
                Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = rotationAngle },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GroupDotsGrid(
    group: Group,
    palette: UrlTestPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
        modifier
            .padding(bottom = 12.dp)
            .fillMaxWidth(),
        shape = GroupCardBottomShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        BoxWithConstraints(
            modifier =
            Modifier
                .clickable(onClick = onClick)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
        ) {
            val dotSize = 11.dp
            val dotSpacing = 4.dp
            val columns = maxOf(1, ((maxWidth + dotSpacing) / (dotSize + dotSpacing)).toInt())
            val rows = (group.items.size + columns - 1) / columns
            val gridHeight = dotSize * rows + dotSpacing * maxOf(0, rows - 1)
            Canvas(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(gridHeight),
            ) {
                val dotSizePx = dotSize.toPx()
                val dotSpacingPx = dotSpacing.toPx()
                val cornerRadius = CornerRadius(4.dp.toPx())
                val selectedDotRadius = 2.dp.toPx()
                group.items.forEachIndexed { index, item ->
                    val x = (index % columns) * (dotSizePx + dotSpacingPx)
                    val y = (index / columns) * (dotSizePx + dotSpacingPx)
                    drawRoundRect(
                        color = palette.forDelay(item.urlTestDelay),
                        topLeft = Offset(x, y),
                        size = Size(dotSizePx, dotSizePx),
                        cornerRadius = cornerRadius,
                    )
                    if (item.tag == group.selected) {
                        drawCircle(
                            color = Color.White,
                            radius = selectedDotRadius,
                            center = Offset(x + dotSizePx / 2, y + dotSizePx / 2),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupItemRow(
    row: List<GroupItem>,
    selectedTag: String,
    isSelectable: Boolean,
    isLast: Boolean,
    palette: UrlTestPalette,
    onItemSelected: (String) -> Unit,
    onItemUrlTest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
        modifier
            .padding(bottom = if (isLast) 12.dp else 0.dp)
            .fillMaxWidth(),
        shape = if (isLast) GroupCardBottomShape else RectangleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier =
            Modifier.padding(
                start = 16.dp,
                end = 16.dp,
                top = 5.dp,
                bottom = if (isLast) 16.dp else 5.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            row.forEach { item ->
                ProxyChip(
                    item = item,
                    isSelected = item.tag == selectedTag,
                    isSelectable = isSelectable,
                    palette = palette,
                    onClick = { onItemSelected(item.tag) },
                    onUrlTest = { onItemUrlTest(item.tag) },
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(2 - row.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProxyChip(
    item: GroupItem,
    isSelected: Boolean,
    isSelectable: Boolean,
    palette: UrlTestPalette,
    onClick: () -> Unit,
    onUrlTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val chipShape = RoundedCornerShape(12.dp)
    Box(modifier = modifier) {
        Surface(
            modifier =
            Modifier
                .fillMaxWidth()
                .clip(chipShape)
                .combinedClickable(
                    onClick = { if (isSelectable) onClick() },
                    onLongClick = { showContextMenu = true },
                ),
            shape = chipShape,
            color =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.tag,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.displayType,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (item.urlTestDelay > 0) {
                        Text(
                            text = "${item.urlTestDelay}ms",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.forDelay(item.urlTestDelay),
                        )
                    }
                }
            }
        }
        if (showContextMenu) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { showContextMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.url_test)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onUrlTest()
                    },
                )
            }
        }
    }
}

@Composable
private fun rememberBounceBlockingNestedScrollConnection(lazyListState: LazyListState): NestedScrollConnection = remember(lazyListState) {
    object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // Only block upward scroll (y < 0) at bottom to prevent sheet expansion
            // Allow downward scroll (y > 0) at top to let sheet collapse
            return if (available.y < 0) available else Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // Only block upward fling (y < 0) to prevent sheet expansion
            // Allow downward fling (y > 0) to let sheet collapse
            return if (available.y < 0) available else Velocity.Zero
        }
    }
}
