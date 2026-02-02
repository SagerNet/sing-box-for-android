package io.nekohasekai.sfa.compose.screen.connections

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.model.Connection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConnectionDetailsScreen(
    connection: Connection,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
) {
    val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    var showMenu by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val bounceBlockingConnection = rememberBounceBlockingNestedScrollConnection(scrollState)

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(bounceBlockingConnection)
            .verticalScroll(scrollState),
    ) {
        if (showHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back),
                    )
                }
                Text(
                    text = stringResource(R.string.connection_details),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (connection.isActive) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.connection_close)) },
                                onClick = {
                                    onClose()
                                    showMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }

        DetailSection(title = stringResource(R.string.connection_section_basic)) {
            DetailRow(
                label = stringResource(R.string.connection_state),
                value = if (connection.isActive) {
                    stringResource(R.string.connection_state_active)
                } else {
                    stringResource(R.string.connection_state_closed)
                },
                valueColor = if (connection.isActive) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            DetailRow(
                label = stringResource(R.string.connection_created_at),
                value = dateTimeFormat.format(Date(connection.createdAt)),
            )
            if (!connection.isActive && connection.closedAt != null) {
                DetailRow(
                    label = stringResource(R.string.connection_closed_at),
                    value = dateTimeFormat.format(Date(connection.closedAt)),
                )
                DetailRow(
                    label = stringResource(R.string.connection_duration),
                    value = Libbox.formatDuration(connection.closedAt - connection.createdAt),
                )
            }
            DetailRow(
                label = stringResource(R.string.connection_uplink),
                value = Libbox.formatBytes(connection.uploadTotal),
            )
            DetailRow(
                label = stringResource(R.string.connection_downlink),
                value = Libbox.formatBytes(connection.downloadTotal),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        DetailSection(title = stringResource(R.string.connection_section_metadata)) {
            DetailRow(
                label = stringResource(R.string.connection_inbound),
                value = connection.inbound,
                monospace = true,
            )
            DetailRow(
                label = stringResource(R.string.connection_inbound_type),
                value = connection.inboundType,
                monospace = true,
            )
            DetailRow(
                label = stringResource(R.string.connection_ip_version),
                value = "IPv${connection.ipVersion}",
            )
            DetailRow(
                label = stringResource(R.string.connection_network),
                value = connection.network.uppercase(),
            )
            DetailRow(
                label = stringResource(R.string.connection_source),
                value = connection.source,
                monospace = true,
            )
            DetailRow(
                label = stringResource(R.string.connection_destination),
                value = connection.destination,
                monospace = true,
            )
            if (connection.domain.isNotEmpty()) {
                DetailRow(
                    label = stringResource(R.string.connection_domain),
                    value = connection.domain,
                    monospace = true,
                )
            }
            if (connection.protocolName.isNotEmpty()) {
                DetailRow(
                    label = stringResource(R.string.connection_protocol),
                    value = connection.protocolName,
                    monospace = true,
                )
            }
            if (connection.user.isNotEmpty()) {
                DetailRow(
                    label = stringResource(R.string.connection_user),
                    value = connection.user,
                    monospace = true,
                )
            }
            if (connection.fromOutbound.isNotEmpty()) {
                DetailRow(
                    label = stringResource(R.string.connection_from_outbound),
                    value = connection.fromOutbound,
                    monospace = true,
                )
            }
            if (connection.rule.isNotEmpty()) {
                DetailRow(
                    label = stringResource(R.string.connection_match_rule),
                    value = connection.rule,
                    monospace = true,
                )
            }
            DetailRow(
                label = stringResource(R.string.connection_outbound),
                value = connection.outbound,
                monospace = true,
            )
            DetailRow(
                label = stringResource(R.string.connection_outbound_type),
                value = connection.outboundType,
                monospace = true,
            )
            if (connection.chain.size > 1) {
                DetailRow(
                    label = stringResource(R.string.connection_chain),
                    value = connection.chain.joinToString(" → "),
                    monospace = true,
                )
            }
        }

        connection.processInfo?.let { processInfo ->
            if (processInfo.packageName.isNotEmpty() ||
                processInfo.processPath.isNotEmpty() ||
                processInfo.processId > 0
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                DetailSection(title = stringResource(R.string.connection_section_process)) {
                    if (processInfo.processId > 0) {
                        DetailRow(
                            label = stringResource(R.string.connection_process_id),
                            value = processInfo.processId.toString(),
                            monospace = true,
                        )
                    }
                    if (processInfo.userId >= 0) {
                        DetailRow(
                            label = stringResource(R.string.connection_user_id),
                            value = processInfo.userId.toString(),
                            monospace = true,
                        )
                    }
                    if (processInfo.userName.isNotEmpty()) {
                        DetailRow(
                            label = stringResource(R.string.connection_user_name),
                            value = processInfo.userName,
                            monospace = true,
                        )
                    }
                    if (processInfo.processPath.isNotEmpty()) {
                        DetailRow(
                            label = stringResource(R.string.connection_process_path),
                            value = processInfo.processPath,
                            monospace = true,
                        )
                    }
                    if (processInfo.packageName.isNotEmpty()) {
                        DetailRow(
                            label = stringResource(R.string.connection_package_name),
                            value = processInfo.packageName,
                            monospace = true,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, monospace: Boolean = false, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
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
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                style = if (monospace) {
                    MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = valueColor,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun rememberBounceBlockingNestedScrollConnection(scrollState: ScrollState): NestedScrollConnection = remember(scrollState) {
    object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = if (available.y < 0) available else Offset.Zero

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = if (available.y < 0) available else Velocity.Zero
    }
}
