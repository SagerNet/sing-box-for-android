package io.nekohasekai.sfa.compose.screen.tools

import android.webkit.MimeTypeMap
import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.TaildropDownloadHandler
import io.nekohasekai.libbox.TaildropInbox
import io.nekohasekai.libbox.TaildropInboxHandler
import io.nekohasekai.libbox.TaildropInboxSubscription
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.compose.base.BaseViewModel
import io.nekohasekai.sfa.utils.CommandTarget
import io.nekohasekai.sfa.utils.StreamSubscription
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class TaildropFileData(
    val name: String,
    val size: Long,
    val senderName: String,
    val modifiedAt: Long,
)

data class TaildropReceivingFileData(
    val name: String,
    val size: Long,
    val receivedBytes: Long,
    val senderID: String,
    val senderName: String,
)

data class TaildropInboxState(
    val files: List<TaildropFileData> = emptyList(),
    val receiving: List<TaildropReceivingFileData> = emptyList(),
    val isSubscribed: Boolean = false,
    val hasUpdate: Boolean = false,
)

object TaildropFiles {
    private const val DIRECTORY_NAME = "Taildrop"
    private const val CACHE_DIRECTORY = "taildrop"
    private const val CACHE_LIFETIME = 24L * 60 * 60 * 1000

    fun mimeType(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    private fun daemonFile(name: String): File? {
        if (CommandTarget.isRemote) return null
        val workingDirectory = Application.application.getExternalFilesDir(null) ?: return null
        val file = File(File(workingDirectory, DIRECTORY_NAME), name)
        return if (file.isFile) file else null
    }

    suspend fun materialize(endpointTag: String, name: String): File = withContext(Dispatchers.IO) {
        daemonFile(name)?.let { return@withContext it }
        val cacheDirectory = File(Application.application.cacheDir, CACHE_DIRECTORY).also { it.mkdirs() }
        val destination = File(cacheDirectory, name)
        val finish = CompletableDeferred<String>()
        val session = CommandTarget.standaloneClient().downloadTaildropFile(
            endpointTag,
            name,
            destination.path,
            object : TaildropDownloadHandler {
                override fun onProgress(downloaded: Long, total: Long) {
                }

                override fun onFinish(errorMessage: String) {
                    finish.complete(errorMessage)
                }
            },
        )
        try {
            val errorMessage = finish.await()
            if (errorMessage.isNotEmpty()) {
                throw IOException(errorMessage)
            }
        } finally {
            session.close()
        }
        destination
    }

    fun cleanCache() {
        val directory = File(Application.application.cacheDir, CACHE_DIRECTORY)
        val expiry = System.currentTimeMillis() - CACHE_LIFETIME
        directory.listFiles()?.forEach { file ->
            if (file.lastModified() < expiry) {
                file.delete()
            }
        }
    }
}

class TaildropViewModel : BaseViewModel<TaildropInboxState, Nothing>() {
    private val subscription = StreamSubscription<TaildropInboxSubscription> { it.close() }
    private var subscribedTag: String? = null

    override fun createInitialState() = TaildropInboxState()

    fun subscribe(endpointTag: String) {
        if (currentState.isSubscribed && subscribedTag == endpointTag) return
        val token = subscription.open()
        subscribedTag = endpointTag
        updateState { TaildropInboxState(isSubscribed = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val session = CommandTarget.standaloneClient()
                    .subscribeTaildropInbox(
                        endpointTag,
                        object : TaildropInboxHandler {
                            override fun onInboxUpdate(inbox: TaildropInbox) {
                                val files = convertFiles(inbox)
                                val receiving = convertReceiving(inbox)
                                viewModelScope.launch {
                                    if (!currentState.isSubscribed) return@launch
                                    updateState { copy(files = files, receiving = receiving, hasUpdate = true) }
                                }
                            }

                            override fun onError(message: String) {
                                subscription.discard(token)
                                viewModelScope.launch {
                                    if (!currentState.isSubscribed) return@launch
                                    subscribedTag = null
                                    updateState { TaildropInboxState(hasUpdate = true) }
                                    sendErrorMessage(message)
                                }
                            }
                        },
                    )
                subscription.store(token, session)
            } catch (e: Exception) {
                viewModelScope.launch {
                    subscribedTag = null
                    updateState { TaildropInboxState(hasUpdate = true) }
                    sendErrorMessage(e.message ?: e.toString())
                }
            }
        }
    }

    fun cancel() {
        subscription.close()
        subscribedTag = null
        updateState { TaildropInboxState() }
    }

    fun markInboxRead(endpointTag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CommandTarget.standaloneClient().markTaildropInboxRead(endpointTag)
            } catch (e: Exception) {
                sendErrorMessage(e.message ?: "mark inbox read failed")
            }
        }
    }

    fun cancelReceiving(endpointTag: String, senderID: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CommandTarget.standaloneClient().cancelTaildropReceiving(endpointTag, senderID, name)
            } catch (e: Exception) {
                sendErrorMessage(e.message ?: "cancel receiving failed")
            }
        }
    }

    fun delete(endpointTag: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CommandTarget.standaloneClient().deleteTaildropFile(endpointTag, name)
            } catch (e: Exception) {
                sendErrorMessage(e.message ?: "delete failed")
            }
        }
    }

    fun deleteAll(endpointTag: String, names: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val client = try {
                CommandTarget.standaloneClient()
            } catch (e: Exception) {
                sendErrorMessage(e.message ?: "delete failed")
                return@launch
            }
            for (name in names) {
                try {
                    client.deleteTaildropFile(endpointTag, name)
                } catch (e: Exception) {
                    sendErrorMessage(e.message ?: "delete failed")
                    return@launch
                }
            }
        }
    }

    override fun onCleared() {
        cancel()
        super.onCleared()
    }

    private fun convertFiles(inbox: TaildropInbox): List<TaildropFileData> {
        val files = mutableListOf<TaildropFileData>()
        val iterator = inbox.files()
        while (iterator.hasNext()) {
            val file = iterator.next()
            files.add(
                TaildropFileData(
                    name = file.name,
                    size = file.size,
                    senderName = file.senderName,
                    modifiedAt = file.modifiedAt,
                ),
            )
        }
        return files
    }

    private fun convertReceiving(inbox: TaildropInbox): List<TaildropReceivingFileData> {
        val files = mutableListOf<TaildropReceivingFileData>()
        val iterator = inbox.receiving()
        while (iterator.hasNext()) {
            val file = iterator.next()
            files.add(
                TaildropReceivingFileData(
                    name = file.name,
                    size = file.size,
                    receivedBytes = file.receivedBytes,
                    senderID = file.senderID,
                    senderName = file.senderName,
                ),
            )
        }
        return files
    }
}
