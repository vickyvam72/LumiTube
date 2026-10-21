package com.lumitube.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
            com.lumitube.app.ui.common.LumiToast.makeText(context, com.lumitube.app.tr("%s belum terpasang", otherName), Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            com.lumitube.app.ui.common.LumiToast.makeText(context, com.lumitube.app.tr("Tidak bisa membuka %s", otherName), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Link YouTube / YouTube Music yang dibuka atau dibagikan ke aplikasi ini:
     * youtube.com/watch?v=, youtu.be/, /shorts/, /live/, /embed/, music.youtube.com, playlist (list=).
     */
    fun itemFromLink(intent: Intent?): Item? {
        if (intent == null) return null
        val text = intent.dataString ?: intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        val url = Regex("https?://\\S+").find(text)?.value ?: text
        if (!url.contains("youtu")) return null
        val video = Regex("[?&]v=([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)
            ?: Regex("youtu\\.be/([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)
            ?: Regex("/(?:embed|live|v)/([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)
        val short = Regex("/shorts/([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)
        val list = Regex("[?&]list=([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1)
        intent.data = null
        intent.removeExtra(Intent.EXTRA_TEXT)
        return when {
            short != null -> Item(if (isMusic) ItemType.SONG else ItemType.SHORT, short, "")
            video != null -> Item(if (isMusic) ItemType.SONG else ItemType.VIDEO, video, "", playlistId = list)
            list != null && !isMusic -> Item(ItemType.PLAYLIST, "VL$list", "Playlist")
            else -> null
        }
    }

    /** Item yang dikirim aplikasi pasangan lewat [openOther], bila ada. */
    fun itemFrom(intent: Intent?): Item? {
        itemFromLink(intent)?.let { return it }
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

    // ---------------- Link YouTube ----------------

    private fun linkComponent(ctx: Context) = android.content.ComponentName(ctx.packageName, "com.lumitube.app.LinkHandler")

    fun linksEnabled(ctx: Context): Boolean =
        ctx.packageManager.getComponentEnabledSetting(linkComponent(ctx)) !=
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    /** Aktif/nonaktifkan penanganan link YouTube oleh aplikasi ini. */
    fun setLinksEnabled(ctx: Context, on: Boolean) {
        ctx.packageManager.setComponentEnabledSetting(
            linkComponent(ctx),
            if (on) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.DONT_KILL_APP,
        )
    }

    /**
     * Android 12+ tidak mengizinkan aplikasi pihak ketiga otomatis menjadi pembuka link youtube.com;
     * pengguna perlu menambahkan link di "Buka secara default". Buka halaman itu langsung.
     */
    fun openLinkSettings(ctx: Context) {
        val uri = android.net.Uri.parse("package:${ctx.packageName}")
        val intent = if (android.os.Build.VERSION.SDK_INT >= 31) {
            Intent(android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, uri)
        } else {
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
        }
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            ctx.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Aplikasi resmi yang biasanya memegang link youtube.com / music.youtube.com. */
    val originalPackage: String get() = if (isMusic) "com.google.android.apps.youtube.music" else "com.google.android.youtube"
    val originalName: String get() = if (isMusic) "YouTube Music" else "YouTube"
    val ownName: String get() = if (isMusic) "YT Music Lumi" else "YouTube Lumi"

    /** Panduan "Atur ulang link" yang sedang tampil: 0 = tidak ada, 1 = langkah 1, 2 = langkah 2. */
    var guideStep by androidx.compose.runtime.mutableIntStateOf(0)

    private var pendingOwnLinkSettings = false

    private fun openByDefault(ctx: Context, pkg: String) {
        val uri = android.net.Uri.parse("package:$pkg")
        val intent = if (android.os.Build.VERSION.SDK_INT >= 31) {
            Intent(android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, uri)
        } else {
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
        }
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            try {
                ctx.startActivity(
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Mulai panduan: langkah 1 melepas link dari YouTube / YouTube Music asli (bila terpasang),
     * langkah 2 menyambungkan link ke aplikasi ini. Tiap langkah dijelaskan dulu di dialog.
     */
    fun startLinkSetup(ctx: Context) {
        guideStep = if (isInstalled(ctx, originalPackage)) 1 else 2
    }

    /** Tombol di dialog panduan: buka halaman pengaturan untuk langkah yang sedang tampil. */
    fun continueGuide(ctx: Context) {
        val step = guideStep
        guideStep = 0
        if (step == 1) {
            pendingOwnLinkSettings = true
            openByDefault(ctx, originalPackage)
        } else {
            openByDefault(ctx, ctx.packageName)
        }
    }

    /** Dipanggil saat aplikasi kembali tampil (onResume): lanjut ke langkah 2. */
    fun resumeLinkSetup(ctx: Context) {
        if (!pendingOwnLinkSettings) return
        pendingOwnLinkSettings = false
        guideStep = 2
    }
}
