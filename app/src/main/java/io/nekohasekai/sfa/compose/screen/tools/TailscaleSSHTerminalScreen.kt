package io.nekohasekai.sfa.compose.screen.tools

import android.content.Context
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.terminal.ImportedFontStore
import io.nekohasekai.sfa.terminal.TailscaleSSHPresentedSession
import io.nekohasekai.sfa.terminal.TailscaleSSHTerminalSession
import io.nekohasekai.sfa.terminal.TerminalColorSchemeLoader
import io.nekohasekai.sfa.terminal.TerminalExtraKeysState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TailscaleSSHTerminalScreen(
    navController: NavController,
    sharedViewModel: TailscaleSSHSharedViewModel,
    tailscaleViewModel: TailscaleStatusViewModel,
) {
    val terminalViewModel: TailscaleSSHTerminalViewModel = viewModel()
    val state by terminalViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val keyboardController = LocalSoftwareKeyboardController.current

    val terminalViewRef = remember { TerminalViewRef() }
    val extraKeysState = remember { TerminalExtraKeysState() }
    val sessionClient = remember { createSessionClient(terminalViewModel, terminalViewRef) }

    LaunchedEffect(Unit) {
        terminalViewModel.sessionClient = sessionClient
        val pending = sharedViewModel.consumePendingSession()
        if (pending != null && state.sessions.isEmpty()) {
            terminalViewModel.addSession(pending)
        }
    }

    val activeSession = state.activeSession
    val displayTitle = when {
        activeSession == null -> ""
        activeSession.terminalSession.phase == TailscaleSSHTerminalSession.Phase.CONNECTING ->
            activeSession.presentedSession.peerHostName
        else -> {
            val termTitle = activeSession.terminalSession.title
            if (termTitle.isNullOrBlank()) activeSession.presentedSession.peerHostName else termTitle
        }
    }

    var showSessionMenu by remember { mutableStateOf(false) }
    var expandedNewSession by remember { mutableStateOf(false) }

    val tailscaleState by tailscaleViewModel.uiState.collectAsState()
    val quickConnectPeerIDs = Settings.tailscaleSSHQuickConnectPeers
    val otherQCPeers = remember(tailscaleState, quickConnectPeerIDs, activeSession) {
        val currentAddress = activeSession?.presentedSession?.peerAddress
        val currentEndpointTag = activeSession?.presentedSession?.endpointTag
        tailscaleState.endpoints.flatMap { endpoint ->
            endpoint.userGroups.flatMap { group ->
                group.peers.filter { peer ->
                    peer.online && peer.sshHostKeys.isNotEmpty() &&
                        peer.tailscaleIPs.isNotEmpty() &&
                        peer.id != endpoint.selfPeer?.id &&
                        quickConnectPeerIDs.contains(peer.stableID) &&
                        !(endpoint.endpointTag == currentEndpointTag && peer.tailscaleIPs.firstOrNull() == currentAddress)
                }.map { peer -> peer to endpoint.endpointTag }
            }
        }
    }

    OverrideTopBar {
        TopAppBar(
            title = { Text(displayTitle, style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                IconButton(onClick = {
                    keyboardController?.hide()
                    navController.navigateUp()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { showSessionMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showSessionMenu,
                        onDismissRequest = {
                            showSessionMenu = false
                            expandedNewSession = false
                        },
                    ) {
                        if (otherQCPeers.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tailscale_ssh_new_session)) },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                onClick = {
                                    showSessionMenu = false
                                    terminalViewModel.duplicateCurrentSession()
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tailscale_ssh_new_session)) },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                trailingIcon = {
                                    Icon(
                                        if (expandedNewSession) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                    )
                                },
                                onClick = { expandedNewSession = !expandedNewSession },
                            )
                            if (expandedNewSession) {
                                if (activeSession != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                activeSession.presentedSession.peerHostName,
                                                modifier = Modifier.padding(start = 24.dp),
                                            )
                                        },
                                        onClick = {
                                            showSessionMenu = false
                                            expandedNewSession = false
                                            terminalViewModel.duplicateCurrentSession()
                                        },
                                    )
                                }
                                otherQCPeers.forEach { (peer, endpointTag) ->
                                    val usernames = Settings.tailscaleSSHRememberedUsernames
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                peer.hostName,
                                                modifier = Modifier.padding(start = 24.dp),
                                            )
                                        },
                                        onClick = {
                                            showSessionMenu = false
                                            expandedNewSession = false
                                            terminalViewModel.addSession(
                                                TailscaleSSHPresentedSession(
                                                    endpointTag = endpointTag,
                                                    peerHostName = peer.hostName,
                                                    peerAddress = peer.tailscaleIPs.first(),
                                                    username = usernames[peer.stableID]?.takeIf { it.isNotBlank() } ?: "root",
                                                    hostKeys = peer.sshHostKeys,
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        if (state.sessions.size > 1) {
                            HorizontalDivider()
                            state.sessions.forEach { session ->
                                val isActive = session.id == state.activeSessionId
                                val sessionTitle = when {
                                    session.terminalSession.phase == TailscaleSSHTerminalSession.Phase.CONNECTING ->
                                        session.presentedSession.peerHostName
                                    else -> {
                                        val termTitle = session.terminalSession.title
                                        if (termTitle.isNullOrBlank()) session.presentedSession.peerHostName else termTitle
                                    }
                                }
                                DropdownMenuItem(
                                    text = { Text(sessionTitle) },
                                    onClick = {
                                        showSessionMenu = false
                                        terminalViewModel.switchSession(session.id)
                                    },
                                    leadingIcon = {
                                        RadioButton(
                                            selected = isActive,
                                            onClick = null,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        if (activeSession != null) {
            Box(modifier = Modifier.weight(1f)) {
                val themeName = if (isDark) Settings.tailscaleSSHDarkTheme else Settings.tailscaleSSHLightTheme
                val fontSize = Settings.tailscaleSSHFontSize
                val fontFamily = Settings.tailscaleSSHFontFamily
                val customFontPath = Settings.tailscaleSSHCustomFontPath

                AndroidView(
                    factory = { ctx ->
                        TerminalColorSchemeLoader.applyScheme(ctx, themeName)
                        TerminalView(ctx, null).apply {
                            isFocusable = true
                            isFocusableInTouchMode = true
                            terminalViewRef.view = this
                            setTerminalViewClient(
                                createViewClient(ctx, this, extraKeysState) {
                                    val active = terminalViewModel.uiState.value.activeSession
                                    if (active != null) {
                                        terminalViewModel.removeSession(active.id)
                                        if (terminalViewModel.uiState.value.sessions.isEmpty()) {
                                            keyboardController?.hide()
                                            navController.navigateUp()
                                        }
                                    }
                                },
                            )
                            attachSession(activeSession.terminalSession)
                            setTextSize(fontSize)
                            val typeface = resolveTypeface(fontFamily, customFontPath)
                            if (typeface != null) {
                                setTypeface(typeface)
                            }
                            post {
                                requestFocus()
                                val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                            }
                        }
                    },
                    update = { view ->
                        if (view.currentSession !== activeSession.terminalSession) {
                            TerminalColorSchemeLoader.applyScheme(context, themeName)
                            view.attachSession(activeSession.terminalSession)
                            view.onScreenUpdated()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (activeSession.terminalSession.phase == TailscaleSSHTerminalSession.Phase.CONNECTING) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(
                                stringResource(R.string.tailscale_ssh_connecting),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                }
            }
            TerminalExtraKeysBar(
                terminalView = terminalViewRef.view,
                extraKeysState = extraKeysState,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (state.sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.tailscale_ssh_no_sessions),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
        }
    }
}

private class TerminalViewRef {
    var view: TerminalView? = null
}

private fun resolveTypeface(fontFamily: String, customFontPath: String): Typeface? {
    if (customFontPath.isNotBlank()) {
        val typeface = ImportedFontStore.loadTypeface(customFontPath)
        if (typeface != null) return typeface
    }
    if (fontFamily.isNotBlank()) {
        return Typeface.create(fontFamily, Typeface.NORMAL)
    }
    return null
}

private fun createSessionClient(viewModel: TailscaleSSHTerminalViewModel, viewRef: TerminalViewRef): TerminalSessionClient = object : TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) {
        viewRef.view?.onScreenUpdated()
    }
    override fun onTitleChanged(changedSession: TerminalSession) {
        viewModel.onTitleChanged()
    }
    override fun onSessionFinished(finishedSession: TerminalSession) {
        val state = viewModel.uiState.value
        if (state.sessions.size > 1) {
            val managed = state.sessions.firstOrNull { it.terminalSession === finishedSession }
            if (managed != null) {
                if (managed.id == state.activeSessionId) {
                    val sshSession = finishedSession as TailscaleSSHTerminalSession
                    if (sshSession.getSSHExitCode() != 0) {
                        return
                    }
                }
                viewModel.removeSession(managed.id)
            }
        }
    }
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
    override fun onPasteTextFromClipboard(session: TerminalSession?) {}
    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
    override fun getTerminalCursorStyle(): Int = 0
    override fun logError(tag: String, message: String) {}
    override fun logWarn(tag: String, message: String) {}
    override fun logInfo(tag: String, message: String) {}
    override fun logDebug(tag: String, message: String) {}
    override fun logVerbose(tag: String, message: String) {}
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
    override fun logStackTrace(tag: String, e: Exception) {}
}

private fun createViewClient(context: Context, terminalView: TerminalView, extraKeysState: TerminalExtraKeysState, onDismissFinishedSession: () -> Unit): TerminalViewClient = object : TerminalViewClient {
    override fun onScale(scale: Float): Float {
        if (scale < 0.9f || scale > 1.1f) {
            val increase = scale > 1f
            var currentSize = Settings.tailscaleSSHFontSize
            currentSize = if (increase) {
                (currentSize + 1).coerceAtMost(48)
            } else {
                (currentSize - 1).coerceAtLeast(8)
            }
            Settings.tailscaleSSHFontSize = currentSize
            terminalView.setTextSize(currentSize)
            return 1.0f
        }
        return scale
    }
    override fun onSingleTapUp(e: MotionEvent) {
        terminalView.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(terminalView, 0)
    }
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (!session.isRunning) {
            onDismissFinishedSession()
            return true
        }
        return false
    }
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun readControlKey(): Boolean = extraKeysState.isCtrlActive
    override fun readAltKey(): Boolean = extraKeysState.isAltActive
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        if (!session.isRunning) {
            onDismissFinishedSession()
            return true
        }
        return false
    }
    override fun onEmulatorSet() {}
    override fun logError(tag: String, message: String) {}
    override fun logWarn(tag: String, message: String) {}
    override fun logInfo(tag: String, message: String) {}
    override fun logDebug(tag: String, message: String) {}
    override fun logVerbose(tag: String, message: String) {}
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
    override fun logStackTrace(tag: String, e: Exception) {}
}
