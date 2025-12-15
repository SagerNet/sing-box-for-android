package io.nekohasekai.sfa.compose.base

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Global event bus that aggregates events from all ViewModels.
 * This allows ComposeActivity to handle all events in a centralized manner.
 */
object GlobalEventBus {
    private val _events =
        MutableSharedFlow<UiEvent>(
            replay = 0,
            extraBufferCapacity = 10,
        )

    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    /**
     * Emit an event to the global event bus.
     * This should be called by ViewModels to send events that need global handling.
     */
    suspend fun emit(event: UiEvent) {
        _events.emit(event)
    }

    /**
     * Try to emit an event without suspending.
     * Returns true if the event was emitted successfully.
     */
    fun tryEmit(event: UiEvent): Boolean {
        return _events.tryEmit(event)
    }
}
