package io.nekohasekai.sfa.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StreamSubscription<T : Any>(private val closeSession: (T) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val access = Any()
    private var session: T? = null
    private var generation = 0L

    fun open(): Long = synchronized(access) {
        dropCurrent()
        ++generation
    }

    fun store(token: Long, newSession: T) {
        synchronized(access) {
            if (generation == token) {
                session = newSession
                return
            }
        }
        scope.launch { runCatching { closeSession(newSession) } }
    }

    fun close() {
        synchronized(access) {
            dropCurrent()
            generation++
        }
    }

    fun discard(token: Long) {
        synchronized(access) {
            if (generation != token) return
            session = null
            generation++
        }
    }

    private fun dropCurrent() {
        val current = session ?: return
        session = null
        scope.launch { runCatching { closeSession(current) } }
    }
}
