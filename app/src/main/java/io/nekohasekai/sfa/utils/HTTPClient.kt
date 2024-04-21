package io.nekohasekai.sfa.utils

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.BuildConfig
import java.io.Closeable

class HTTPClient : Closeable {

    companion object {
        val userAgent by lazy {
            var userAgent = "SFA/"
            userAgent += BuildConfig.VERSION_NAME
            userAgent += " ("
            userAgent += BuildConfig.VERSION_CODE
            userAgent += "; sing-box "
            userAgent += Libbox.version()
            userAgent += ")"
            userAgent
        }
    }

    private val client = Libbox.newHTTPClient()

    init {
        client.modernTLS()
    }

    private fun _getResponse(url: String): HTTPResponse {
        val request = client.newRequest()
        request.setUserAgent(userAgent)
        request.setURL(url)
        return request.execute()
    }

    fun getString(url: String): String {
        return _getResponse(url).contentString
    }

    fun getConfigWithUpdatedURL(url: String): Pair<String, String> {
        val response = _getResponse(url);
        return Pair(response.contentString, response.finalURL)
    }

    override fun close() {
        client.close()
    }


}
