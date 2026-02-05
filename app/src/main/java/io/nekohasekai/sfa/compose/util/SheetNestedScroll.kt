package io.nekohasekai.sfa.compose.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity

@Composable
fun rememberSheetDismissFromContentOnlyIfGestureStartedAtTopModifier(isAtTop: () -> Boolean): Modifier {
    val isAtTopState = rememberUpdatedState(isAtTop)
    val gestureStartedAtTop = remember { mutableStateOf(true) }

    val nestedScrollConnection =
        remember {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    val startedAtTop = gestureStartedAtTop.value
                    return when {
                        available.y < 0 -> available
                        available.y > 0 && !startedAtTop -> available
                        else -> Offset.Zero
                    }
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    val startedAtTop = gestureStartedAtTop.value
                    return when {
                        available.y < 0 -> available
                        available.y > 0 && !startedAtTop -> available
                        else -> Velocity.Zero
                    }
                }
            }
        }

    val gestureGateModifier =
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                gestureStartedAtTop.value = isAtTopState.value.invoke()
                do {
                    val event = awaitPointerEvent()
                } while (event.changes.any { it.pressed })
            }
        }

    return gestureGateModifier.nestedScroll(nestedScrollConnection)
}
