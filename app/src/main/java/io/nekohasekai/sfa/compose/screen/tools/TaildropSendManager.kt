package io.nekohasekai.sfa.compose.screen.tools

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.TaildropSendHandler
import io.nekohasekai.libbox.TaildropSendOptions
import io.nekohasekai.libbox.TaildropSendSession
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.bg.TaildropSendService
import io.nekohasekai.sfa.utils.CommandTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

class TaildropSendFile(
    val name: String,
    private val descriptor: ParcelFileDescriptor?,
    private val text: ByteArray? = null,
) {
    val size: Long = text?.size?.toLong() ?: descriptor!!.statSize

    fun open(): InputStream = if (text != null) {
        ByteArrayInputStream(text)
    } else {
        FileInputStream(descriptor!!.fileDescriptor)
    }

    fun close() {
        descriptor?.let { runCatching { it.close() } }
    }
}

data class TaildropSendFileState(
    val name: String,
    val size: Long,
    val sentBytes: Long = 0,
    val completed: Boolean = false,
)

data class TaildropSendState(
    val id: Long,
    val endpointTag: String,
    val peerName: String,
    val files: List<TaildropSendFileState>,
    val finished: Boolean = false,
    val errorMessage: String? = null,
)

object TaildropSendManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _sessions = MutableStateFlow<List<TaildropSendState>>(emptyList())
    val sessions: StateFlow<List<TaildropSendState>> = _sessions.asStateFlow()

    private val access = Any()
    private var nextID = 0L
    private val jobs = mutableMapOf<Long, Job>()
    private val holders = mutableMapOf<Long, SessionHolder>()

    fun openURIs(uris: List<Uri>): List<TaildropSendFile> {
        val files = mutableListOf<TaildropSendFile>()
        try {
            for (uri in uris) {
                if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
                    throw FileNotFoundException("unsupported share source: $uri")
                }
                val resolver = Application.application.contentResolver
                val descriptor = resolver.openFileDescriptor(uri, "r")
                    ?: throw FileNotFoundException(uri.toString())
                files.add(TaildropSendFile(displayName(resolver, uri), descriptor))
            }
        } catch (e: Exception) {
            for (file in files) {
                file.close()
            }
            throw e
        }
        return files
    }

    fun createTextFile(name: String, text: String): TaildropSendFile = TaildropSendFile(name, null, text.encodeToByteArray())

    fun send(endpointTag: String, peerStableID: String, peerName: String, files: List<TaildropSendFile>): Long {
        if (files.isEmpty()) return -1L
        val id: Long
        val holder = SessionHolder()
        val job: Job
        synchronized(access) {
            id = ++nextID
            holders[id] = holder
            job = scope.launch(start = CoroutineStart.LAZY) {
                runSession(id, holder, endpointTag, peerStableID, files)
            }
            jobs[id] = job
        }
        _sessions.update { sessions ->
            sessions + TaildropSendState(
                id = id,
                endpointTag = endpointTag,
                peerName = peerName,
                files = files.map { TaildropSendFileState(name = it.name, size = it.size) },
            )
        }
        ContextCompat.startForegroundService(
            Application.application,
            Intent(Application.application, TaildropSendService::class.java),
        )
        job.start()
        return id
    }

    fun cancel(id: Long) {
        val job: Job?
        val holder: SessionHolder?
        synchronized(access) {
            job = jobs.remove(id)
            holder = holders.remove(id)
        }
        dismiss(id)
        job?.cancel()
        scope.launch { holder?.close() }
    }

    fun cancelAll() {
        for (session in _sessions.value) {
            if (!session.finished) {
                cancel(session.id)
            }
        }
    }

    fun dismiss(id: Long) {
        _sessions.update { sessions -> sessions.filterNot { it.id == id } }
    }

    private class SessionHolder {
        private var session: TaildropSendSession? = null
        private var closed = false

        @Synchronized
        fun set(newSession: TaildropSendSession) {
            if (closed) {
                runCatching { newSession.close() }
                return
            }
            session = newSession
        }

        @Synchronized
        fun close() {
            closed = true
            val current = session
            session = null
            runCatching { current?.close() }
        }
    }

    private suspend fun runSession(
        id: Long,
        holder: SessionHolder,
        endpointTag: String,
        peerStableID: String,
        files: List<TaildropSendFile>,
    ) {
        try {
            val options = TaildropSendOptions()
            options.endpointTag = endpointTag
            options.peerStableID = peerStableID
            for (file in files) {
                options.addFile(file.name, file.size)
            }
            val finish = CompletableDeferred<String>()
            val session = CommandTarget.standaloneClient().sendTaildropFiles(
                options,
                object : TaildropSendHandler {
                    override fun onProgress(fileIndex: Int, sentBytes: Long) {
                        updateFile(id, fileIndex) { copy(sentBytes = sentBytes) }
                    }

                    override fun onFileCompleted(fileIndex: Int, sentBytes: Long) {
                        updateFile(id, fileIndex) { copy(size = sentBytes, sentBytes = sentBytes, completed = true) }
                    }

                    override fun onFinish(errorMessage: String) {
                        finish.complete(errorMessage)
                    }
                },
            )
            holder.set(session)

            var readFailure: IOException? = null
            try {
                for (file in files) {
                    writeFile(session, file.open())
                }
            } catch (e: IOException) {
                readFailure = e
            } catch (_: Exception) {
            }
            if (readFailure != null) {
                holder.close()
                throw readFailure
            }

            val errorMessage = finish.await()
            updateSession(id) {
                copy(finished = true, errorMessage = errorMessage.ifEmpty { null })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateSession(id) {
                copy(
                    finished = true,
                    errorMessage = e.message ?: e.toString(),
                )
            }
        } finally {
            synchronized(access) {
                jobs.remove(id)
                holders.remove(id)
            }
            holder.close()
            for (file in files) {
                file.close()
            }
        }
    }

    private fun writeFile(session: TaildropSendSession, input: InputStream) {
        val chunkSize = Libbox.TaildropChunkSize.toInt()
        var reachedEnd = false
        while (!reachedEnd) {
            val chunk = ByteArray(chunkSize)
            var filled = 0
            while (filled < chunkSize) {
                val read = input.read(chunk, filled, chunkSize - filled)
                if (read < 0) {
                    reachedEnd = true
                    break
                }
                filled += read
            }
            if (filled > 0) {
                session.writeChunk(if (filled == chunkSize) chunk else chunk.copyOf(filled))
            }
        }
        session.finishFile()
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                val name = cursor.getString(0)
                if (!name.isNullOrEmpty()) return File(name).name
            }
        }
        return uri.lastPathSegment?.let { File(it).name }?.takeIf { it.isNotEmpty() } ?: "file"
    }

    private fun updateSession(id: Long, reducer: TaildropSendState.() -> TaildropSendState) {
        _sessions.update { sessions ->
            sessions.map { if (it.id == id) it.reducer() else it }
        }
    }

    private fun updateFile(
        id: Long,
        fileIndex: Int,
        reducer: TaildropSendFileState.() -> TaildropSendFileState,
    ) {
        updateSession(id) {
            if (fileIndex !in files.indices) {
                this
            } else {
                copy(
                    files = files.mapIndexed { index, file ->
                        if (index == fileIndex) file.reducer() else file
                    },
                )
            }
        }
    }
}
