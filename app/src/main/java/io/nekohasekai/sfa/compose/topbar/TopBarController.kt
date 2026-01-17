package io.nekohasekai.sfa.compose.topbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

internal data class TopBarEntry(val key: Any, val content: @Composable () -> Unit)

class TopBarController internal constructor(private val state: MutableState<List<TopBarEntry>>) {
    val current: (@Composable () -> Unit)? get() = state.value.lastOrNull()?.content

    fun set(key: Any, content: @Composable () -> Unit) {
        state.value = state.value.filterNot { it.key == key } + TopBarEntry(key, content)
    }

    fun clear(key: Any) {
        state.value = state.value.filterNot { it.key == key }
    }
}

val LocalTopBarController = compositionLocalOf<TopBarController> {
    error("TopBarController not provided")
}

@Composable
fun OverrideTopBar(content: @Composable () -> Unit) {
    val controller = LocalTopBarController.current
    val token = remember { Any() }
    val currentContent = rememberUpdatedState(content)
    DisposableEffect(controller, token) {
        controller.set(token) { currentContent.value() }
        onDispose { controller.clear(token) }
    }
}
