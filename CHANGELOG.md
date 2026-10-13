<div align="center">

# 📝 Changelog · Catatan Perubahan

**🌐 Language / Bahasa:** tap a section below to expand it / ketuk bagian di bawah untuk membuka

[⬅️ README (EN)](README.md) · [⬅️ README (ID)](README.id.md)

</div>

---

## ✦ v2.0 — 13 October 2026 / 13 Oktober 2026

> 🌐 **Choose your language / Pilih bahasa** — tap a section to expand it / ketuk salah satu untuk membuka.

<details open>
<summary><h2>🇬🇧 English</h2></summary>

### ⚡ Improvements

**📦 Apps**
- Split into two dedicated, ultra-light apps (about 3 MB each): **YouTube Lumi** and **YT Music Lumi**
- Send any video to YT Music Lumi from the ⋮ menu

**🎬 Player**
- YouTube-style controls: double-tap ±10 s, press and hold either side for 2× speed
- Chapters on the timeline and a sharp thumbnail preview while scrubbing
- Quality, speed, loop, subtitles with auto-translate, original audio track
- Subtitles toggle instantly without reloading the video
- Picture-in-Picture for videos, with the screen kept on
- Sleep timer, with an option to stop when the current song or video ends

**📱 Shorts**
- Shorts feed from YouTube's own recommendations, tailored to your account
- Full player controls: seek preview, hold for 2×, captions, quality and settings
- Comments, like, subscribe, share and download from inside the player
- Your position is kept when you open a channel and come back
- Faster swiping: the next Shorts are prepared in advance

**📺 Channels**
- Full channel page: banner, avatar, @handle, stats, description and links
- Tabs: Videos · Shorts · Live · Releases · Playlists · Posts (empty tabs hidden)
- Sorting: Latest · Popular · Oldest
- About page with clickable links, country, join date and views
- Posts with clickable links and full-screen, pinch-to-zoom images
- Accurate Subscribe / Unsubscribe state

**🔎 Search**
- Filters: All · Videos · Shorts (Shorts as a 3-column grid)
- Channel cards with Subscribe and View channel buttons
- Search suggestions, local history and search inside a channel
- Searches are sent without your account, so they never appear in your official YouTube / YouTube Music search history
- Results are kept after you watch a video, with a "No more results" marker at the end

**📚 Library and account**
- History grouped by day with All / Videos / Shorts tabs, search and watch progress
- Watched videos and Shorts are registered to your account history
- ⋮ menu on every video: Add to playlist · Download · Share · Listen in YT Music Lumi
- Save to Watch later or any playlist, or create a new one
- Drag-to-reorder playlist items, remove items, and delete playlists
- Pull-to-refresh on feeds, with the scroll position kept

**🔴 Live streams**
- Live badges, start at the live edge, and a "Live" button
- Real-time live chat, including a side panel in full screen

**🎵 YT Music Lumi**
- 🎤 **Lyrics in Picture-in-Picture** when the app is minimized
- 🎶 Word-synced karaoke lyrics from **11 providers**: BetterLyrics, BetterLyrics QQ, YouLyPlus, LyricsPlus, Unison, SimpMusic, BiniLyrics, lrc.red, LRCLIB, KuGou and YouTube Music
- Search that mirrors YouTube Music: top result card, filters and full results
- Repeat one / repeat all for each queue type
- Original song titles in the player
- Playback resumes and lyrics reload automatically after a network drop or audio interruption

**📥 Downloads**
- Download single items or entire playlists
- Downloads page with auto-collections (Songs / Videos) and downloaded playlists
- Lyrics saved with every music download, with automatic backfill when you're back online
- Lightweight batch downloads: 144p MP4 for music, up to 720p for YouTube
- One tidy, auto-clearing notification for each batch

**🚀 Performance and reliability**
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

<details>
<summary><h2>🇮🇩 Bahasa Indonesia</h2></summary>

### ⚡ Peningkatan

**📦 Aplikasi**
- Dipisah menjadi dua aplikasi khusus yang super ringan (sekitar 3 MB): **YouTube Lumi** dan **YT Music Lumi**
- Kirim video mana pun ke YT Music Lumi dari menu ⋮

**🎬 Pemutar**
- Kontrol gaya YouTube: ketuk dua kali ±10 detik, tekan lama sisi kiri atau kanan untuk kecepatan 2×
- Bab di garis waktu dan pratinjau gambar yang tajam saat menggeser
- Kualitas, kecepatan, ulang, subtitle dengan terjemahan otomatis, trek audio asli
- Subtitle langsung menyala atau mati tanpa memuat ulang video
- Picture-in-Picture untuk video, dengan layar tetap menyala
- Timer tidur, bisa juga berhenti saat lagu atau video selesai

**📱 Shorts**
- Feed Shorts dari rekomendasi asli YouTube, sesuai akunmu
- Kontrol pemutar lengkap: pratinjau saat menggeser, tahan untuk 2×, teks, kualitas, dan pengaturan
- Komentar, suka, subscribe, bagikan, dan download langsung dari pemutar
- Posisi tetap diingat saat kamu membuka channel lalu kembali
- Geser lebih cepat karena Shorts berikutnya disiapkan lebih dulu

**📺 Channel**
- Halaman channel lengkap: banner, foto, @handle, statistik, deskripsi, dan link
- Tab: Video · Shorts · Live · Rilis · Playlist · Postingan (tab kosong disembunyikan)
- Urutan: Terbaru · Populer · Terlama
- Halaman selengkapnya dengan link yang bisa diklik, negara, tanggal bergabung, dan penayangan
- Postingan dengan link yang bisa diklik dan gambar layar penuh yang bisa di-zoom
- Status Subscribe / Disubscribe yang akurat

**🔎 Pencarian**
- Filter: Semua · Video · Shorts (Shorts tampil sebagai grid 3 kolom)
- Kartu channel dengan tombol Subscribe dan Lihat Channel
- Saran pencarian, histori di HP, dan pencarian di dalam channel
- Pencarian dikirim tanpa akun, jadi tidak pernah muncul di histori pencarian YouTube / YouTube Music resmi
- Hasil tetap ada setelah menonton video, dengan tanda "Tidak ada hasil lain lagi" di ujungnya

**📚 Koleksi dan akun**
- Histori per hari dengan tab Semua / Video / Shorts, pencarian, dan progres tontonan
- Video dan Shorts yang ditonton tercatat di histori akunmu
- Menu ⋮ di setiap video: Tambahkan ke playlist · Download · Bagikan · Dengarkan di YT Music Lumi
- Simpan ke Tonton nanti atau playlist mana pun, atau buat playlist baru
- Atur urutan isi playlist dengan seret, hapus item, dan hapus playlist
- Tarik-untuk-refresh di feed, posisi scroll tetap diingat

**🔴 Siaran langsung**
- Tanda live, mulai dari bagian paling baru, dan tombol "Live"
- Live chat real-time, termasuk panel samping di layar penuh

**🎵 YT Music Lumi**
- 🎤 **Lirik di Picture-in-Picture** saat aplikasi diminimalkan
- 🎶 Lirik karaoke sinkron per kata dari **11 penyedia**: BetterLyrics, BetterLyrics QQ, YouLyPlus, LyricsPlus, Unison, SimpMusic, BiniLyrics, lrc.red, LRCLIB, KuGou, dan YouTube Music
- Pencarian seperti YouTube Music: kartu hasil teratas, filter, dan hasil lengkap
- Ulangi satu / ulangi semua untuk tiap jenis antrean
- Judul lagu asli di pemutar
- Lagu lanjut dan lirik dimuat ulang otomatis setelah internet putus atau audio terganggu

**📥 Download**
- Download satu item atau satu playlist penuh
- Halaman Download dengan koleksi otomatis (Lagu / Video) dan playlist yang di-download
- Lirik ikut tersimpan di setiap download musik, dan dilengkapi otomatis saat internet kembali
- Download massal yang ringan: MP4 144p untuk musik, maksimal 720p untuk YouTube
- Satu notifikasi rapi yang hilang sendiri untuk tiap download massal

**🚀 Performa dan keandalan**
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

