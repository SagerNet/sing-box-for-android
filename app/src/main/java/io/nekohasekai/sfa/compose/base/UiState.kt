package io.nekohasekai.sfa.compose.base

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()

    data class Success<T>(val data: T) : UiState<T>()

    data class Error(val exception: Throwable, val message: String? = null) : UiState<Nothing>()
}

data class BaseUiState<T>(
    val isLoading: Boolean = false,
    val data: T? = null,
    val error: String? = null,
)
