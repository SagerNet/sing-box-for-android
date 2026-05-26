package io.nekohasekai.sfa.compose.screen.tools

import io.nekohasekai.sfa.compose.base.BaseViewModel
import io.nekohasekai.sfa.terminal.TailscaleSSHPresentedSession

data class TailscaleSSHSharedState(
    val pendingSession: TailscaleSSHPresentedSession? = null,
)

class TailscaleSSHSharedViewModel : BaseViewModel<TailscaleSSHSharedState, Nothing>() {
    override fun createInitialState() = TailscaleSSHSharedState()

    fun setPendingSession(session: TailscaleSSHPresentedSession) {
        updateState { copy(pendingSession = session) }
    }

    fun consumePendingSession(): TailscaleSSHPresentedSession? {
        val session = currentState.pendingSession
        updateState { copy(pendingSession = null) }
        return session
    }
}
