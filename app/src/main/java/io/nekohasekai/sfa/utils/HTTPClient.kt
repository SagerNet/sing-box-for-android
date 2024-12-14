package io.nekohasekai.sfa.utils

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.ktx.unwrap
import java.io.Closeable
import java.util.Locale

class HTTPClient : Closeable {

    companion object {
        val userAgent by lazy {
            var userAgent = "SFA/"
            userAgent += BuildConfig.VERSION_NAME
            userAgent += " ("
            userAgent += BuildConfig.VERSION_CODE
            userAgent += "; sing-box "
            userAgent += Libbox.version()
            userAgent += "; language "
            userAgent += Locale.getDefault().toLanguageTag().replace("-", "_")
            userAgent += ")"
            userAgent
        }
    }

    private val client = Libbox.newHTTPClient()

    init {
        client.modernTLS()
    }

    fun getString(url: String): String {
        val request = client.newRequest()
        request.setUserAgent(userAgent)
        request.setURL(url)
        val response = request.execute()
        return response.content.unwrap
    }

    override fun close() {
        client.close()
    }


}