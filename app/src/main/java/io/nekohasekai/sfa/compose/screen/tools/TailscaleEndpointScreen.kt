package io.nekohasekai.sfa.compose.screen.tools

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.component.qr.QRCodeDialog
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.compose.util.QRCodeGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TailscaleEndpointScreen(
    navController: NavController,
    viewModel: TailscaleStatusViewModel,
    endpointTag: String,
) {
    OverrideTopBar {
        TopAppBar(
            title = { Text(endpointTag) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                }
            },
        )
    }

    val state by viewModel.uiState.collectAsState()
    val endpoint = state.endpoints.firstOrNull { it.endpointTag == endpointTag }

    if (endpoint == null) {
        LaunchedEffect(Unit) {
            navController.navigateUp()
        }
        return
    }

    val context = LocalContext.current
    var showAuthQRCode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        val hasNetwork = endpoint.networkName.isNotEmpty()
        val hasMagicDNS = endpoint.magicDNSSuffix.isNotEmpty()
        val hasExitNode = endpoint.backendState == "Running" && endpoint.hasExitNodeCandidates
        val hasAuth = endpoint.authURL.isNotEmpty()

        // Status section
        SectionHeader(stringResource(R.string.tailscale_status))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column {
                val stateIsLast = !hasNetwork && !hasMagicDNS && !hasExitNode && !hasAuth
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.tailscale_state),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(stateColor(endpoint.backendState)),
                            )
                            Text(
                                endpoint.backendState,
                                style = MaterialTheme.typography.bodyMedium,
                                color = stateColor(endpoint.backendState),
                            )
                        }
                    },
                    modifier = Modifier.clip(
                        if (stateIsLast) {
                            RoundedCornerShape(12.dp)
                        } else {
                            RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        },
                    ),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                if (hasNetwork) {
                    val networkIsLast = !hasMagicDNS && !hasExitNode && !hasAuth
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.tailscale_network),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                endpoint.networkName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = if (networkIsLast) {
                            Modifier.clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                        } else {
                            Modifier
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                if (hasMagicDNS) {
                    val magicDNSIsLast = !hasExitNode && !hasAuth
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.tailscale_magic_dns),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                endpoint.magicDNSSuffix,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = if (magicDNSIsLast) {
                            Modifier.clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                        } else {
                            Modifier
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                if (hasExitNode) {
                    val exitNodeIsLast = !hasAuth
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.tailscale_exit_node),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                endpoint.exitNode?.hostName ?: stringResource(R.string.disabled),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        },
                        modifier = Modifier
                            .clip(
                                if (exitNodeIsLast) {
                                    RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                                } else {
                                    RoundedCornerShape(0.dp)
                                },
                            )
                            .clickable {
                                navController.navigate(
                                    "tools/tailscale/${Uri.encode(endpointTag)}/exit_node",
                                )
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                if (hasAuth) {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.tailscale_open_auth_url),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(endpoint.authURL)))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.tailscale_open_auth_url_qr_code),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.QrCode2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                            .clickable { showAuthQRCode = true },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }

        // This Device section
        if (endpoint.backendState == "Running" && endpoint.selfPeer != null) {
            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(stringResource(R.string.tailscale_this_device))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                PeerItem(
                    peer = endpoint.selfPeer,
                    onClick = {
                        navController.navigate(
                            "tools/tailscale/${Uri.encode(endpointTag)}/peer/${Uri.encode(endpoint.selfPeer.id)}",
                        )
                    },
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                )
            }
        }

        // User group sections
        for (group in endpoint.userGroups) {
            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(group.displayName.ifEmpty { group.loginName })
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column {
                    group.peers.forEachIndexed { index, peer ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        PeerItem(
                            peer = peer,
                            onClick = {
                                navController.navigate(
                                    "tools/tailscale/${Uri.encode(endpointTag)}/peer/${Uri.encode(peer.id)}",
                                )
                            },
                            modifier = when {
                                group.peers.size == 1 -> Modifier.clip(RoundedCornerShape(12.dp))
                                index == 0 -> Modifier.clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                index == group.peers.lastIndex -> Modifier.clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                                else -> Modifier
                            },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showAuthQRCode && endpoint.authURL.isNotEmpty()) {
        val qrBitmap = QRCodeGenerator.rememberBitmap(endpoint.authURL)
        QRCodeDialog(
            bitmap = qrBitmap,
            onDismiss = { showAuthQRCode = false },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
}

@Composable
private fun PeerItem(
    peer: TailscalePeerData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val badges = peerBadges(peer)
    val firstIP = peer.tailscaleIPs.firstOrNull()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (peer.online) Color(0xFF4CAF50) else Color.Gray),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                peer.hostName,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (firstIP != null) {
                Text(
                    firstIP,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (badges.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (badge in badges) {
                        PeerBadgeView(badge)
                    }
                }
            }
        }
    }
}

private data class PeerBadge(val text: String, val color: Color)

@Composable
private fun peerBadges(peer: TailscalePeerData): List<PeerBadge> {
    val badges = mutableListOf<PeerBadge>()
    if (peer.shareeNode) {
        badges += PeerBadge(stringResource(R.string.tailscale_shared_in), Color(0xFFF44336))
    }
    if (peer.exitNodeOption) {
        badges += PeerBadge(stringResource(R.string.tailscale_exit_node), Color(0xFF2196F3))
    }
    when {
        peer.expired -> {
            badges += PeerBadge(stringResource(R.string.tailscale_expired), Color(0xFFF44336))
        }
        peer.keyExpiry > 0 -> {
            val expiryMs = peer.keyExpiry * 1000
            val now = System.currentTimeMillis()
            val oneMonthMs = 30L * 24 * 60 * 60 * 1000
            if (expiryMs - now <= oneMonthMs) {
                val rel = DateUtils.getRelativeTimeSpanString(
                    expiryMs,
                    now,
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString()
                badges += PeerBadge(stringResource(R.string.tailscale_expires_relative, rel), Color.Gray)
            }
        }
        else -> {
            badges += PeerBadge(stringResource(R.string.tailscale_key_expiry_disabled), Color.Gray)
        }
    }
    if (peer.sshHostKeys.isNotEmpty()) {
        badges += PeerBadge(stringResource(R.string.tailscale_ssh), Color(0xFF4CAF50))
    }
    return badges
}

@Composable
private fun PeerBadgeView(badge: PeerBadge) {
    Text(
        text = badge.text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(badge.color)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun stateColor(state: String): Color = when (state) {
    "Running" -> Color(0xFF4CAF50)
    "NeedsLogin", "NeedsMachineAuth" -> Color(0xFFFF9800)
    "Starting" -> Color(0xFFFFEB3B)
    else -> Color.Gray
}
