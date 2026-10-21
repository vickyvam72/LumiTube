package com.lumitube.app.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.lumitube.app.MainActivity
import com.lumitube.app.R

/** Service pemutaran di latar belakang + notifikasi media (dengan tombol Tutup) + kontrol layar kunci. */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    private val closeCommand = SessionCommand(ACTION_CLOSE, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()
        com.lumitube.app.CrashLog.install(this)
        PlaybackManager.init(this)
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val close = CommandButton.Builder()
            .setDisplayName(com.lumitube.app.tr("Tutup"))
            .setIconResId(R.drawable.ic_notif_close)
            .setSessionCommand(closeCommand)
            .build()
        session = MediaSession.Builder(this, PlaybackManager.player)
            .setSessionActivity(intent)
            .setCustomLayout(listOf(close))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(closeCommand).build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == ACTION_CLOSE) closeAll()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()
    }

    /** Tombol Tutup di notifikasi: hentikan video/lagu dan hilangkan notifikasinya. */
    private fun closeAll() {
        PlaybackManager.stop()
        session?.player?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Aplikasi di-clear dari daftar aplikasi terbaru: hentikan semua pemutaran. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        PlaybackManager.stop()
        session?.let { it.player.stop() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    private companion object {
        const val ACTION_CLOSE = "LUMI_CLOSE"
    }
}
