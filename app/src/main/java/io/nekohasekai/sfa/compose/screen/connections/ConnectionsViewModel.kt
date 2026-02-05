package io.nekohasekai.sfa.compose.screen.connections

import androidx.lifecycle.viewModelScope
import libbox.ConnectionEvents
import libbox.Connections
import libbox.Libbox
import io.nekohasekai.sfa.compose.base.BaseViewModel
import io.nekohasekai.sfa.compose.base.ScreenEvent
import io.nekohasekai.sfa.compose.model.Connection
import io.nekohasekai.sfa.compose.model.ConnectionSort
import io.nekohasekai.sfa.compose.model.ConnectionStateFilter
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.ktx.toList
import io.nekohasekai.sfa.utils.AppLifecycleObserver
import io.nekohasekai.sfa.utils.CommandClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

data class ConnectionsUiState(
    val connections: List<Connection> = emptyList(),
    val allConnections: List<Connection> = emptyList(),
    val isLoading: Boolean = false,
    val stateFilter: ConnectionStateFilter = ConnectionStateFilter.Active,
    val sort: ConnectionSort = ConnectionSort.ByDate,
    val searchText: String = "",
    val isSearchActive: Boolean = false,
)

sealed class ConnectionsEvent : ScreenEvent {
    data class ConnectionClosed(val id: String) : ConnectionsEvent()
    data object AllConnectionsClosed : ConnectionsEvent()
}

class ConnectionsViewModel :
    BaseViewModel<ConnectionsUiState, ConnectionsEvent>(),
    CommandClient.Handler {
    private val commandClient = CommandClient(
        viewModelScope,
        CommandClient.ConnectionType.Connections,
        this,
    )

    private val _serviceStatus = MutableStateFlow(Status.Stopped)
    val serviceStatus = _serviceStatus.asStateFlow()
    private var lastServiceStatus: Status = Status.Stopped

    private val _visibleCount = MutableStateFlow(0)

    private var connectionsStore: Connections? = null
    private val connectionsMutex = Mutex()
    private val connectionsGeneration = AtomicLong(0)

    override fun createInitialState() = ConnectionsUiState()

    private data class ConnectionState(
        val foreground: Boolean,
        val screenOn: Boolean,
        val visibleCount: Int,
        val status: Status,
    )

    init {
        viewModelScope.launch {
            combine(
                AppLifecycleObserver.isForeground,
                AppLifecycleObserver.isScreenOn,
                _visibleCount,
                _serviceStatus,
            ) { foreground, screenOn, visibleCount, status ->
                ConnectionState(foreground, screenOn, visibleCount, status)
            }.collect { state ->
                val shouldConnect = state.foreground && state.screenOn &&
                    state.visibleCount > 0 && state.status == Status.Started
                if (shouldConnect) {
                    updateState { copy(isLoading = true) }
                    commandClient.connect()
                } else {
                    commandClient.disconnect()
                }
            }
        }
    }

    fun setVisible(visible: Boolean) {
        _visibleCount.value += if (visible) 1 else -1
    }

    override fun onCleared() {
        super.onCleared()
        commandClient.disconnect()
    }

    private suspend fun handleServiceStatusChange(status: Status) {
        if (status != Status.Started) {
            withContext(Dispatchers.Default) {
                connectionsMutex.withLock {
                    connectionsStore = null
                }
                connectionsGeneration.incrementAndGet()
            }
            updateState {
                copy(connections = emptyList(), allConnections = emptyList(), isLoading = false)
            }
        }
    }

    fun updateServiceStatus(status: Status) {
        if (status == lastServiceStatus) return
        lastServiceStatus = status
        viewModelScope.launch {
            _serviceStatus.emit(status)
            handleServiceStatusChange(status)
        }
    }

    fun setStateFilter(filter: ConnectionStateFilter) {
        updateState { copy(stateFilter = filter) }
        requestConnectionsRefresh()
    }

    fun setSort(sort: ConnectionSort) {
        updateState { copy(sort = sort) }
        requestConnectionsRefresh()
    }

    fun setSearchText(text: String) {
        updateState { copy(searchText = text) }
        requestConnectionsRefresh()
    }

    fun toggleSearch() {
        val newSearchActive = !currentState.isSearchActive
        updateState {
            copy(
                isSearchActive = newSearchActive,
                searchText = if (newSearchActive) searchText else "",
            )
        }
        if (!newSearchActive) {
            requestConnectionsRefresh()
        }
    }

    fun closeConnection(connectionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Libbox.newStandaloneCommandClient().closeConnection(connectionId)
                withContext(Dispatchers.Main) {
                    sendEvent(ConnectionsEvent.ConnectionClosed(connectionId))
                }
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }

    fun closeAllConnections() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Libbox.newStandaloneCommandClient().closeConnections()
                withContext(Dispatchers.Main) {
                    sendEvent(ConnectionsEvent.AllConnectionsClosed)
                }
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }

    override fun onConnected() {
        viewModelScope.launch(Dispatchers.Main) {
            updateState { copy(isLoading = false) }
        }
    }

    override fun onDisconnected() {
        viewModelScope.launch(Dispatchers.Default) {
            connectionsMutex.withLock {
                connectionsStore = null
            }
            connectionsGeneration.incrementAndGet()
            withContext(Dispatchers.Main) {
                updateState {
                    copy(connections = emptyList(), allConnections = emptyList(), isLoading = false)
                }
            }
        }
    }

    override fun writeConnectionEvents(events: ConnectionEvents) {
        viewModelScope.launch(Dispatchers.Default) {
            val generation = connectionsGeneration.get()
            val snapshot = connectionsMutex.withLock {
                if (connectionsStore == null) {
                    connectionsStore = Connections()
                }
                val store = connectionsStore ?: return@withLock null
                store.applyEvents(events)
                buildConnectionLists(store, uiState.value)
            } ?: return@launch
            if (connectionsGeneration.get() != generation) {
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (connectionsGeneration.get() != generation) {
                    return@withContext
                }
                updateState {
                    copy(
                        connections = snapshot.connections,
                        allConnections = snapshot.allConnections,
                        isLoading = false,
                    )
                }
            }
        }
    }

    private fun requestConnectionsRefresh() {
        viewModelScope.launch(Dispatchers.Default) {
            val generation = connectionsGeneration.get()
            val snapshot = connectionsMutex.withLock {
                val store = connectionsStore ?: return@withLock null
                buildConnectionLists(store, uiState.value)
            } ?: return@launch
            if (connectionsGeneration.get() != generation) {
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (connectionsGeneration.get() != generation) {
                    return@withContext
                }
                updateState {
                    copy(
                        connections = snapshot.connections,
                        allConnections = snapshot.allConnections,
                        isLoading = false,
                    )
                }
            }
        }
    }

    private fun buildConnectionLists(
        connections: Connections,
        currentState: ConnectionsUiState,
    ): ConnectionLists {
        val allConnectionList = connections.iterator().toList()
            .filter { it.outboundType != "dns" }
            .map { Connection.from(it) }

        connections.filterState(currentState.stateFilter.libboxValue)

        when (currentState.sort) {
            ConnectionSort.ByDate -> connections.sortByDate()
            ConnectionSort.ByTraffic -> connections.sortByTraffic()
            ConnectionSort.ByTrafficTotal -> connections.sortByTrafficTotal()
        }

        val connectionList = connections.iterator().toList()
            .filter { it.outboundType != "dns" }
            .map { Connection.from(it) }
            .filter { it.performSearch(currentState.searchText) }

        return ConnectionLists(
            connections = connectionList,
            allConnections = allConnectionList,
        )
    }

    private data class ConnectionLists(
        val connections: List<Connection>,
        val allConnections: List<Connection>,
    )
}
