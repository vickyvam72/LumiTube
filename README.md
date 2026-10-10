# LumiTube

Klien YouTube + YouTube Music tidak resmi untuk Android dalam satu aplikasi.

- **Mode YouTube**: tampilan seperti aplikasi YouTube (beranda, chip kategori, Shorts, Subscription, tab Anda, halaman tonton dengan video terkait, subscribe, like, fullscreen, picture-in-picture).
- **Mode Music**: tampilan seperti YouTube Music (latar gelap, chip mood, Pintasan cepat 3x3, rak lagu, mini player, pemutar penuh dengan sakelar Lagu/Video, antrean "Berikutnya", lirik, Jelajahi, Telusuri, Koleksi).
- **Satu login** lewat WebView (cookie akun Google) untuk keduanya: beranda personal, subscription, histori, playlist, tonton nanti, video/lagu yang disukai, album & artis di koleksi.
- Putar di latar belakang dengan notifikasi media dan kontrol layar kunci.
- Pindah mode kapan saja: tombol not musik di YouTube, ikon video di Music, "Putar di Music" di halaman tonton, tombol "YOUTUBE" di pemutar musik.

> Tidak berafiliasi dengan Google atau YouTube. Melanggar ToS YouTube; gunakan untuk pribadi dan sebaiknya pakai akun cadangan.

## Build APK lewat GitHub (tanpa PC)

1. Buat repo baru di github.com (boleh private).
2. Upload seluruh isi folder ini (termasuk folder `.github`, `app`, `gradle`, `keystore`).
3. Buka tab **Actions** → workflow **Build LumiTube APK** berjalan otomatis (atau tekan *Run workflow*).
4. Setelah selesai (±5–8 menit), unduh `LumiTube.apk` dari halaman **Releases** (atau dari artifact di halaman run).

## Build di komputer

Buka folder ini di Android Studio (Ladybug atau lebih baru), lalu **Build → Build APK(s)**, atau jalankan:

```
./gradlew :app:assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`.

## Struktur kode

| Berkas | Isi |
|---|---|
| `data/InnerTube.kt` | Klien API internal YouTube (WEB & WEB_REMIX), header login SAPISIDHASH |
| `data/Parser.kt` | Parser respons (video, Shorts, lagu, album, artis, playlist, rak, chip, halaman tonton) |
| `data/StreamResolver.kt` | Mengambil URL stream (klien VISIONOS, cadangan ANDROID_VR & IOS) |
| `data/Repo.kt` | Fungsi beranda, pencarian, subscription, histori, koleksi, antrean radio, lirik |
| `player/` | ExoPlayer bersama, antrean, MediaSession untuk notifikasi |
| `ui/youtube/` | Semua layar mode YouTube |
| `ui/music/` | Semua layar mode Music + pemutar penuh |
| `ui/common/Login.kt` | Login WebView |

## Kalau pemutaran berhenti berfungsi

YouTube rutin mengubah aturan. Biasanya cukup memperbarui daftar `clients` di `data/StreamResolver.kt`
(nama klien, versi, user agent) dan versi bawaan di `data/InnerTube.kt`. Proyek NewPipeExtractor
(`ClientsConstants.java`) bisa jadi acuan nilai terbaru.

## Catatan

- Kunci tanda tangan ada di `keystore/lumitube.jks` (password `lumitube`) agar APK baru bisa memperbarui yang lama. Jangan pakai kunci ini untuk aplikasi lain.
- Notifikasi YouTube, komentar, upload, download offline, dan tab "Sampel" belum dibuat.
