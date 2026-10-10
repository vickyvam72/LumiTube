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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Root() }
    }

    @Composable
    private fun Root() {
        var mode by rememberSaveable { mutableStateOf(Session.mode) }
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

        fun switchTo(m: String) {
            mode = m
            Session.mode = m
        }

        LumiTheme(dark = dark) {
            Box(Modifier.fillMaxSize()) {
                run {
                    if (mode == "music") {
                        MusicApp(
                            pendingPlay = pendingPlay,
                            onPendingConsumed = { pendingPlay = null },
                            onSwitchVideo = { switchTo("video") },
                            onLogin = { showLogin = true },
                            onOpenVideo = { item ->
                                val video = item.copy(type = ItemType.VIDEO)
                                if (PlaybackManager.current?.id == item.id) {
                                    PlaybackManager.switchMode(video = true)
                                } else {
                                    PlaybackManager.playVideo(video)
                                }
                                pendingWatch = video
                                switchTo("video")
                            },
                        )
                    } else {
                        YouTubeApp(
                            pendingWatch = pendingWatch,
                            onPendingConsumed = { pendingWatch = null },
                            onSwitchMusic = { switchTo("music") },
                            onLogin = { showLogin = true },
                            onPlayInMusic = { item ->
                                pendingPlay = item
                                switchTo("music")
                            },
                        )
                    }
                    if (showLogin) LoginScreen(onClose = { showLogin = false })
                }
                // Mode Picture-in-Picture: tampilkan video saja di atas UI (state UI tetap terjaga).
                if (inPip) {
                    if (PlaybackManager.isVideo) {
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

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val videoPip = PlaybackManager.isVideo && PlaybackManager.isPlaying &&
            PlaybackManager.current?.type != ItemType.SHORT
        // Mode Music: tampilkan PiP lirik (bisa dimatikan di Setelan Music)
        val lyricsPip = !PlaybackManager.isVideo && PlaybackManager.isPlaying && Session.lyricsPip
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && (videoPip || lyricsPip)) {
            if (lyricsPip) PlaybackManager.loadLyrics()
            try {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                )
            } catch (_: Exception) {
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }
}
