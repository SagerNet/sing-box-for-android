package io.nekohasekai.sfa.compose.screen.tools

import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.TailscaleStatusHandler
import io.nekohasekai.libbox.TailscaleStatusSubscription
import io.nekohasekai.libbox.TailscaleStatusUpdate
import io.nekohasekai.sfa.compose.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class TailscalePeerData(
    val id: String,
    val stableID: String,
    val hostName: String,
    val dnsName: String,
    val os: String,
    val tailscaleIPs: List<String>,
    val sshHostKeys: List<String>,
    val online: Boolean,
    val exitNode: Boolean,
    val exitNodeOption: Boolean,
    val shareeNode: Boolean,
    val expired: Boolean,
    val active: Boolean,
    val rxBytes: Long,
    val txBytes: Long,
    val keyExpiry: Long,
    val lastSeen: Long,
) {
    val displayName: String get() = dnsName.substringBefore(".").ifEmpty { hostName }
}

data class TailscaleUserGroupData(
    val id: Long,
    val loginName: String,
    val displayName: String,
    val profilePicURL: String,
    val peers: List<TailscalePeerData>,
)

data class TailscaleEndpointData(
    val endpointTag: String,
    val backendState: String,
    val authURL: String,
    val networkName: String,
    val magicDNSSuffix: String,
    val selfPeer: TailscalePeerData?,
    val exitNode: TailscalePeerData?,
    val userGroups: List<TailscaleUserGroupData>,
    val keyAuth: Boolean,
) {
    val hasExitNodeCandidates: Boolean
        get() {
            if (exitNode != null) return true
            val selfStableID = selfPeer?.stableID
            return userGroups.any { group ->
                group.peers.any { it.exitNodeOption && it.stableID != selfStableID }
            }
        }
}

data class TailscaleStatusState(
    val endpoints: List<TailscaleEndpointData> = emptyList(),
    val isSubscribed: Boolean = false,
)

class TailscaleStatusViewModel : BaseViewModel<TailscaleStatusState, Nothing>() {
    private var statusSubscription: TailscaleStatusSubscription? = null

    override fun createInitialState() = TailscaleStatusState()

    fun subscribe() {
        if (currentState.isSubscribed) return
        updateState { copy(isSubscribed = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                statusSubscription =
                    Libbox.newStandaloneCommandClient()
                        .subscribeTailscaleStatus(object : TailscaleStatusHandler {
                            override fun onStatusUpdate(status: TailscaleStatusUpdate) {
                                val endpoints = convertUpdate(status)
                                viewModelScope.launch {
                                    if (!currentState.isSubscribed) return@launch
                                    updateState { copy(endpoints = endpoints) }
                                }
                            }

                            override fun onError(message: String) {
                                viewModelScope.launch {
                                    if (!currentState.isSubscribed) return@launch
                                    updateState { copy(endpoints = emptyList(), isSubscribed = false) }
                                    statusSubscription = null
                                    sendErrorMessage(message)
                                }
                            }
                        })
            } catch (_: Exception) {
                viewModelScope.launch {
                    updateState { copy(endpoints = emptyList(), isSubscribed = false) }
                    statusSubscription = null
                }
            }
        }
    }

    fun cancel() {
        try {
            statusSubscription?.close()
        } catch (_: Exception) {
        }
        statusSubscription = null
        updateState { copy(endpoints = emptyList(), isSubscribed = false) }
    }

    fun setExitNode(endpointTag: String, stableID: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Libbox.newStandaloneCommandClient().setTailscaleExitNode(endpointTag, stableID)
            } catch (e: Exception) {
                sendErrorMessage(e.message ?: "set exit node failed")
            }
        }
    }

    fun logout(endpointTag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Libbox.newStandaloneCommandClient().tailscaleLogout(endpointTag)
            } catch (e: Exception) {
                sendErrorMessage(e.message ?: "logout failed")
            }
        }
    }

    fun endpoint(tag: String): TailscaleEndpointData? = currentState.endpoints.firstOrNull { it.endpointTag == tag }

    fun peer(endpointTag: String, peerId: String): TailscalePeerData? {
        val ep = endpoint(endpointTag) ?: return null
        if (ep.selfPeer?.id == peerId) return ep.selfPeer
        for (group in ep.userGroups) {
            val found = group.peers.firstOrNull { it.id == peerId }
            if (found != null) return found
        }
        return null
    }

    override fun onCleared() {
        cancel()
        super.onCleared()
    }

    private fun convertUpdate(status: TailscaleStatusUpdate): List<TailscaleEndpointData> {
        val endpoints = mutableListOf<TailscaleEndpointData>()
        val iterator = status.endpoints()
        while (iterator.hasNext()) {
            endpoints.add(convertEndpoint(iterator.next()))
        }
        return endpoints
    }

    private fun convertEndpoint(
        endpoint: io.nekohasekai.libbox.TailscaleEndpointStatus,
    ): TailscaleEndpointData {
        val userGroups = mutableListOf<TailscaleUserGroupData>()
        val groupIterator = endpoint.userGroups()
        while (groupIterator.hasNext()) {
            userGroups.add(convertUserGroup(groupIterator.next()))
        }
        val self = endpoint.getSelf()
        val exitNode = endpoint.exitNode
        return TailscaleEndpointData(
            endpointTag = endpoint.endpointTag,
            backendState = endpoint.backendState,
            authURL = endpoint.authURL,
            networkName = endpoint.networkName,
            magicDNSSuffix = endpoint.magicDNSSuffix,
            selfPeer = if (self != null) convertPeer(self) else null,
            exitNode = if (exitNode != null) convertPeer(exitNode) else null,
            userGroups = userGroups,
            keyAuth = endpoint.keyAuth,
        )
    }

    private fun convertUserGroup(
        group: io.nekohasekai.libbox.TailscaleUserGroup,
    ): TailscaleUserGroupData {
        val peers = mutableListOf<TailscalePeerData>()
        val peerIterator = group.peers()
        while (peerIterator.hasNext()) {
            peers.add(convertPeer(peerIterator.next()))
        }
        return TailscaleUserGroupData(
            id = group.userID,
            loginName = group.loginName,
            displayName = group.displayName,
            profilePicURL = group.profilePicURL,
            peers = peers,
        )
    }

    private fun convertPeer(peer: io.nekohasekai.libbox.TailscalePeer): TailscalePeerData {
        val ips = mutableListOf<String>()
        val ipIterator = peer.tailscaleIPs()
        while (ipIterator.hasNext()) {
            ips.add(ipIterator.next())
        }
        val sshKeys = mutableListOf<String>()
        val keyIterator = peer.sshHostKeys()
        while (keyIterator.hasNext()) {
            sshKeys.add(keyIterator.next())
        }
        val dnsName = peer.getDNSName()
        return TailscalePeerData(
            id = if (dnsName.isNotEmpty()) dnsName else peer.hostName,
            stableID = peer.stableID,
            hostName = peer.hostName,
            dnsName = dnsName,
            os = peer.getOS(),
            tailscaleIPs = ips,
            sshHostKeys = sshKeys,
            online = peer.online,
            exitNode = peer.exitNode,
            exitNodeOption = peer.exitNodeOption,
            shareeNode = peer.shareeNode,
            expired = peer.expired,
            active = peer.active,
            rxBytes = peer.rxBytes,
            txBytes = peer.txBytes,
            keyExpiry = peer.keyExpiry,
            lastSeen = peer.lastSeen,
        )
    }
}
