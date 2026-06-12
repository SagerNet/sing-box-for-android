package io.nekohasekai.sfa.utils

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.RemoteConnectionOptions
import io.nekohasekai.sfa.database.RemoteServer

object CommandTarget {
    private val access = Any()
    private var activeRemoteServer: RemoteServer? = null

    // One gRPC channel per remote session: standalone calls reuse it instead of
    // paying a TCP+TLS handshake (and leaking the connection) on every action.
    private var sharedRemoteClient: io.nekohasekai.libbox.CommandClient? = null

    val remoteServer: RemoteServer?
        get() = synchronized(access) { activeRemoteServer }

    val isRemote: Boolean
        get() = remoteServer != null

    fun setRemoteServer(server: RemoteServer?) {
        val previousClient: io.nekohasekai.libbox.CommandClient?
        synchronized(access) {
            previousClient = sharedRemoteClient
            sharedRemoteClient = null
            activeRemoteServer = server
        }
        previousClient?.apply {
            runCatching {
                disconnect()
            }
        }
    }

    fun libboxOptions(server: RemoteServer): RemoteConnectionOptions {
        val options = RemoteConnectionOptions()
        options.setURL(server.url)
        options.secret = server.secret
        return options
    }

    // Returns a client for one-shot calls and streamed sessions. In remote mode the
    // client is shared for the whole session — callers must not disconnect it.
    fun standaloneClient(): io.nekohasekai.libbox.CommandClient = synchronized(access) {
        val server = activeRemoteServer ?: return Libbox.newStandaloneCommandClient()
        sharedRemoteClient?.let { return it }
        val client = Libbox.newStandaloneRemoteCommandClient(libboxOptions(server))
        sharedRemoteClient = client
        client
    }

    // Returns a dedicated client owned by the caller, who is responsible for
    // disconnecting it (e.g. the SSH terminal closes its client on session end).
    fun ownedStandaloneClient(): io.nekohasekai.libbox.CommandClient {
        val server = remoteServer ?: return Libbox.newStandaloneCommandClient()
        return Libbox.newStandaloneRemoteCommandClient(libboxOptions(server))
    }
}
