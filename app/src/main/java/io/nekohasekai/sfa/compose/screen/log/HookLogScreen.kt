package io.nekohasekai.sfa.compose.screen.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.constant.Status

@Composable
fun HookLogScreen(onBack: () -> Unit) {
    val viewModel: HookLogViewModel = viewModel()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadLogs(context)
    }

    LogScreen(
        serviceStatus = Status.Stopped,
        showStartFab = false,
        showStatusBar = false,
        title = context.getString(R.string.title_log),
        viewModel = viewModel,
        showPause = false,
        showClear = false,
        showStatusInfo = false,
        emptyMessage = context.getString(R.string.privilege_settings_hook_logs_empty),
        saveFilePrefix = "hook_logs",
        onBack = onBack,
    )
}
