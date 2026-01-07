package io.nekohasekai.sfa.compose.screen.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.compose.util.ProfileIcon
import io.nekohasekai.sfa.compose.util.icons.IconCategory
import io.nekohasekai.sfa.compose.util.icons.MaterialIconsLibrary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconSelectionScreen(
    currentIconId: String?,
    onIconSelected: (String?) -> Unit,
    onNavigateBack: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var viewMode by remember { mutableStateOf(IconViewMode.CATEGORIES) }
    var isSearchActive by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Get icons based on current mode
    val displayedIcons =
        remember(searchQuery, selectedCategory, viewMode) {
            when {
                searchQuery.isNotEmpty() -> MaterialIconsLibrary.searchIcons(searchQuery)
                selectedCategory != null -> {
                    MaterialIconsLibrary.categories
                        .find { it.name == selectedCategory }
                        ?.icons ?: emptyList()
                }
                viewMode == IconViewMode.ALL -> MaterialIconsLibrary.getAllIcons()
                else -> emptyList()
            }
        }

    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.select_icon)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back),
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) {
                            searchQuery = ""
                            viewMode = IconViewMode.CATEGORIES
                            selectedCategory = null
                            focusManager.clearFocus()
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription =
                            if (isSearchActive) {
                                stringResource(R.string.close_search)
                            } else {
                                stringResource(
                                    R.string.search_icons,
                                )
                            },
                        tint =
                            if (isSearchActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
        )
    }

    val currentIcon =
        currentIconId?.let { id ->
            MaterialIconsLibrary.getIconById(id)?.let { icon -> id to icon }
        }
    val bottomInset =
        with(LocalDensity.current) {
            WindowInsets.navigationBars.getBottom(this).toDp()
        }
    val bottomBarPadding =
        if (currentIcon != null) {
            88.dp + bottomInset
        } else {
            0.dp
        }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomBarPadding),
        ) {
            // Show search bar with animation
            AnimatedVisibility(
                visible = isSearchActive,
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

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            if (it.isNotEmpty()) {
                                viewMode = IconViewMode.SEARCH
                            } else {
                                viewMode = IconViewMode.CATEGORIES
                                selectedCategory = null
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                                .focusRequester(focusRequester),
                        placeholder = { Text(stringResource(R.string.search_icons_placeholder)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.search),
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    viewMode = IconViewMode.CATEGORIES
                                    selectedCategory = null
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
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

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
            ) {
                // View mode tabs (only show when not searching)
                AnimatedVisibility(visible = searchQuery.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = viewMode == IconViewMode.CATEGORIES && selectedCategory == null,
                            onClick = {
                                viewMode = IconViewMode.CATEGORIES
                                selectedCategory = null
                            },
                            label = { Text(stringResource(R.string.categories)) },
                            leadingIcon =
                                if (viewMode == IconViewMode.CATEGORIES && selectedCategory == null) {
                                    { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) }
                                } else {
                                    null
                                },
                        )

                        FilterChip(
                            selected = viewMode == IconViewMode.ALL,
                            onClick = {
                                viewMode = IconViewMode.ALL
                                selectedCategory = null
                            },
                            label = { Text(stringResource(R.string.all_icons)) },
                            leadingIcon =
                                if (viewMode == IconViewMode.ALL) {
                                    { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) }
                                } else {
                                    null
                                },
                        )

                        FilterChip(
                            selected = currentIconId == null,
                            onClick = {
                                onIconSelected(null)
                                onNavigateBack()
                            },
                            label = { Text(stringResource(R.string.default_text)) },
                            leadingIcon = {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, Modifier.size(16.dp))
                            },
                        )
                    }
                }

                // Back button when category is selected
                AnimatedVisibility(visible = selectedCategory != null && searchQuery.isEmpty()) {
                    TextButton(
                        onClick = {
                            selectedCategory = null
                            viewMode = IconViewMode.CATEGORIES
                        },
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.back_to_categories))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Main content area
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                ) {
                    when {
                        // Search results
                        searchQuery.isNotEmpty() -> {
                            if (displayedIcons.isEmpty()) {
                                EmptySearchResult(searchQuery)
                            } else {
                                IconGrid(
                                    icons = displayedIcons,
                                    currentIconId = currentIconId,
                                    onIconClick = { icon ->
                                        onIconSelected(icon.id)
                                        onNavigateBack()
                                    },
                                )
                            }
                        }
                        // Category view
                        viewMode == IconViewMode.CATEGORIES && selectedCategory == null -> {
                            CategoryList(
                                categories = MaterialIconsLibrary.categories,
                                currentIconId = currentIconId,
                                onCategoryClick = { category ->
                                    selectedCategory = category.name
                                },
                            )
                        }
                        // Icons in selected category or all icons
                        displayedIcons.isNotEmpty() -> {
                            Column {
                                selectedCategory?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                }
                                IconGrid(
                                    icons = displayedIcons,
                                    currentIconId = currentIconId,
                                    onIconClick = { icon ->
                                        onIconSelected(icon.id)
                                        onNavigateBack()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        currentIcon?.let { (id, icon) ->
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        val iconInfo = MaterialIconsLibrary.getAllIcons().find { it.id == id }
                        Text(
                            text =
                                stringResource(
                                    R.string.current_icon_format,
                                    iconInfo?.label ?: id,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        MaterialIconsLibrary.getCategoryForIcon(id)?.let { category ->
                            Text(
                                text = category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryList(
    categories: List<IconCategory>,
    currentIconId: String?,
    onCategoryClick: (IconCategory) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories) { category ->
            CategoryCard(
                category = category,
                hasSelectedIcon = category.icons.any { it.id == currentIconId },
                onClick = { onCategoryClick(category) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryCard(
    category: IconCategory,
    hasSelectedIcon: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (hasSelectedIcon) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.icon_count_format, category.icons.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Preview first 3 icons
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                category.icons.take(3).forEach { icon ->
                    Icon(
                        imageVector = icon.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IconGrid(
    icons: List<ProfileIcon>,
    currentIconId: String?,
    onIconClick: (ProfileIcon) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 72.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(icons) { icon ->
            IconGridItem(
                icon = icon,
                isSelected = currentIconId == icon.id,
                onClick = { onIconClick(icon) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconGridItem(
    icon: ProfileIcon,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    },
            ),
        border =
            if (isSelected) {
                CardDefaults.outlinedCardBorder()
            } else {
                null
            },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon.icon,
                contentDescription = icon.label,
                modifier = Modifier.size(28.dp),
                tint =
                    if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = icon.label,
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmptySearchResult(query: String) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_icons_found),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.no_icons_match, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private enum class IconViewMode {
    CATEGORIES,
    ALL,
    SEARCH,
}
