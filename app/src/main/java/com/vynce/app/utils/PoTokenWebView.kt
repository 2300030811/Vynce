package com.vynce.app.utils

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class PoTokenExtractor(private val context: Context) {

    private var cachedToken: String? = null
    private var cachedVisitorData: String? = null
    private var lastRefresh: Long = 0
    private val REFRESH_INTERVAL = 25 * 60 * 1000L // 25 minutes

    suspend fun getPoToken(): Pair<String, String>? {
        val now = System.currentTimeMillis()
        if (cachedToken != null && (now - lastRefresh) < REFRESH_INTERVAL) {
            return Pair(cachedToken!!, cachedVisitorData!!)
        }
        return extractFromWebView()
    }

    private suspend fun extractFromWebView(): Pair<String, String>? {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                try {
                    val webView = WebView(context)
                    webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        userAgentString = "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36"
                    }
                    webView.addJavascriptInterface(object : Any() {
                        @android.webkit.JavascriptInterface
                        fun onTokenExtracted(visitorData: String, poToken: String) {
                            cachedToken = poToken
                            cachedVisitorData = visitorData
                            lastRefresh = System.currentTimeMillis()
                            if (cont.isActive) cont.resume(Pair(poToken, visitorData))
                            webView.post { webView.destroy() }
                        }
                    }, "Android")
                    webView.webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: android.webkit.WebView, url: String) {
                            view.evaluateJavascript("""
                                (function() {
                                    var yt = window.yt && window.yt.config_;
                                    if (yt) {
                                        Android.onTokenExtracted(
                                            yt.VISITOR_DATA || '',
                                            yt.INNERTUBE_CONTEXT_CLIENT_INFO?.poToken || ''
                                        );
                                    } else {
                                        Android.onTokenExtracted('', '');
                                    }
                                })();
                            """.trimIndent(), null)
                        }
                    }
                    webView.loadUrl("https://music.youtube.com")
                    cont.invokeOnCancellation { webView.post { webView.destroy() } }
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }
}
