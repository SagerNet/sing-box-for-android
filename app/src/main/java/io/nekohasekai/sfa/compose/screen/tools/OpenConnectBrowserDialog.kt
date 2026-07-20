package io.nekohasekai.sfa.compose.screen.tools

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.nekohasekai.sfa.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenConnectBrowserDialog(
    challengeID: String,
    request: OpenConnectBrowserRequestData,
    onDismiss: () -> Unit,
    onResult: (OpenConnectBrowserResultData) -> Unit,
    onError: (String) -> Unit,
) {
    val unsupportedMessage = stringResource(R.string.endpoint_globalprotect_sso_unsupported_android)
    val missingCookieMessage = stringResource(R.string.endpoint_browser_cookie_missing)
    val browser = remember(challengeID) {
        OpenConnectWebViewBrowser(
            request,
            unsupportedMessage,
            missingCookieMessage,
            onResult,
            onError,
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.endpoint_authentication)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    },
                )
                AndroidView(
                    factory = { context -> WebView(context).also(browser::start) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    BackHandler(onBack = onDismiss)
    DisposableEffect(browser) {
        onDispose(browser::close)
    }
}

private class OpenConnectWebViewBrowser(
    private val request: OpenConnectBrowserRequestData,
    private val unsupportedMessage: String,
    private val missingCookieMessage: String,
    private val onResult: (OpenConnectBrowserResultData) -> Unit,
    private val onError: (String) -> Unit,
) {
    companion object {
        private const val COOKIE_RETRY_COUNT = 30
        private const val COOKIE_RETRY_DELAY_MILLIS = 100L
    }

    private var webView: WebView? = null
    private var completed = false
    private var closed = false

    @SuppressLint("SetJavaScriptEnabled")
    fun start(view: WebView) {
        if (request.headerNames.isNotEmpty()) {
            fail(unsupportedMessage)
            return
        }
        if (request.cookieNames.isEmpty()) {
            fail(missingCookieMessage)
            return
        }

        webView = view
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
        }
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (request.finalURL.isEmpty() || url == request.finalURL) {
                    completeWhenCookiesAvailable(view, url, 0)
                }
            }
        }
        view.loadUrl(request.url)
    }

    fun close() {
        if (closed) return
        closed = true
        webView?.apply {
            stopLoading()
            removeAllViews()
            destroy()
        }
        webView = null
    }

    private fun completeWhenCookiesAvailable(view: WebView, url: String, attempt: Int) {
        if (completed || closed || webView !== view) return
        val cookies = requestedCookies(url)
        if (cookies.any { it.value.isNotEmpty() }) {
            completed = true
            onResult(OpenConnectBrowserResultData(url, cookies))
            return
        }
        if (attempt >= COOKIE_RETRY_COUNT) {
            fail(missingCookieMessage)
            return
        }
        view.postDelayed(
            { completeWhenCookiesAvailable(view, url, attempt + 1) },
            COOKIE_RETRY_DELAY_MILLIS,
        )
    }

    private fun requestedCookies(url: String): List<OpenConnectBrowserCookieData> {
        val requestedNames = request.cookieNames.toSet()
        return CookieManager.getInstance().getCookie(url)
            .orEmpty()
            .split(';')
            .mapNotNull { entry ->
                val separator = entry.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val name = entry.substring(0, separator).trim()
                if (name !in requestedNames) return@mapNotNull null
                OpenConnectBrowserCookieData(name, entry.substring(separator + 1))
            }
    }

    private fun fail(message: String) {
        if (completed || closed) return
        completed = true
        onError(message)
    }
}
