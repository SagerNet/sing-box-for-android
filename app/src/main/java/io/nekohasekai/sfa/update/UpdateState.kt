package io.nekohasekai.sfa.update

import androidx.compose.runtime.mutableStateOf

object UpdateState {
    val hasUpdate = mutableStateOf(false)
    val updateInfo = mutableStateOf<UpdateInfo?>(null)
    val isChecking = mutableStateOf(false)

    fun setUpdate(info: UpdateInfo?) {
        updateInfo.value = info
        hasUpdate.value = info != null
    }

    fun clear() {
        hasUpdate.value = false
        updateInfo.value = null
    }
}
