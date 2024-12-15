package io.nekohasekai.sfa.ui.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import io.nekohasekai.sfa.R


class RattleFragment : Fragment() {
    private var orderId: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_rattle, container, false)
        val webView: WebView = view.findViewById(R.id.webview)
        webView.webViewClient = object : WebViewClient() {
        }

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.loadsImagesAutomatically = true
        WebView.setWebContentsDebuggingEnabled(true)
        webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.152 Mobile Safari/537.36"


        webView.loadUrl("https://app.rtlvpn.link/analog")
        return view
    }

    companion object {
        @JvmStatic
        fun newInstance() = RattleFragment()
    }
}