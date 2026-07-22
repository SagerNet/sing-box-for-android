package io.nekohasekai.sfa.compose.screen.tools

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.nekohasekai.sfa.R
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenConnectBrowserDialog(
    challengeID: String,
    endpointTag: String,
    request: OpenConnectBrowserRequestData,
    onDismiss: () -> Unit,
    onResult: (OpenConnectBrowserResultData) -> Unit,
    onError: (String) -> Unit,
) {
    val unsupportedMessage = stringResource(R.string.endpoint_globalprotect_sso_unsupported_android)
    val missingCookieMessage = stringResource(R.string.endpoint_browser_cookie_missing)
    val profileUnavailableMessage = stringResource(R.string.endpoint_browser_profile_unavailable)
    val storageID = if (request.cacheID.isEmpty()) endpointTag else "${request.cacheID}:$endpointTag"
    val browser = remember(challengeID, storageID, request) {
        OpenConnectWebViewBrowser(
            request,
            storageID,
            unsupportedMessage,
            missingCookieMessage,
            profileUnavailableMessage,
            onResult,
            onError,
            onDismiss,
        )
    }

    Dialog(
        onDismissRequest = browser::close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.endpoint_authentication)) },
                    navigationIcon = {
                        IconButton(onClick = browser::close) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    },
                )
                AndroidView(
                    factory = { context -> FrameLayout(context).also(browser::start) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    BackHandler(onBack = browser::close)
    DisposableEffect(browser) {
        onDispose(browser::dispose)
    }
}

private class OpenConnectWebViewBrowser(
    private val request: OpenConnectBrowserRequestData,
    storageID: String,
    private val unsupportedMessage: String,
    private val missingCookieMessage: String,
    private val profileUnavailableMessage: String,
    private val onResult: (OpenConnectBrowserResultData) -> Unit,
    private val onError: (String) -> Unit,
    private val onDismiss: () -> Unit,
) {
    private enum class State {
        OPENING,
        ACTIVE,
        COMPLETING,
        CLOSED,
    }

    private data class NavigationState(
        var generation: Int = 0,
        var lastURL: String? = null,
        var loading: Boolean = false,
    )

    companion object {
        private const val FINAL_COOKIE_RETRY_COUNT = 30
        private const val COOKIE_POLL_DELAY_MILLIS = 100L
        private const val MAXIMUM_WEB_VIEW_COUNT = 8
        private const val MAXIMUM_OBSERVED_URL_COUNT = 128
    }

    private var container: FrameLayout? = null
    private var rootWebView: WebView? = null
    private var cookieManager: CookieManager? = null
    private var state = State.OPENING
    private val profileName = "openconnect-browser-" + UUID.nameUUIDFromBytes(
        "openconnect-browser:$storageID".toByteArray(Charsets.UTF_8),
    )
    private val navigationStates = linkedMapOf<WebView, NavigationState>()
    private val collectedCookies = linkedMapOf<String, OpenConnectBrowserCookieData>()
    private val observedURLs = linkedSetOf<String>()
    private val cookiePoller = object : Runnable {
        override fun run() {
            if (state != State.ACTIVE || request.completionMode != OpenConnectBrowserCompletionMode.COOKIE) return
            val currentURL = navigationStates.values.lastOrNull()?.lastURL ?: request.url
            if (navigationStates.values.none(NavigationState::loading)) {
                captureCookies(currentURL, rebuild = true)
            }
            if (state == State.ACTIVE) {
                container?.postDelayed(this, COOKIE_POLL_DELAY_MILLIS)
            }
        }
    }

    fun start(viewContainer: FrameLayout) {
        if (!isSupportedBrowserURL(request.url)) {
            fail(viewContainer.context.getString(R.string.endpoint_browser_navigation_failed, request.url))
            return
        }
        if (request.completionMode == OpenConnectBrowserCompletionMode.HEADER) {
            fail(unsupportedMessage)
            return
        }
        if (request.completionMode == OpenConnectBrowserCompletionMode.INVALID) {
            fail(missingCookieMessage)
            return
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            fail(profileUnavailableMessage)
            return
        }

        container = viewContainer
        state = State.ACTIVE
        val view = WebView(viewContainer.context)
        if (!addWebView(view)) return
        rootWebView = view
        observeURL(request.url)
        prepareCookiesAndLoad(view)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun addWebView(view: WebView): Boolean {
        if (!assignProfile(view)) {
            view.destroy()
            fail(profileUnavailableMessage)
            return false
        }
        navigationStates[view] = NavigationState()
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, navigationRequest: WebResourceRequest): Boolean = handleNavigation(view, navigationRequest.url.toString(), navigationRequest.isForMainFrame)

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = handleNavigation(view, url, true)

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                val navigation = navigationStates[view] ?: return
                if (
                    !navigation.loading &&
                    request.completionMode == OpenConnectBrowserCompletionMode.COOKIE &&
                    captureCookies(navigation.lastURL)
                ) {
                    view.stopLoading()
                    return
                }
                navigation.lastURL = url
                navigation.generation++
                navigation.loading = true
                observeURL(url)
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                val navigation = navigationStates[view] ?: return
                navigation.loading = false
                if (request.completionMode == OpenConnectBrowserCompletionMode.COOKIE) pollCookies(view, url, navigation.generation, 0)
            }

            override fun onReceivedError(view: WebView, navigationRequest: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, navigationRequest, error)
                if (navigationRequest.isForMainFrame && navigationStates.containsKey(view)) {
                    fail(view.context.getString(R.string.endpoint_browser_navigation_failed, error.description))
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                navigationRequest: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                super.onReceivedHttpError(view, navigationRequest, errorResponse)
                if (navigationRequest.isForMainFrame && navigationStates.containsKey(view)) {
                    fail(
                        view.context.getString(
                            R.string.endpoint_browser_navigation_failed,
                            "${errorResponse.statusCode} ${errorResponse.reasonPhrase}",
                        ),
                    )
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                if (navigationStates.containsKey(view)) {
                    fail(view.context.getString(R.string.endpoint_browser_navigation_failed, error.toString()))
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (navigationStates.containsKey(view)) {
                    fail(view.context.getString(R.string.endpoint_browser_navigation_failed, detail.toString()))
                }
                return true
            }

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && navigationStates.containsKey(view)) {
                    fail(view.context.getString(R.string.endpoint_browser_navigation_failed, description))
                }
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (
                    state != State.ACTIVE ||
                    container == null ||
                    !navigationStates.containsKey(view)
                ) {
                    return false
                }
                if (navigationStates.size >= MAXIMUM_WEB_VIEW_COUNT) {
                    fail(view.context.getString(R.string.endpoint_browser_too_many_windows))
                    return false
                }
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val childView = WebView(view.context)
                if (!addWebView(childView)) return false
                childView.requestFocus()
                transport.webView = childView
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) {
                if (window === rootWebView) {
                    close()
                } else {
                    removeWebView(window)
                }
            }
        }
        container?.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        return true
    }

    private fun assignProfile(view: WebView): Boolean {
        try {
            WebViewCompat.setProfile(view, profileName)
            val profileCookieManager = WebViewCompat.getProfile(view).cookieManager
            profileCookieManager.setAcceptCookie(true)
            profileCookieManager.setAcceptThirdPartyCookies(view, true)
            cookieManager = profileCookieManager
        } catch (_: Exception) {
            return false
        }
        return true
    }

    fun close() {
        val shouldDismiss = state == State.OPENING || state == State.ACTIVE
        dispose()
        if (shouldDismiss) onDismiss()
    }

    fun dispose() {
        state = State.CLOSED
        container?.removeCallbacks(cookiePoller)
        for (view in navigationStates.keys.toList()) {
            removeWebView(view)
        }
        rootWebView = null
        container = null
        cookieManager?.flush()
        cookieManager = null
    }

    private fun removeWebView(view: WebView) {
        if (navigationStates.remove(view) == null) return
        container?.removeView(view)
        view.stopLoading()
        view.removeAllViews()
        view.destroy()
    }

    private fun prepareCookiesAndLoad(view: WebView) {
        val cookieManager = cookieManager
        if (cookieManager == null) {
            fail(profileUnavailableMessage)
            return
        }
        val requestedNames = (request.cookieNames + request.earlyCookieNames).toSet()
        val cookieURLs = listOf(request.url, request.finalURL).filter(::isHTTPURL).distinct()
        val expirationRequests = cookieURLs.flatMap { url ->
            requestedNames.map { name -> url to "$name=; Max-Age=0; Path=/" }
        }
        if (request.completionMode != OpenConnectBrowserCompletionMode.COOKIE || expirationRequests.isEmpty()) {
            activate(view)
            return
        }
        var remainingRequests = expirationRequests.size
        for ((url, cookie) in expirationRequests) {
            cookieManager.setCookie(url, cookie) {
                if (state == State.ACTIVE && rootWebView === view && navigationStates.containsKey(view)) {
                    remainingRequests--
                    if (remainingRequests == 0) {
                        cookieManager.flush()
                        activate(view)
                    }
                }
            }
        }
    }

    private fun activate(view: WebView) {
        if (state != State.ACTIVE || rootWebView !== view || !navigationStates.containsKey(view)) return
        if (request.completionMode == OpenConnectBrowserCompletionMode.COOKIE) view.post(cookiePoller)
        view.loadUrl(request.url)
    }

    private fun isSupportedBrowserURL(url: String): Boolean {
        val parsedURL = Uri.parse(url)
        return parsedURL.scheme.equals("data", ignoreCase = true) || isHTTPURL(url)
    }

    private fun isHTTPURL(url: String): Boolean {
        val parsedURL = Uri.parse(url)
        return (parsedURL.scheme.equals("http", ignoreCase = true) || parsedURL.scheme.equals("https", ignoreCase = true)) &&
            parsedURL.host != null
    }

    private fun handleNavigation(view: WebView, url: String, isForMainFrame: Boolean): Boolean {
        if (state != State.ACTIVE || !navigationStates.containsKey(view)) return true
        if (!isForMainFrame) return !isAllowedNavigationURL(url)
        val navigation = navigationStates[view]
        if (
            navigation?.loading == false &&
            request.completionMode == OpenConnectBrowserCompletionMode.COOKIE &&
            captureCookies(navigation.lastURL)
        ) {
            view.stopLoading()
            return true
        }
        if (request.callbackURLPrefixes.any { it.isNotEmpty() && url.startsWith(it) }) {
            view.stopLoading()
            complete(OpenConnectBrowserResultData(url, emptyList()))
            return true
        }
        if (!isAllowedNavigationURL(url)) {
            view.stopLoading()
            fail(view.context.getString(R.string.endpoint_browser_navigation_failed, url))
            return true
        }
        observeURL(url)
        return false
    }

    private fun pollCookies(view: WebView, url: String, generation: Int, attempt: Int) {
        if (state != State.ACTIVE || navigationStates[view]?.generation != generation) return
        val reachedFinalURL = url == request.finalURL
        if (captureCookies(url, reachedFinalURL)) return
        if (collectedCookies.isNotEmpty() && reachedFinalURL) {
            complete(OpenConnectBrowserResultData(url, collectedCookies.values.toList()))
            return
        }
        if (reachedFinalURL && attempt >= FINAL_COOKIE_RETRY_COUNT) {
            fail(missingCookieMessage)
            return
        }
        if (!reachedFinalURL) return
        view.postDelayed(
            { pollCookies(view, url, generation, attempt + 1) },
            COOKIE_POLL_DELAY_MILLIS,
        )
    }

    private fun complete(result: OpenConnectBrowserResultData) {
        if (state != State.ACTIVE) return
        state = State.COMPLETING
        onResult(result)
    }

    private fun captureCookies(url: String?, rebuild: Boolean = false): Boolean {
        if (url.isNullOrEmpty()) return false
        val cookies = if (rebuild) {
            buildList {
                val snapshotURLs = linkedSetOf(url)
                snapshotURLs.addAll(observedURLs.toList().asReversed())
                for (snapshotURL in snapshotURLs) {
                    addAll(requestedCookies(snapshotURL))
                }
            }
        } else {
            requestedCookies(url)
        }
        for (name in request.earlyCookieNames) {
            val cookie = cookies.firstOrNull { it.name == name && it.value.isNotEmpty() } ?: continue
            complete(OpenConnectBrowserResultData("", listOf(cookie)))
            return true
        }
        if (rebuild) collectedCookies.clear()
        for (name in request.cookieNames) {
            val cookie = cookies.firstOrNull { it.name == name && it.value.isNotEmpty() } ?: continue
            collectedCookies[name] = cookie
        }
        return false
    }

    private fun requestedCookies(url: String): List<OpenConnectBrowserCookieData> {
        val requestedNames = (request.cookieNames + request.earlyCookieNames).toSet()
        return cookieManager?.getCookie(url)
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

    private fun observeURL(url: String) {
        val parsedURL = Uri.parse(url)
        if (
            (!parsedURL.scheme.equals("http", ignoreCase = true) && !parsedURL.scheme.equals("https", ignoreCase = true)) ||
            parsedURL.host == null
        ) {
            return
        }
        observedURLs.remove(url)
        observedURLs.add(url)
        while (observedURLs.size > MAXIMUM_OBSERVED_URL_COUNT) {
            observedURLs.remove(observedURLs.first())
        }
    }

    private fun isAllowedNavigationURL(url: String): Boolean {
        val parsedURL = Uri.parse(url)
        return when (parsedURL.scheme?.lowercase()) {
            "http", "https" -> parsedURL.host != null
            "data", "about", "blob" -> true
            else -> false
        }
    }

    private fun fail(message: String) {
        if (state == State.COMPLETING || state == State.CLOSED) return
        state = State.CLOSED
        onError(message)
    }
}
