package io.nekohasekai.sfa.vendor

import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.update.UpdateState
import io.nekohasekai.sfa.utils.HTTPClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libbox.Libbox
import java.io.Closeable
import java.io.File

class ApkDownloader : Closeable {
    private val client = Libbox.newHTTPClient().apply {
        modernTLS()
        keepAlive()
    }

    suspend fun download(url: String): File = withContext(Dispatchers.IO) {
        val cacheDir = File(Application.application.cacheDir, "updates")
        cacheDir.mkdirs()
        val apkFile = File(cacheDir, "update.apk")

        if (apkFile.exists()) apkFile.delete()

        val request = client.newRequest()
        request.setUserAgent(HTTPClient.userAgent)
        request.setURL(url)

        val response = request.execute()
        response.writeTo(apkFile.absolutePath)

        if (!apkFile.exists() || apkFile.length() == 0L) {
            throw Exception("Download failed: empty file")
        }

        UpdateState.saveApkPath(apkFile)
        apkFile
    }

    override fun close() {
        client.close()
    }
}
