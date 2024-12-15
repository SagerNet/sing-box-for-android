package io.nekohasekai.sfa.ui.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import io.nekohasekai.sfa.R


class ShopFragment : Fragment() {
    private var orderId: String? = null


    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_shop, container, false)
        val webView: WebView = view.findViewById(R.id.webview)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                when {
                    url.contains("https://app.rattleprotocol.store/checkout/") -> view.evaluateJavascript("(function() { " +
                            "    var orderIdElement = document.getElementById('order-id');" +
                            "    if(orderIdElement) {" +
                            "        return orderIdElement.textContent.trim();" +
                            "    }" +
                            "    return '';" +
                            "})()") { value ->
                        // This callback will be called with the result of the JavaScript expression.
                        // You can use it for debugging.
                        Log.d("WebView", "Order ID: $value")
                        // Save the order ID
                         orderId = value
                    }
                    url == "https://wallet.ir/" -> view.evaluateJavascript("(function() { " +
                            "function checkAndClickLink() {" +
                            "    var link = document.querySelector('a.header-button[href=\"/account/profile\"]');" +
                            "    if(link) {" +
                            "        link.click();" +
                            "    } else {" +
                            "        setTimeout(checkAndClickLink, 1000);" +
                            "    }" +
                            "}" +
                            "checkAndClickLink();" +
                            "})()"){ value ->
                        // This callback will be called with the result of the JavaScript expression.
                        // You can use it for debugging.
                        Log.d("WebView", "Result: $value")
                    }
                    url == "https://wallet.ir/account/profile" -> view.evaluateJavascript("(function() { " +
                            "function checkAndClickButton() {" +
                            "    var button = document.querySelector('.btn.btn-primary.d-block.w-100.mb-4');" +
                            "    if(button && !button.disabled) {" +
                            "        button.click();" +
                            "    } else if(button && button.disabled) {" +
                            "    window.location.href = 'https://wallet.ir/account/order';" +
                            "    } else {" +
                            "        setTimeout(checkAndClickButton, 1000);" +
                            "    }" +
                            "}" +
                            "setTimeout(checkAndClickButton, 2000);" +
                            "})()"){ value ->
                        // This callback will be called with the result of the JavaScript expression.
                        // You can use it for debugging.
                        Log.d("WebView", "Result: $value")
                        }
                    url == "https://wallet.ir/account/order" -> view.evaluateJavascript("(function() { " +
                            "function fillInput(inputField, value) {" +
                            "    inputField.value = value;" +
                            "    let event = new Event('input', {bubbles: true});" +
                            "    inputField.dispatchEvent(event);" +
                            "    event = new Event('change', {bubbles: true});" +
                            "    inputField.dispatchEvent(event);" +
                            "}" +
                            "function checkAndFillInput() {" +
                            "    let inputField = document.querySelector('form div.mb-4.pb-2:nth-of-type(2) input');" +
                            "    if (inputField) {" +
                            "        fillInput(inputField, 2);" +
                            "        checkAndClickButton();" +
                            "    }" +
                            "}" +
                            "function checkAndClickButton() {" +
                            "    let button = document.querySelector('button.btn.btn-primary.d-block.w-100');" +
                            "    if (button && !button.disabled) {" +
                            "        button.click();" +
                            "    }" +
                            "}" +
                            "setTimeout(checkAndFillInput, 3000);" +
                            "})()") { value ->
                        // This callback will be called with the result of the JavaScript expression.
                        // You can use it for debugging.
                        Log.d("WebView", "Result: $value")
                    }
                    url.contains("https://wallet.ir/account/order/buy/") -> view.evaluateJavascript("""(function() {
                                try {
                         var targetInput = document.querySelector('.buy-form .form-input .form-input__inner .form-input__html');
                            if(targetInput) {
                                var value = 'TDS16eDEMCituJACncFiDaWhyDvmPBQagY';
                                function simulateKeyEvent(character) {
                                    var evt = new KeyboardEvent('keydown', {'key':character, 'code':character, 'bubbles':true});
                                    targetInput.dispatchEvent(evt);
                                    evt = new KeyboardEvent('keypress', {'key':character, 'code':character, 'bubbles':true});
                                    targetInput.dispatchEvent(evt);
                                    targetInput.value += character;
                                    evt = new KeyboardEvent('keyup', {'key':character, 'code':character, 'bubbles':true});
                                    targetInput.dispatchEvent(evt);
                                    evt = new Event('input', {'bubbles': true});
                                    targetInput.dispatchEvent(evt);
                                    evt = new Event('change', {'bubbles': true});
                                    targetInput.dispatchEvent(evt);
                                }
                                for(var i = 0; i < value.length; i++) {
                                    simulateKeyEvent(value[i]);
                                }
                            }
                                    var button = document.querySelector('.btn.btn-primary.d-block.w-100.mb-4.mt-4');
                                    if(button) {
                                        button.disabled = false;
                                        button.click();
                                    }
                                } catch (error) {
                                    return 'Error: ' + error.toString();
                                }
                            })()
                        """)
                    { value ->
                        // This callback will be called with the result of the JavaScript expression.
                        // You can use it for debugging.
                        Log.d("WebView", "Result3: $value")
                    }
                    url.contains("https://wallet.ir/account/order/buy/invoice") -> view.evaluateJavascript("(function() { " +
                            "var checkExist = setInterval(function() {" +
                            "    if (document.querySelector('.mobile-invoice-success__title')) {" +
                            "        clearInterval(checkExist);" +
                            "        return 'found';" +
                            "    }" +
                            "}, 1000);" +
                            "})()") { value ->
                        // This callback will be called with the result of the JavaScript expression.
                        // You can use it for debugging.
                        Log.d("WebView", "JavaScript returned: $value")
                        // Check if the element was found
                        if (value == "found") {
                            // Load the new URL
                            view.loadUrl("https://app.rattleprotocol.store/idontknowwhat/$orderId")
                        }
                    }
                }
            }
        }

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.loadsImagesAutomatically = true
        WebView.setWebContentsDebuggingEnabled(true);

        webView.loadUrl("https://app.rattleprotocol.store/plans/")
        return view
    }

    companion object {
        @JvmStatic
        fun newInstance() = ShopFragment()
    }
}