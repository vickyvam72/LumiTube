package com.lumitube.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.lumitube.app.data.Item
import com.lumitube.app.data.ItemType
import com.lumitube.app.data.Repo
import com.lumitube.app.data.Session
import com.lumitube.app.player.PlaybackManager
import com.lumitube.app.player.PlaybackService
import com.lumitube.app.ui.common.LoginScreen
import com.lumitube.app.ui.common.LumiTheme
import com.lumitube.app.ui.common.VideoSurface
import com.lumitube.app.ui.music.MusicApp
import com.lumitube.app.ui.youtube.YouTubeApp

class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var inPip by mutableStateOf(false)
    /** Item yang dikirim aplikasi pasangan (YouTube Lumi <-> YT Music Lumi). */
    private var incoming by mutableStateOf<Item?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) incoming = CrossApp.itemFrom(intent)
        setContent { Root() }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        CrossApp.itemFrom(intent)?.let { incoming = it }
    }

    @Composable
    private fun Root() {
        val mode = CrossApp.mode
        var showLogin by rememberSaveable { mutableStateOf(false) }
        var pendingWatch by remember { mutableStateOf<Item?>(null) }
        var pendingPlay by remember { mutableStateOf<Item?>(null) }
        val dark = mode == "music" || isSystemInDarkTheme()

        LaunchedEffect(dark) {
            val style = if (dark) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }

        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Session.loggedIn && Session.account == null) {
                try {
                    Repo.account()
                } catch (_: Exception) {
                }
            }
        }

        // Item dari aplikasi pasangan: putar di sini.
        LaunchedEffect(incoming) {
            val item = incoming ?: return@LaunchedEffect
            incoming = null
            if (mode == "music") {
                pendingPlay = item
            } else {
                if (item.type == ItemType.VIDEO) PlaybackManager.playVideo(item)
                pendingWatch = item
            }
        }
        val context = this@MainActivity
        // Mode PiP: layar tetap menyala selama video/lagu diputar (meski timeout layar pendek).
        LaunchedEffect(inPip, PlaybackManager.isPlaying) {
            if (inPip && PlaybackManager.isPlaying) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        LumiTheme(dark = dark) {
            Box(Modifier.fillMaxSize()) {
                run {
                    if (mode == "music") {
                        MusicApp(
                            pendingPlay = pendingPlay,
                            onPendingConsumed = { pendingPlay = null },
                            onSwitchVideo = { CrossApp.openOther(context) },
                            onLogin = { showLogin = true },
                        )
                    } else {
                        YouTubeApp(
                            pendingWatch = pendingWatch,
                            onPendingConsumed = { pendingWatch = null },
                            onSwitchMusic = { CrossApp.openOther(context) },
                            onLogin = { showLogin = true },
                            onPlayInMusic = { item -> CrossApp.openOther(context, item) },
                        )
                    }
                    if (showLogin) LoginScreen(onClose = { showLogin = false })
                }
                // Mode Picture-in-Picture: tampilkan video saja di atas UI (state UI tetap terjaga).
                if (inPip) {
                    // Selama di YouTube Music: PiP lirik. Di YouTube biasa: PiP video.
                    if (mode != "music") {
                        VideoSurface(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black), controller = false)
                    } else {
                        com.lumitube.app.ui.music.LyricsPip()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
    }

    override fun onResume() {
        super.onResume()
        CrossApp.resumeLinkSetup(this)
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val musicMode = CrossApp.isMusic
        // YouTube biasa (termasuk Shorts): PiP video. YouTube Music: PiP lirik (walau sedang mode Video).
        val videoPip = !musicMode && PlaybackManager.isVideo && PlaybackManager.isPlaying
        val lyricsPip = musicMode && PlaybackManager.isPlaying && Session.lyricsPip
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && (videoPip || lyricsPip)) {
            if (lyricsPip) PlaybackManager.loadLyrics()
            val ratio = when {
                lyricsPip -> Rational(16, 9)
                PlaybackManager.current?.type == ItemType.SHORT -> Rational(9, 16)
                else -> {
                    val vs = PlaybackManager.exo.videoSize
                    if (vs.width > 0 && vs.height > 0) {
                        val r = vs.width.toFloat() / vs.height
                        when {
                            r > 2.39f -> Rational(239, 100)
                            r < 0.42f -> Rational(100, 239)
                            else -> Rational(vs.width, vs.height)
                        }
                    } else {
                        Rational(16, 9)
                    }
                }
            }
            try {
                enterPictureInPictureMode(pipParams(ratio, videoPip))
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Posisi jendela PiP ditentukan sistem Android (tidak bisa dipaksa oleh aplikasi).
     * sourceRectHint di bagian atas layar membuat animasinya berangkat dari atas, dan
     * setelah jendela digeser sekali ke kanan atas, Android mengingat posisi itu.
     */
    private fun pipParams(ratio: Rational, video: Boolean): PictureInPictureParams {
        val b = PictureInPictureParams.Builder().setAspectRatio(ratio)
        val w = window.decorView.width
        if (w > 0) {
            val top = androidx.core.view.ViewCompat.getRootWindowInsets(window.decorView)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top ?: 0
            val h = if (video) (w / ratio.toFloat()).toInt().coerceAtMost(window.decorView.height) else w * 9 / 16
            b.setSourceRectHint(android.graphics.Rect(0, top, w, top + h))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) b.setSeamlessResizeEnabled(true)
        return b.build()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        // Jendela PiP ditutup (bukan diperbesar): aktivitas sudah berhenti. Opsional: tutup semuanya.
        if (!isInPictureInPictureMode &&
            lifecycle.currentState == androidx.lifecycle.Lifecycle.State.CREATED &&
            Session.closeOnPipClose
        ) {
            PlaybackManager.stop()
            finishAndRemoveTask()
        }
    }
}
