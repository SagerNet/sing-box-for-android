package io.nekohasekai.sfa.compose.screen.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.ui.connections.Connection
import io.nekohasekai.sfa.ui.connections.ConnectionSort
import io.nekohasekai.sfa.ui.connections.ConnectionStateFilter

@Composable
fun ConnectionsScreen(
    serviceStatus: Status,
    viewModel: ConnectionsViewModel = viewModel(),
    onConnectionClick: (Connection) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var showStateMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(serviceStatus) {
        viewModel.updateServiceStatus(serviceStatus)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                FilterChip(
                    selected = uiState.stateFilter != ConnectionStateFilter.All,
                    onClick = { showStateMenu = true },
                    label = {
                        Text(
                            when (uiState.stateFilter) {
                                ConnectionStateFilter.All -> stringResource(R.string.connection_state_all)
                                ConnectionStateFilter.Active -> stringResource(R.string.connection_state_active)
                                ConnectionStateFilter.Closed -> stringResource(R.string.connection_state_closed)
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.FilterList, contentDescription = null)
                    },
                )

                DropdownMenu(
                    expanded = showStateMenu,
                    onDismissRequest = { showStateMenu = false },
                ) {
                    ConnectionStateFilter.entries.forEach { filter ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (filter) {
                                        ConnectionStateFilter.All -> stringResource(R.string.connection_state_all)
                                        ConnectionStateFilter.Active -> stringResource(R.string.connection_state_active)
                                        ConnectionStateFilter.Closed -> stringResource(R.string.connection_state_closed)
                                    }
                                )
                            },
                            onClick = {
                                viewModel.setStateFilter(filter)
                                showStateMenu = false
                            },
                            leadingIcon = {
                                if (uiState.stateFilter == filter) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }

            Box {
                FilterChip(
                    selected = uiState.sort != ConnectionSort.ByDate,
                    onClick = { showSortMenu = true },
                    label = {
                        Text(
                            when (uiState.sort) {
                                ConnectionSort.ByDate -> stringResource(R.string.connection_sort_date)
                                ConnectionSort.ByTraffic -> stringResource(R.string.connection_sort_traffic)
                                ConnectionSort.ByTrafficTotal -> stringResource(R.string.connection_sort_traffic_total)
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.SwapVert, contentDescription = null)
                    },
                )

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    ConnectionSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (sort) {
                                        ConnectionSort.ByDate -> stringResource(R.string.connection_sort_date)
                                        ConnectionSort.ByTraffic -> stringResource(R.string.connection_sort_traffic)
                                        ConnectionSort.ByTrafficTotal -> stringResource(R.string.connection_sort_traffic_total)
                                    }
                                )
                            },
                            onClick = {
                                viewModel.setSort(sort)
                                showSortMenu = false
                            },
                            leadingIcon = {
                                if (uiState.sort == sort) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }
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
private fun rememberBounceBlockingNestedScrollConnection(
    lazyListState: LazyListState
): NestedScrollConnection = remember(lazyListState) {
    object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            return if (available.y < 0) available else Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            return if (available.y < 0) available else Velocity.Zero
        }
    }
}
