<div align="center">

# 📝 Catatan Perubahan · Changelog

**🌐 Bahasa / Language:** ketuk bagian di bawah untuk membuka / tap a section below to expand it

[⬅️ README (ID)](README.md) · [⬅️ README (EN)](README.en.md)

</div>

---

## ✦ v2.0 — 13 Oktober 2026 / 13 October 2026

> 🌐 **Pilih bahasa / Choose your language** — ketuk salah satu untuk membuka / tap a section to expand it.

<details open>
<summary><h2>🇮🇩 Bahasa Indonesia</h2></summary>

### ⚡ Peningkatan

- Buka aplikasi jauh lebih cepat: versi klien disimpan, beranda disimpan di HP, beberapa klien dicoba bersamaan saat pertama dibuka
- Streaming lebih lancar: download bertahap, ukuran potongan adaptif, lompat ke keyframe, buffer lebih besar
- Video yang terlihat disiapkan lebih dulu, jadi langsung diputar saat diketuk
- Pemutaran tetap jalan saat layar mati
- Coba ulang otomatis dan ganti klien saat stream gagal
- Laporan crash bawaan dengan tombol "Salin laporan"

### 🐞 Perbaikan
- Pemutaran tidak lagi berhenti setelah 1 MB pertama (HTTP 403)
- Lirik tidak lagi macet di "Mencari lirik…" setelah ganti lagu
- Lirik offline tidak lagi hilang di beberapa lagu yang di-download
- Download tidak lagi macet di 100%
- Download playlist kini berisi persis lagu di playlist itu
- Notifikasi download massal tidak lagi menumpuk
- Musik tidak lagi berhenti saat layar mati
- Tombol putar kini memuat ulang stream yang macet
- Filter pencarian kini benar-benar mengubah hasil
- Loading yang berputar tanpa henti di ujung daftar sudah dihilangkan
- Video dan Shorts channel tidak lagi berhenti di halaman pertama
- Status Subscribe kini tampil benar di halaman channel dan hasil pencarian
- Live chat tidak lagi muncul di video yang tidak sedang live
- Urutan playlist kini tersimpan dengan benar
- "Simpan ke playlist" dan "Tonton nanti" tetap berhasil walau panelnya sudah ditutup
- Video dan Shorts yang ditonton kini muncul di histori YouTube
- Error di proses latar belakang tidak lagi membuat aplikasi force close
- Beberapa force close acak sudah diperbaiki

</details>

<details>
<summary><h2>🇬🇧 English</h2></summary>

### ⚡ Improvements

- Much faster startup: cached client version, disk-cached home feed, parallel clients on cold start
- Smoother streaming: chunked downloads, adaptive chunk size, keyframe seeking, a larger buffer
- Visible videos are prepared in advance, so playback starts almost instantly
- Playback keeps running with the screen off
- Automatic network retry and client switching when a stream fails
- Built-in crash reporter with a "Copy report" button

### 🐞 Fixes
- Playback no longer stops after the first 1 MB (HTTP 403)
- Lyrics no longer get stuck on "Mencari lirik…" (Searching for lyrics…) after switching songs
- Offline lyrics are no longer missing for some downloaded songs
- Downloads no longer freeze at 100%
- A playlist download now contains exactly the playlist's songs
- Batch download notifications no longer pile up
- Music no longer stops when the screen turns off
- The play button now reloads a stalled stream
- Search filters now actually change the results
- Endless loading spinners at the end of lists have been removed
- Channel videos and Shorts no longer stop loading after the first page
- Subscribe status now shows correctly on channel pages and in search results
- Live chat no longer appears on videos that aren't live
- Playlist reordering is now saved reliably
- "Save to playlist" and "Watch later" now work even after the sheet is closed
- Watched videos and Shorts now appear in your YouTube history
- Errors in background tasks no longer crash the app
- Several random force closes fixed

</details>


