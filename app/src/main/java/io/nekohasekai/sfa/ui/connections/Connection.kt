package io.nekohasekai.sfa.ui.connections

import androidx.compose.runtime.Immutable
import io.nekohasekai.libbox.Connection as LibboxConnection
import io.nekohasekai.libbox.ProcessInfo as LibboxProcessInfo
import io.nekohasekai.sfa.ktx.toList

@Immutable
data class ProcessInfo(
    val processId: Long,
    val userId: Int,
    val userName: String,
    val processPath: String,
    val packageName: String,
) {
    companion object {
        fun from(processInfo: LibboxProcessInfo?): ProcessInfo? {
            if (processInfo == null) return null
            return ProcessInfo(
                processId = processInfo.processID,
                userId = processInfo.userID,
                userName = processInfo.userName ?: "",
                processPath = processInfo.processPath ?: "",
                packageName = processInfo.packageName ?: "",
            )
        }
    }
}

@Immutable
data class Connection(
    val id: String,
    val inbound: String,
    val inboundType: String,
    val ipVersion: Int,
    val network: String,
    val source: String,
    val destination: String,
    val domain: String,
    val displayDestination: String,
    val protocolName: String,
    val user: String,
    val fromOutbound: String,
    val createdAt: Long,
    val closedAt: Long?,
    val upload: Long,
    val download: Long,
    val uploadTotal: Long,
    val downloadTotal: Long,
    val rule: String,
    val outbound: String,
    val outboundType: String,
    val chain: List<String>,
    val processInfo: ProcessInfo?,
) {
    val isActive: Boolean get() = closedAt == null || closedAt == 0L

    companion object {
        fun from(connection: LibboxConnection): Connection {
            return Connection(
                id = connection.id,
                inbound = connection.inbound,
                inboundType = connection.inboundType,
                ipVersion = connection.ipVersion,
                network = connection.network,
                source = connection.source,
                destination = connection.destination,
                domain = connection.domain,
                displayDestination = connection.displayDestination(),
                protocolName = connection.protocol,
                user = connection.user,
                fromOutbound = connection.fromOutbound,
                createdAt = connection.createdAt,
                closedAt = if (connection.closedAt > 0) connection.closedAt else null,
                upload = connection.uplink,
                download = connection.downlink,
                uploadTotal = connection.uplinkTotal,
                downloadTotal = connection.downlinkTotal,
                rule = connection.rule,
                outbound = connection.outbound,
                outboundType = connection.outboundType,
                chain = connection.chain().toList(),
                processInfo = ProcessInfo.from(connection.processInfo),
            )
        }
    }
}
