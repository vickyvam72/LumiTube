package com.lumitube.app.ui.common

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lumitube.app.CrossApp
import com.lumitube.app.data.Session

/** Layar perkenalan + izin saat aplikasi pertama kali dipasang. */
object Onboarding {
    val done: Boolean get() = Session.getString("onboarded") == "1"
    fun finish() = Session.putString("onboarded", "1")
}

private val BgTop = Color(0xFF0E0F16)
private val BgBottom = Color(0xFF1B1D2B)
private val CardBg = Color(0xFF171926)
private val CardBorder = Color(0xFF2C3044)
private val Sub = Color(0xFFA3A8BD)
private val Accent = Brush.horizontalGradient(listOf(Color(0xFFFF5A52), Color(0xFFD50000)))
private val Good = Color(0xFF34D399)
private val Amber = Color(0xFFF5B547)

private val appName get() = if (CrossApp.isMusic) "YT Music Lumi" else "YouTube Lumi"

@Composable
private fun Screen(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
            .statusBarsPadding().navigationBarsPadding(),
    ) { content() }
}

@Composable
private fun AppIcon(size: Int) {
    val ctx = LocalContext.current
    val bmp = remember {
        try {
            ctx.packageManager.getApplicationIcon(ctx.packageName).toBitmap(256, 256).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    if (bmp != null) {
        Image(bmp, null, Modifier.size(size.dp).clip(CircleShape))
    } else {
        Box(Modifier.size(size.dp).clip(CircleShape).background(Accent))
    }
}

@Composable
private fun PrimaryButton(text: String, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp))
            .then(if (enabled) Modifier.background(Accent) else Modifier.background(Color(0xFF262A3B)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, color = if (enabled) Color.White else Color(0xFF7D8299), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ---------------------------------------------------------------- 1. Perkenalan

@Composable
fun WelcomeScreen(onConnect: () -> Unit, onSkip: () -> Unit) {
    val music = CrossApp.isMusic
    val features = if (music) listOf(
        Triple(Icons.Filled.MusicNote, "Lirik karaoke & PiP lirik", "Lirik sinkron per kata dari 11 sumber, tetap tampil di jendela kecil saat aplikasi diminimalkan."),
        Triple(Icons.Filled.Groups, "Listen Together", "Dengar lagu yang sama bareng teman secara bersamaan, cukup dengan kode room."),
        Triple(Icons.Filled.DownloadForOffline, "Download & lirik offline", "Simpan lagu atau satu playlist penuh, lengkap dengan liriknya."),
    ) else listOf(
        Triple(Icons.Filled.PlayCircle, "Tanpa iklan & latar belakang", "Video tetap jalan saat layar mati, bisa juga Picture-in-Picture."),
        Triple(Icons.Filled.Groups, "Watch Together", "Nonton video yang sama bareng teman secara bersamaan dengan kode room."),
        Triple(Icons.Filled.DownloadForOffline, "Download offline", "Simpan video atau satu playlist penuh untuk ditonton tanpa internet."),
    )
    Screen {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            AppIcon(96)
            Spacer(Modifier.height(18.dp))
            Text(appName, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(
                if (music) "YouTube Music yang ringan, tanpa iklan, lengkap dengan lirik karaoke."
                else "YouTube yang ringan, tanpa iklan, dan bisa diputar di latar belakang.",
                color = Sub, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp,
            )
            Spacer(Modifier.height(28.dp))
            features.forEach { (icon, title, desc) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(CardBg),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, tint = Color(0xFFFF6B63), modifier = Modifier.size(24.dp)) }
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(desc, color = Sub, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (Session.loggedIn) {
                PrimaryButton("Lanjut", onClick = onSkip)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Sudah masuk sebagai ${Session.account?.name ?: "akun Google"}.",
                    color = Sub, fontSize = 12.sp, textAlign = TextAlign.Center,
                )
            } else {
                PrimaryButton("Hubungkan akun Google", Icons.Filled.Lock, onClick = onConnect)
                Box(
                    Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp)).clickable(onClick = onSkip),
                    contentAlignment = Alignment.Center,
                ) { Text("Lewati", color = Color.White, fontSize = 15.sp) }
                Text(
                    "Login dilakukan langsung di halaman resmi accounts.google.com. Sesi login hanya disimpan di HP-mu, dan bisa dilakukan nanti dari tab Anda.",
                    color = Color(0xFF7D8299), fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 17.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------- 2. Izin

private fun notificationsGranted(ctx: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

private fun batteryIgnored(ctx: Context): Boolean {
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(ctx.packageName)
}

private fun tryStart(ctx: Context, intent: Intent): Boolean = try {
    ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: Exception) {
    false
}

private fun requestBattery(ctx: Context) {
    val pkg = Uri.parse("package:${ctx.packageName}")
    if (!tryStart(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))) {
        tryStart(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

/** Halaman "Mulai otomatis" bawaan tiap merek HP (berbeda dengan penghemat baterai). */
private fun openAutostart(ctx: Context) {
    val candidates = listOf(
        // Xiaomi / Redmi / POCO (MIUI & HyperOS): "Mulai otomatis di latar belakang"
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        // Oppo / Realme / OnePlus (ColorOS)
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        // Vivo / iQOO
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        // Infinix / Tecno / itel (Transsion)
        "com.transsion.phonemaster" to "com.cyin.himgr.autostart.AutoStartActivity",
        // Huawei / Honor
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        // Asus
        "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity",
    )
    for ((pkg, cls) in candidates) {
        if (tryStart(ctx, Intent().setComponent(ComponentName(pkg, cls)))) {
            Toast.makeText(ctx, "Aktifkan $appName di daftar ini", Toast.LENGTH_LONG).show()
            return
        }
    }
    // Merek lain: halaman info aplikasi (biasanya ada pilihan "Mulai otomatis" / "Baterai").
    tryStart(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
    Toast.makeText(ctx, "Cari pilihan \"Mulai otomatis\" atau \"Autostart\" untuk $appName", Toast.LENGTH_LONG).show()
}

@Composable
private fun PermCard(
    icon: ImageVector,
    title: String,
    desc: String,
    granted: Boolean,
    action: String,
    optional: Boolean = false,
    onClick: () -> Unit,
) {
    val border = when {
        granted -> Good.copy(alpha = 0.55f)
        optional -> Amber.copy(alpha = 0.45f)
        else -> CardBorder
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardBg)
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .clickable(enabled = !granted || optional, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                .background(if (granted) Good.copy(alpha = 0.15f) else Color(0xFF22263A)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (granted && !optional) Icons.Filled.CheckCircle else icon, null,
                tint = if (granted && !optional) Good else if (optional) Amber else Color(0xFFFF6B63),
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = if (optional) Amber else Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
            Text(desc, color = Sub, fontSize = 12.sp, lineHeight = 15.5.sp)
            if (granted && !optional) Text("✓ Sudah diizinkan", color = Good, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp))
        }
        if (!granted || optional) {
            Box(
                Modifier.clip(RoundedCornerShape(16.dp))
                    .then(
                        if (optional) Modifier.border(1.dp, Amber.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        else Modifier.background(Accent)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text(action, color = if (optional) Amber else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
fun PermissionScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    // Perbarui status saat kembali dari halaman pengaturan.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val notif = remember(tick) { notificationsGranted(ctx) }
    val battery = remember(tick) { batteryIgnored(ctx) }
    var linksDone by remember { mutableStateOf(CrossApp.linksEnabled(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val music = CrossApp.isMusic

    Screen {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            AppIcon(48)
            Text("Satu langkah lagi", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
            Text(
                "Supaya ${if (music) "musik" else "video"} tetap jalan saat layar mati dan kontrol pemutaran muncul di notifikasi, dua izin pertama wajib diaktifkan. Dua lainnya opsional.",
                color = Sub, fontSize = 13.5.sp, lineHeight = 19.sp,
            )
            PermCard(
                Icons.Filled.Notifications, "Izinkan notifikasi",
                "Untuk kontrol putar/jeda di layar kunci dan progres download.",
                granted = notif, action = "Izinkan",
            ) {
                if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else tryStart(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
            PermCard(
                Icons.Filled.BatteryChargingFull, "Izinkan berjalan di latar belakang",
                "Mengecualikan $appName dari penghemat baterai agar pemutaran tidak terhenti.",
                granted = battery, action = "Izinkan",
            ) { requestBattery(ctx) }
            PermCard(
                Icons.Filled.PlayCircle, "Pengguna Xiaomi, Oppo, Vivo, Realme & Infinix",
                "Opsional: aktifkan \"Mulai otomatis\" karena HP merek ini sering menutup aplikasi di latar belakang.",
                granted = false, action = "Buka", optional = true,
            ) { openAutostart(ctx) }
            PermCard(
                Icons.Filled.Link, if (music) "Buka link YouTube Music di sini" else "Buka link YouTube di sini",
                "Opsional: link ${if (music) "music.youtube.com" else "YouTube"} yang dibuka atau dibagikan langsung diputar di $appName, bukan di aplikasi asli.",
                granted = false, action = if (linksDone) "Ulangi" else "Atur", optional = true,
            ) {
                CrossApp.setLinksEnabled(ctx, true)
                linksDone = true
                CrossApp.startLinkSetup(ctx)
            }
            Spacer(Modifier.height(2.dp))
            PrimaryButton(
                if (notif && battery) "Mulai pakai $appName" else "Aktifkan kedua izin dulu",
                enabled = notif && battery,
                onClick = onDone,
            )
        }
    }
}
