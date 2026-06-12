package io.nekohasekai.sfa.database

@Suppress("RedundantSuspendModifier")
object RemoteServerManager {
    private val callbacks = mutableListOf<() -> Unit>()

    fun registerCallback(callback: () -> Unit) {
        callbacks.add(callback)
    }

    fun unregisterCallback(callback: () -> Unit) {
        callbacks.remove(callback)
    }

    private fun notifyCallbacks() {
        for (callback in callbacks.toList()) {
            callback()
        }
    }

    suspend fun nextOrder(): Long = ProfileManager.remoteServerDao().nextOrder() ?: 0

    suspend fun get(id: Long): RemoteServer? = ProfileManager.remoteServerDao().get(id)

    suspend fun create(server: RemoteServer): RemoteServer {
        server.userOrder = nextOrder()
        server.id = ProfileManager.remoteServerDao().insert(server)
        notifyCallbacks()
        return server
    }

    suspend fun update(server: RemoteServer): Int {
        try {
            return ProfileManager.remoteServerDao().update(server)
        } finally {
            notifyCallbacks()
        }
    }

    suspend fun update(servers: List<RemoteServer>): Int {
        try {
            return ProfileManager.remoteServerDao().update(servers)
        } finally {
            notifyCallbacks()
        }
    }

    suspend fun delete(server: RemoteServer): Int {
        try {
            return ProfileManager.remoteServerDao().delete(server)
        } finally {
            notifyCallbacks()
        }
    }

    suspend fun list(): List<RemoteServer> = ProfileManager.remoteServerDao().list()
}
