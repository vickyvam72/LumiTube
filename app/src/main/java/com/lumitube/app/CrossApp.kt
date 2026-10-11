package com.lumitube.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.lumitube.app.data.Item
import com.lumitube.app.data.ItemType
import com.lumitube.app.player.PlaybackManager

/**
 * YouTube Lumi dan YT Music Lumi adalah dua aplikasi terpisah dari kode yang sama.
 * Objek ini menentukan aplikasi mana yang sedang berjalan dan membuka aplikasi
 * pasangannya (misalnya "Dengarkan di YouTube Music").
 */
object CrossApp {
    const val YOUTUBE_PACKAGE = "com.lumitube.app"
    const val MUSIC_PACKAGE = "com.lumitube.music"

    val isMusic: Boolean get() = BuildConfig.APP_MODE == "music"
    val mode: String get() = if (isMusic) "music" else "video"
    val otherName: String get() = if (isMusic) "YouTube Lumi" else "YT Music Lumi"

    private const val EXTRA_ID = "lumi_id"

    /** Buka aplikasi pasangan; bila [item] diberikan, langsung putar item itu di sana. */
    fun openOther(context: Context, item: Item? = null) {
        val pkg = if (isMusic) YOUTUBE_PACKAGE else MUSIC_PACKAGE
        val intent = Intent(Intent.ACTION_MAIN)
            .setClassName(pkg, "com.lumitube.app.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (item != null) {
            intent.putExtra(EXTRA_ID, item.id)
                .putExtra("lumi_title", item.title)
                .putExtra("lumi_subtitle", item.subtitle)
                .putExtra("lumi_thumb", item.thumbnail)
                .putExtra("lumi_channel", item.channelName)
                .putExtra("lumi_channel_id", item.channelId)
        }
        try {
            context.startActivity(intent)
            // Hanya satu pemutar yang berbunyi.
            if (item != null) PlaybackManager.stop()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "$otherName belum terpasang", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "Tidak bisa membuka $otherName", Toast.LENGTH_SHORT).show()
        }
    }

    /** Item yang dikirim aplikasi pasangan lewat [openOther], bila ada. */
    fun itemFrom(intent: Intent?): Item? {
        val id = intent?.getStringExtra(EXTRA_ID) ?: return null
        intent.removeExtra(EXTRA_ID)
        return Item(
            type = if (isMusic) ItemType.SONG else ItemType.VIDEO,
            id = id,
            title = intent.getStringExtra("lumi_title") ?: "",
            subtitle = intent.getStringExtra("lumi_subtitle") ?: "",
            thumbnail = intent.getStringExtra("lumi_thumb"),
            channelName = intent.getStringExtra("lumi_channel"),
            channelId = intent.getStringExtra("lumi_channel_id"),
        )
    }
}
