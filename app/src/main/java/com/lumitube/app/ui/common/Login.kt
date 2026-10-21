package com.lumitube.app.ui.common

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lumitube.app.data.Repo
import com.lumitube.app.data.Session
import kotlinx.coroutines.launch

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Ddesktop%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"

/**
 * Login lewat WebView. Setelah Google mengarahkan kembali ke youtube.com / music.youtube.com,
 * cookie (termasuk SAPISID) diambil dan disimpan. Cookie yang sama dipakai untuk
 * YouTube dan YouTube Music.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onClose: () -> Unit) {
    BackHandler { onClose() }
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(0) }
    var done by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = Color.Black)
            }
            Text(
                "Login dengan akun Google",
                color = Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = YtRed,
            )
        }
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val cm = CookieManager.getInstance()
                    cm.removeAllCookies(null)
                    cm.setAcceptCookie(true)
                    WebView(ctx).apply {
                        cm.setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Hilangkan penanda "wv" agar Google tidak menolak login dari WebView.
                        settings.userAgentString = settings.userAgentString
                            .replace("; wv", "")
                            .replace(Regex("Version/\\d+\\.\\d+ "), "")
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                val u = url ?: return
                                if (done) return
                                if (u.startsWith("https://music.youtube.com") ||
                                    u.startsWith("https://www.youtube.com") ||
                                    u.startsWith("https://m.youtube.com")
                                ) {
                                    val cookie = cm.getCookie("https://music.youtube.com")
                                        ?: cm.getCookie("https://www.youtube.com")
                                        ?: return
                                    if (cookie.contains("SAPISID=")) {
                                        done = true
                                        cm.flush()
                                        Session.saveCookie(cookie)
                                        scope.launch {
                                            try {
                                                Repo.account()
                                            } catch (_: Exception) {
                                            }
                                            onClose()
                                        }
                                    }
                                }
                            }
                        }
                        loadUrl(LOGIN_URL)
                    }
                },
            )
        }
    }
}
