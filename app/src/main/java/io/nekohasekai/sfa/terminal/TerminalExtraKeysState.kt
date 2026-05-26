package io.nekohasekai.sfa.terminal

import com.termux.terminal.KeyHandler
import kotlinx.coroutines.flow.MutableStateFlow

class TerminalExtraKeysState {

    enum class StickyModifierState { INACTIVE, ARMED, LOCKED }

    val ctrlState = MutableStateFlow(StickyModifierState.INACTIVE)
    val altState = MutableStateFlow(StickyModifierState.INACTIVE)

    val isCtrlActive: Boolean get() = ctrlState.value != StickyModifierState.INACTIVE
    val isAltActive: Boolean get() = altState.value != StickyModifierState.INACTIVE

    private var lastCtrlTapTime = 0L
    private var lastAltTapTime = 0L

    fun toggleCtrl(currentTimeMs: Long) {
        ctrlState.value = toggleModifier(ctrlState.value, currentTimeMs, lastCtrlTapTime)
        lastCtrlTapTime = currentTimeMs
    }

    fun toggleAlt(currentTimeMs: Long) {
        altState.value = toggleModifier(altState.value, currentTimeMs, lastAltTapTime)
        lastAltTapTime = currentTimeMs
    }

    fun consumeModifiers() {
        if (ctrlState.value == StickyModifierState.ARMED) {
            ctrlState.value = StickyModifierState.INACTIVE
        }
        if (altState.value == StickyModifierState.ARMED) {
            altState.value = StickyModifierState.INACTIVE
        }
    }

    fun currentKeyMod(): Int {
        var mod = 0
        if (isCtrlActive) mod = mod or KeyHandler.KEYMOD_CTRL
        if (isAltActive) mod = mod or KeyHandler.KEYMOD_ALT
        return mod
    }

    private fun toggleModifier(
        current: StickyModifierState,
        currentTimeMs: Long,
        lastTapTime: Long,
    ): StickyModifierState = when (current) {
        StickyModifierState.INACTIVE -> StickyModifierState.ARMED
        StickyModifierState.ARMED -> {
            if (currentTimeMs - lastTapTime < DOUBLE_TAP_THRESHOLD_MS) {
                StickyModifierState.LOCKED
            } else {
                StickyModifierState.INACTIVE
            }
        }
        StickyModifierState.LOCKED -> StickyModifierState.INACTIVE
    }

    companion object {
        private const val DOUBLE_TAP_THRESHOLD_MS = 300L
    }
}
