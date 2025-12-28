package io.nekohasekai.sfa.compose.screen.connections

import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.Connections
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.compose.base.BaseViewModel
import io.nekohasekai.sfa.compose.base.ScreenEvent
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.ktx.toList
import io.nekohasekai.sfa.ui.connections.Connection
import io.nekohasekai.sfa.ui.connections.ConnectionSort
import io.nekohasekai.sfa.ui.connections.ConnectionStateFilter
import io.nekohasekai.sfa.utils.CommandClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConnectionsUiState(
    val connections: List<Connection> = emptyList(),
    val allConnections: List<Connection> = emptyList(),
    val isLoading: Boolean = false,
    val stateFilter: ConnectionStateFilter = ConnectionStateFilter.Active,
    val sort: ConnectionSort = ConnectionSort.ByDate,
)

sealed class ConnectionsEvent : ScreenEvent {
    data class ConnectionClosed(val id: String) : ConnectionsEvent()
    data object AllConnectionsClosed : ConnectionsEvent()
}

class ConnectionsViewModel(
    private val sharedCommandClient: CommandClient? = null,
) : BaseViewModel<ConnectionsUiState, ConnectionsEvent>(), CommandClient.Handler {
    private val commandClient: CommandClient
    private val isUsingSharedClient: Boolean

    private val _serviceStatus = MutableStateFlow(Status.Stopped)
    val serviceStatus = _serviceStatus.asStateFlow()
    private var lastServiceStatus: Status = Status.Stopped
    private var connectionJob: Job? = null

    private var rawConnections: Connections? = null

    init {
        if (sharedCommandClient != null) {
            commandClient = sharedCommandClient
            isUsingSharedClient = true
            commandClient.addHandler(this)
        } else {
            commandClient = CommandClient(
                viewModelScope,
                CommandClient.ConnectionType.Connections,
                this,
            )
            isUsingSharedClient = false
        }
    }

    override fun createInitialState() = ConnectionsUiState()

    override fun onCleared() {
        super.onCleared()
        connectionJob?.cancel()
        connectionJob = null
        if (isUsingSharedClient) {
            commandClient.removeHandler(this)
        } else {
            commandClient.disconnect()
        }
    }

    private fun handleServiceStatusChange(status: Status) {
        if (status == Status.Started) {
            if (!isUsingSharedClient) {
                updateState { copy(isLoading = true) }
                connectionJob?.cancel()
                connectionJob = viewModelScope.launch(Dispatchers.IO) {
                    while (isActive) {
                        try {
                            commandClient.connect()
                            break
                        } catch (e: Exception) {
                            delay(100)
                        }
                    }
                }
            }
        } else {
            connectionJob?.cancel()
            connectionJob = null
            if (!isUsingSharedClient) {
                commandClient.disconnect()
            }
            rawConnections = null
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
        rawConnections?.let { processConnections(it) }
    }

    fun setSort(sort: ConnectionSort) {
        updateState { copy(sort = sort) }
        rawConnections?.let { processConnections(it) }
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
        viewModelScope.launch(Dispatchers.Main) {
            rawConnections = null
            updateState {
                copy(connections = emptyList(), allConnections = emptyList(), isLoading = false)
            }
        }
    }

    override fun updateConnections(connections: Connections) {
        rawConnections = connections
        processConnections(connections)
    }

    private fun processConnections(connections: Connections) {
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch(Dispatchers.Default) {
            val currentState = uiState.value

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

            withContext(Dispatchers.Main) {
                updateState {
                    copy(
                        connections = connectionList,
                        allConnections = allConnectionList,
                        isLoading = false,
                    )
                }
            }
        }
    }
}
