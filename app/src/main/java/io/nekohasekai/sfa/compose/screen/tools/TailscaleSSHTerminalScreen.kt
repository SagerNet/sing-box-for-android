package io.nekohasekai.sfa.compose.screen.tools

import android.graphics.Typeface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.sagernet.libghostty.compose.GhosttyDialogs
import io.github.sagernet.libghostty.compose.GhosttyExtraKeysBar
import io.github.sagernet.libghostty.compose.GhosttyProgressIndicator
import io.github.sagernet.libghostty.compose.GhosttyTerminal
import io.github.sagernet.libghostty.compose.rememberGhosttyTerminalState
import io.github.sagernet.libghostty.extras.GhosttyFontStore
import io.github.sagernet.libghostty.extras.GhosttyThemeStore
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.terminal.DEFAULT_SSH_TERMINAL_TYPE
import io.nekohasekai.sfa.terminal.GhosttyCustomConfig
import io.nekohasekai.sfa.terminal.ManagedSession
import io.nekohasekai.sfa.terminal.TailscaleSSHPresentedSession
import io.nekohasekai.sfa.terminal.TailscaleSSHSessionStore
import io.nekohasekai.sfa.terminal.TerminalSessionPhase

internal const val DEFAULT_SSH_USERNAME = "root"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TailscaleSSHTerminalScreen(
    navController: NavController,
    sharedViewModel: TailscaleSSHSharedViewModel,
    tailscaleViewModel: TailscaleStatusViewModel,
) {
    val sessionStore = TailscaleSSHSessionStore
    val state by sessionStore.state.collectAsState()
    val isDark = isSystemInDarkTheme()
    val keyboardController = LocalSoftwareKeyboardController.current

    val terminalState = rememberGhosttyTerminalState()

    LaunchedEffect(Unit) {
        val pending = sharedViewModel.consumePendingSession()
        if (pending != null) {
            sessionStore.addSession(pending)
        }
    }

    val activeSession = state.activeSession

    var showSessionMenu by remember { mutableStateOf(false) }
    var expandedNewSession by remember { mutableStateOf(false) }

    val tailscaleState by tailscaleViewModel.uiState.collectAsState()
    val quickConnectPeerIDs = Settings.tailscaleSSHQuickConnectPeers
    val activePeerAddress = activeSession?.presentedSession?.peerAddress
    val activeEndpointTag = activeSession?.presentedSession?.endpointTag
    val otherQuickConnectPeers = remember(tailscaleState, quickConnectPeerIDs, activePeerAddress, activeEndpointTag) {
        tailscaleState.endpoints.flatMap { endpoint ->
            endpoint.userGroups.flatMap { group ->
                group.peers.filter { peer ->
                    peer.online && peer.sshHostKeys.isNotEmpty() &&
                        peer.tailscaleIPs.isNotEmpty() &&
                        peer.id != endpoint.selfPeer?.id &&
                        quickConnectPeerIDs.contains(peer.stableID) &&
                        !(endpoint.endpointTag == activeEndpointTag && peer.tailscaleIPs.firstOrNull() == activePeerAddress)
                }.map { peer -> peer to endpoint.endpointTag }
            }
        }
    }

    OverrideTopBar {
        TopAppBar(
            title = {
                Column {
                    Text(
                        if (activeSession != null) sessionTitle(activeSession) else "",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val workingDirectory = if (activeSession != null) {
                        activeSession.terminalSession.workingDirectory.collectAsState().value
                    } else {
                        null
                    }
                    if (!workingDirectory.isNullOrEmpty()) {
                        Text(
                            workingDirectory,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
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
                        if (otherQuickConnectPeers.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tailscale_ssh_new_session)) },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                onClick = {
                                    showSessionMenu = false
                                    sessionStore.duplicateCurrentSession()
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
                                            sessionStore.duplicateCurrentSession()
                                        },
                                    )
                                }
                                val rememberedUsernames = Settings.tailscaleSSHRememberedUsernames
                                val rememberedTerminalTypes = Settings.tailscaleSSHRememberedTerminalTypes
                                otherQuickConnectPeers.forEach { (peer, endpointTag) ->
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
                                            sessionStore.addSession(
                                                TailscaleSSHPresentedSession(
                                                    endpointTag = endpointTag,
                                                    peerHostName = peer.hostName,
                                                    peerAddress = peer.tailscaleIPs.first(),
                                                    username = rememberedUsernames[peer.stableID]?.takeIf { it.isNotBlank() }
                                                        ?: DEFAULT_SSH_USERNAME,
                                                    terminalType = rememberedTerminalTypes[peer.stableID]?.takeIf { it.isNotBlank() }
                                                        ?: DEFAULT_SSH_TERMINAL_TYPE,
                                                    hostKeys = peer.sshHostKeys,
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        if (activeSession != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tailscale_ssh_close_session)) },
                                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                                onClick = {
                                    showSessionMenu = false
                                    expandedNewSession = false
                                    sessionStore.removeSession(activeSession.id)
                                    if (sessionStore.state.value.sessions.isEmpty()) {
                                        keyboardController?.hide()
                                        navController.navigateUp()
                                    }
                                },
                            )
                        }
                        if (state.sessions.size > 1) {
                            HorizontalDivider()
                            state.sessions.forEach { session ->
                                val isActive = session.id == state.activeSessionId
                                DropdownMenuItem(
                                    text = { Text(sessionTitle(session)) },
                                    onClick = {
                                        showSessionMenu = false
                                        sessionStore.switchSession(session.id)
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

    val scaffoldPadding = LocalScaffoldPadding.current

    Column(
        modifier = Modifier.fillMaxSize()
            .padding(scaffoldPadding)
            .imePadding(),
    ) {
        if (activeSession != null) {
            GhosttyProgressIndicator(
                activeSession.terminalSession,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(modifier = Modifier.weight(1f)) {
                val context = LocalContext.current
                val themeName = if (isDark) Settings.tailscaleSSHDarkTheme else Settings.tailscaleSSHLightTheme
                val customConfig = remember(themeName, isDark) {
                    if (themeName.isEmpty()) {
                        GhosttyCustomConfig.parse(
                            if (isDark) Settings.tailscaleSSHDarkConfig else Settings.tailscaleSSHLightConfig,
                        )
                    } else {
                        null
                    }
                }
                val theme = remember(themeName, isDark, customConfig) {
                    customConfig?.theme ?: GhosttyThemeStore.loadThemeOrDefault(context, themeName, isDark)
                }
                val typeface = remember(customConfig) {
                    if (Settings.tailscaleSSHFontFollowTheme) {
                        customConfig?.fontFamily
                            ?.takeIf { it.isNotBlank() }
                            ?.let { Typeface.create(it, Typeface.NORMAL) }
                    } else {
                        GhosttyFontStore.resolveTypeface(
                            Settings.tailscaleSSHFontFamily,
                            Settings.tailscaleSSHCustomFontPath,
                        )
                    } ?: Typeface.MONOSPACE
                }
                val fontSize = remember { Settings.tailscaleSSHFontSize }

                GhosttyTerminal(
                    session = activeSession.terminalSession,
                    state = terminalState,
                    modifier = Modifier.fillMaxSize(),
                    theme = theme,
                    darkColorScheme = isDark,
                    typeface = typeface,
                    fontSizeSp = fontSize.toFloat(),
                    onFontSizeChanged = { Settings.tailscaleSSHFontSize = it.toInt() },
                    onFinishedSessionKey = {
                        val active = sessionStore.state.value.activeSession
                        if (active != null) {
                            sessionStore.removeSession(active.id)
                            if (sessionStore.state.value.sessions.isEmpty()) {
                                keyboardController?.hide()
                                navController.navigateUp()
                            }
                        }
                    },
                )

                val terminalPhase = activeSession.phase.collectAsState().value
                val banner = activeSession.banner.collectAsState().value
                if (terminalPhase == TerminalSessionPhase.CONNECTING ||
                    (terminalPhase == TerminalSessionPhase.FINISHED && !banner.isNullOrBlank())
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (terminalPhase == TerminalSessionPhase.CONNECTING) {
                                CircularProgressIndicator()
                                Text(
                                    stringResource(R.string.tailscale_ssh_connecting),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                            }
                            if (!banner.isNullOrBlank()) {
                                val linkColor = MaterialTheme.colorScheme.primary
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                                        .widthIn(max = 480.dp),
                                ) {
                                    Text(
                                        remember(banner, linkColor) { bannerAnnotatedString(banner, linkColor) },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            GhosttyExtraKeysBar(
                terminalState,
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

    GhosttyDialogs(terminalState)

    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
        }
    }
}

@Composable
private fun sessionTitle(session: ManagedSession): String {
    val phase = session.phase.collectAsState().value
    val terminalTitle = session.terminalSession.title.collectAsState().value
    if (phase == TerminalSessionPhase.CONNECTING || terminalTitle.isNullOrBlank()) {
        return session.presentedSession.peerHostName
    }
    return terminalTitle
}

private val bannerUrlRegex = Regex("""https?://\S+""")

private fun bannerAnnotatedString(text: String, linkColor: Color) = buildAnnotatedString {
    var lastIndex = 0
    for (match in bannerUrlRegex.findAll(text)) {
        if (match.range.first > lastIndex) {
            append(text.substring(lastIndex, match.range.first))
        }
        val url = match.value
        withLink(
            LinkAnnotation.Url(
                url,
                TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
            ),
        ) {
            append(url)
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}
