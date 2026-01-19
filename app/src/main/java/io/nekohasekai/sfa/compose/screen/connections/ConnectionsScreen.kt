package io.nekohasekai.sfa.compose.screen.connections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.model.Connection
import io.nekohasekai.sfa.compose.model.ConnectionSort
import io.nekohasekai.sfa.compose.model.ConnectionStateFilter
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.constant.Status

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsPage(
    serviceStatus: Status,
    viewModel: ConnectionsViewModel = viewModel(),
    showTitle: Boolean = true,
    showTopBar: Boolean = false,
    onConnectionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var showStateMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showConnectionsMenu by remember { mutableStateOf(false) }

    if (showTopBar) {
        OverrideTopBar {
            TopAppBar(
                title = { Text(stringResource(R.string.title_connections)) },
            )
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showTitle) {
                Text(
                    text = stringResource(R.string.title_connections),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Box {
                FilterChip(
                    selected = uiState.stateFilter != ConnectionStateFilter.Active,
                    onClick = { showStateMenu = true },
                    label = {
                        Text(
                            when (uiState.stateFilter) {
                                ConnectionStateFilter.All -> stringResource(R.string.connection_state_all)
                                ConnectionStateFilter.Active -> stringResource(R.string.connection_state_active)
                                ConnectionStateFilter.Closed -> stringResource(R.string.connection_state_closed)
                            },
                        )
                    },
                )

                DropdownMenu(
                    expanded = showStateMenu,
                    onDismissRequest = { showStateMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.connection_state_all)) },
                        onClick = {
                            viewModel.setStateFilter(ConnectionStateFilter.All)
                            showStateMenu = false
                        },
                        leadingIcon = {
                            if (uiState.stateFilter == ConnectionStateFilter.All) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.connection_state_active)) },
                        onClick = {
                            viewModel.setStateFilter(ConnectionStateFilter.Active)
                            showStateMenu = false
                        },
                        leadingIcon = {
                            if (uiState.stateFilter == ConnectionStateFilter.Active) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.connection_state_closed)) },
                        onClick = {
                            viewModel.setStateFilter(ConnectionStateFilter.Closed)
                            showStateMenu = false
                        },
                        leadingIcon = {
                            if (uiState.stateFilter == ConnectionStateFilter.Closed) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        },
                    )
                }
            }

            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.Default.SwapVert, contentDescription = null)
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.connection_sort_date)) },
                        onClick = {
                            viewModel.setSort(ConnectionSort.ByDate)
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (uiState.sort == ConnectionSort.ByDate) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.connection_sort_traffic)) },
                        onClick = {
                            viewModel.setSort(ConnectionSort.ByTraffic)
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (uiState.sort == ConnectionSort.ByTraffic) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.connection_sort_traffic_total)) },
                        onClick = {
                            viewModel.setSort(ConnectionSort.ByTrafficTotal)
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (uiState.sort == ConnectionSort.ByTrafficTotal) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        },
                    )
                }
            }

            Box {
                IconButton(onClick = { showConnectionsMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }

                DropdownMenu(
                    expanded = showConnectionsMenu,
                    onDismissRequest = { showConnectionsMenu = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (uiState.isSearchActive) {
                                    stringResource(R.string.close_search)
                                } else {
                                    stringResource(R.string.search)
                                },
                            )
                        },
                        onClick = {
                            viewModel.toggleSearch()
                            showConnectionsMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (uiState.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = null,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.connection_close_all)) },
                        onClick = {
                            viewModel.closeAllConnections()
                            showConnectionsMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Close, contentDescription = null)
                        },
                        enabled = uiState.connections.any { it.isActive },
                    )
                }
            }
        }

        ConnectionsScreen(
            serviceStatus = serviceStatus,
            viewModel = viewModel,
            onConnectionClick = { connection -> onConnectionClick(connection.id) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDetailsRoute(
    connectionId: String,
    serviceStatus: Status,
    viewModel: ConnectionsViewModel = viewModel(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val connection =
        uiState.allConnections.find { it.id == connectionId }
            ?: uiState.connections.find { it.id == connectionId }

    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.connection_details)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back),
                    )
                }
            },
            actions = {
                if (connection?.isActive == true) {
                    IconButton(onClick = { viewModel.closeConnection(connectionId) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.connection_close),
                        )
                    }
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        viewModel.setVisible(true)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setVisible(false)
        }
    }

    LaunchedEffect(serviceStatus) {
        viewModel.updateServiceStatus(serviceStatus)
    }

    if (connection == null) {
        LaunchedEffect(connectionId) {
            onBack()
        }
        Box(modifier = modifier.fillMaxSize())
    } else {
        ConnectionDetailsScreen(
            connection = connection,
            onBack = onBack,
            onClose = { viewModel.closeConnection(connectionId) },
            modifier = modifier,
            showHeader = false,
        )
    }
}

@Composable
fun ConnectionsScreen(
    serviceStatus: Status,
    viewModel: ConnectionsViewModel = viewModel(),
    onConnectionClick: (Connection) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.setVisible(true)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setVisible(false)
        }
    }

    LaunchedEffect(serviceStatus) {
        viewModel.updateServiceStatus(serviceStatus)
    }

    Column(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = uiState.isSearchActive,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val focusRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            OutlinedTextField(
                value = uiState.searchText,
                onValueChange = { viewModel.setSearchText(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.search_connections)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchText.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchText("") }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
            )
        }

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.connections.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.empty_connections),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                val lazyListState = rememberLazyListState()
                val bounceBlockingConnection = rememberBounceBlockingNestedScrollConnection(lazyListState)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(bounceBlockingConnection),
                    state = lazyListState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = uiState.connections,
                        key = { it.id },
                    ) { connection ->
                        ConnectionItem(
                            connection = connection,
                            onClick = { onConnectionClick(connection) },
                            onClose = { viewModel.closeConnection(connection.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberBounceBlockingNestedScrollConnection(lazyListState: LazyListState): NestedScrollConnection = remember(lazyListState) {
    object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = if (available.y < 0) available else Offset.Zero

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = if (available.y < 0) available else Velocity.Zero
    }
}
