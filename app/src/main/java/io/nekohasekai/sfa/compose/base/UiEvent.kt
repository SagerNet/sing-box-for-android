package io.nekohasekai.sfa.compose.base

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Base sealed class for all UI events in the application.
 * These are one-time events that should trigger UI actions.
 */
sealed class UiEvent {
    data class ErrorMessage(val message: String) : UiEvent()

    data class OpenUrl(val url: String) : UiEvent()

    data class EditProfile(val profileId: Long) : UiEvent()

    object RequestStartService : UiEvent()

    object RequestReconnectService : UiEvent()

    object RestartToTakeEffect : UiEvent()
}

/**
 * Interface for screen-specific events that don't need global handling
 */
interface ScreenEvent

interface EventHandler<T : UiEvent> {
    val events: SharedFlow<T>

    suspend fun sendEvent(event: T)
}

class UiEventHandler<T : UiEvent> : EventHandler<T> {
    private val _events = MutableSharedFlow<T>()
    override val events: SharedFlow<T> = _events.asSharedFlow()

    override suspend fun sendEvent(event: T) {
        _events.emit(event)
    }
}
