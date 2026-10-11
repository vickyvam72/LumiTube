package com.lumitube.app.player

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lumitube.app.data.JsRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

/**
 * Mesin JavaScript tanpa tampilan memakai WebView (seperti PoTokenWebView milik NewPipe).
 * Skrip pemutar YouTube dijalankan di sini untuk memecahkan sig dan parameter n.
 */
class WebJsRunner(private val context: Context) : JsRunner {
    private var webView: WebView? = null
    private var key: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun load(key: String, script: String): Boolean = withContext(Dispatchers.Main) {
        if (this@WebJsRunner.key == key && webView != null) return@withContext true
        webView?.destroy()
        webView = null
        this@WebJsRunner.key = null
        val bytes = script.toByteArray()
        val wv = try {
            WebView(context.applicationContext)
        } catch (_: Exception) {
            return@withContext false
        }
        wv.settings.javaScriptEnabled = true
        wv.settings.blockNetworkImage = true
        val loaded = withTimeoutOrNull(25_000) {
            suspendCancellableCoroutine { cont ->
                var done = false
                wv.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse {
                        val url = request?.url?.toString() ?: ""
                        return if (url == SCRIPT_URL) {
                            WebResourceResponse("application/javascript", "utf-8", ByteArrayInputStream(bytes))
                        } else {
                            // Jangan biarkan skrip pemutar memuat apa pun dari internet.
                            WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (!done) {
                            done = true
                            if (cont.isActive) cont.resume(true)
                        }
                    }
                }
                wv.loadDataWithBaseURL("https://www.youtube.com/", HTML, "text/html", "utf-8", null)
            }
        } ?: false
        if (loaded) {
            webView = wv
            this@WebJsRunner.key = key
        } else {
            wv.destroy()
        }
        loaded
    }

    override suspend fun eval(js: String): String? = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext null
        withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                wv.evaluateJavascript(js) { r -> if (cont.isActive) cont.resume(r) }
            }
        }
    }

    private companion object {
        const val SCRIPT_URL = "https://www.youtube.com/lumi/player_ias/base.js"
        const val HTML = "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
            "<script>window._yt_player=window._yt_player||{};</script>" +
            "<script src=\"$SCRIPT_URL\"></script></head><body></body></html>"
    }
}
