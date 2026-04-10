package io.nekohasekai.sfa.compose.screen.tools

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.LineChart
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.ktx.clipboardText
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TailscalePeerScreen(
    navController: NavController,
    viewModel: TailscaleStatusViewModel,
    endpointTag: String,
    peerId: String,
) {
    val state by viewModel.uiState.collectAsState()
    val peer = viewModel.peer(endpointTag, peerId)
    val isSelf = viewModel.endpoint(endpointTag)?.selfPeer?.id == peerId
    val pingViewModel: TailscalePingViewModel = viewModel()
    val pingState by pingViewModel.uiState.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            if (pingState.isRunning) {
                pingViewModel.stopPing()
            }
        }
    }

    OverrideTopBar {
        TopAppBar(
            title = {
                Column {
                    Text(
                        peer?.hostName ?: "",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (peer?.online == true) Color(0xFF4CAF50) else Color.Gray,
                                ),
                        )
                        Text(
                            if (peer?.online == true) {
                                stringResource(R.string.tailscale_connected)
                            } else {
                                stringResource(R.string.tailscale_not_connected)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                }
            },
        )
    }

    if (peer == null) {
        LaunchedEffect(Unit) {
            navController.navigateUp()
        }
        return
    }

    var copiedAddress by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        // Tailscale Addresses section
        SectionHeader(stringResource(R.string.tailscale_addresses))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (peer.dnsName.isNotEmpty()) {
                    AddressRow(
                        address = Libbox.formatFQDN(peer.dnsName),
                        label = stringResource(R.string.tailscale_magic_dns),
                        copied = copiedAddress,
                        onCopy = { copiedAddress = it },
                    )
                }
                for (ip in peer.tailscaleIPs) {
                    AddressRow(
                        address = ip,
                        label = if (ip.contains(":")) {
                            stringResource(R.string.tailscale_ipv6)
                        } else {
                            stringResource(R.string.tailscale_ipv4)
                        },
                        copied = copiedAddress,
                        onCopy = { copiedAddress = it },
                    )
                }
            }
        }

        // Ping section (not for self peer)
        if (!isSelf && peer.online && peer.tailscaleIPs.isNotEmpty()) {
            val peerIP = peer.tailscaleIPs.first()

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tailscale_ping),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Surface(
                    onClick = {
                        if (pingState.isRunning) {
                            pingViewModel.stopPing()
                        } else {
                            pingViewModel.startPing(endpointTag, peerIP)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSystemInDarkTheme()) {
                        lerp(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            0.5f,
                        )
                    } else {
                        MaterialTheme.colorScheme.surfaceDim
                    },
                    modifier = Modifier.size(width = 44.dp, height = 32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (pingState.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (pingState.isRunning) {
                                stringResource(R.string.tailscale_ping_stop)
                            } else {
                                stringResource(R.string.tailscale_ping_start)
                            },
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    if (pingState.hasResult) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (pingState.isDirect) {
                                Text(
                                    text = "\u2192 ",
                                    color = Color(0xFF4CAF50),
                                )
                                Text(
                                    text = stringResource(R.string.tailscale_ping_direct),
                                    color = Color(0xFF4CAF50),
                                )
                            } else {
                                Text(
                                    text = "\u21BB ",
                                    color = Color(0xFFFF9800),
                                )
                                Text(
                                    text = stringResource(R.string.tailscale_ping_derp),
                                    color = Color(0xFFFF9800),
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${pingState.latencyMs.toInt()} ms",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (pingState.isRunning && pingState.latencyHistory.size > 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LineChart(
                                    data = pingState.latencyHistory,
                                    lineColor = if (pingState.isDirect) {
                                        Color(0xFF4CAF50)
                                    } else {
                                        Color(0xFF2196F3)
                                    },
                                    animate = false,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val maxMs = (
                                    (
                                        pingState.latencyHistory.maxOrNull()
                                            ?: 1f
                                        ) * 1.2f
                                    ).toInt().coerceAtLeast(1)
                                Column(
                                    modifier = Modifier.height(80.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "${maxMs}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "${maxMs * 2 / 3}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "${maxMs / 3}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "0ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No data",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Details section
        val showDetails = peer.keyExpiry > 0 || peer.os.isNotEmpty() || peer.exitNode
        if (showDetails) {
            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(stringResource(R.string.tailscale_details))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                ),
            ) {
                val context = LocalContext.current
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (peer.keyExpiry > 0) {
                        val expiryText = DateUtils.getRelativeTimeSpanString(
                            peer.keyExpiry * 1000,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                        ).toString()
                        DetailRow(
                            label = stringResource(R.string.tailscale_key_expiry),
                            value = expiryText,
                        )
                    }
                    if (peer.os.isNotEmpty()) {
                        DetailRow(
                            label = stringResource(R.string.tailscale_os),
                            value = peer.os,
                        )
                    }
                    if (peer.exitNode) {
                        DetailRow(
                            label = stringResource(R.string.tailscale_exit_node),
                            value = stringResource(R.string.tailscale_active),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    LaunchedEffect(copiedAddress) {
        if (copiedAddress != null) {
            delay(2000)
            copiedAddress = null
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AddressRow(
    address: String,
    label: String,
    copied: String?,
    onCopy: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                address,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = {
            clipboardText = address
            onCopy(address)
        }) {
            if (copied == address) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
