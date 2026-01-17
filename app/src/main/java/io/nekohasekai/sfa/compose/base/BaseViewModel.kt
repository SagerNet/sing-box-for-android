package io.nekohasekai.sfa.compose.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel<State, Event> : ViewModel() {
    private val _uiState: MutableStateFlow<State> by lazy { MutableStateFlow(createInitialState()) }
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    abstract fun createInitialState(): State

    protected val currentState: State
        get() = _uiState.value

    protected fun updateState(reducer: State.() -> State) {
        _uiState.value = _uiState.value.reducer()
    }

    /**
     * Send an event that will be handled locally by the screen.
     * For global events, use sendGlobalEvent() instead.
     */
    protected fun sendEvent(event: Event) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    /**
     * Send a global UI event that will be handled by ComposeActivity.
     * This is a convenience method for sending UiEvents to the global bus.
     */
    fun sendGlobalEvent(event: UiEvent) {
        viewModelScope.launch {
            GlobalEventBus.emit(event)
        }
    }

    /**
     * Send an error event to be displayed as a dialog.
     * This is a convenience method for the common error handling case.
     */
    protected fun sendErrorMessage(message: String) {
        sendGlobalEvent(UiEvent.ErrorMessage(message))
    }

    protected fun launch(onError: ((Throwable) -> Unit)? = null, block: suspend CoroutineScope.() -> Unit) {
        val errorHandler =
            CoroutineExceptionHandler { _, throwable ->
                onError?.invoke(throwable) ?: sendError(throwable)
            }

        viewModelScope.launch(errorHandler, block = block)
    }

    /**
     * Convenience method to handle exceptions with a custom fallback message
     */
    protected fun sendError(throwable: Throwable) {
        sendErrorMessage(throwable.message ?: "An unknown error occurred")
    }
}
