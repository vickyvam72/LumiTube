<div align="center">

# ✦ LumiTube ✦

### Lightweight YouTube & YouTube Music clients for Android

*Two apps · one codebase · about 3 MB each · no ads*

[![Release](https://img.shields.io/github/v/release/vickyvam72/LumiTube?label=release&color=e53935&style=for-the-badge)](https://github.com/vickyvam72/LumiTube/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#-requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#%EF%B8%8F-tech-stack)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue?style=for-the-badge)](LICENSE)
[![Size](https://img.shields.io/badge/APK-~3%20MB-00897B?style=for-the-badge)](#-why-lumitube)

**🌐 Bahasa / Language:** &nbsp; [🇮🇩 Bahasa Indonesia](README.md) &nbsp;|&nbsp; **🇬🇧 English**

[⬇️ Download](#-download--install) · [📸 Screenshots](#-screenshots) · [✨ Features](#-features) · [📖 User Guide](#-user-guide) · [❓ FAQ](#-faq--troubleshooting) · [📝 Changelog](CHANGELOG.md)

</div>

---

## 🌟 Why LumiTube?

> **YouTube Lumi** and **YT Music Lumi** do everything you expect from the official apps — and a few things they don't — in a package about **one-twentieth the size**.

| | |
|---|---|
| 🪶 **Ultra-light** | Each APK is about **3 MB**. Starts fast, uses little storage and memory. |
| 🎤 **Lyrics in Picture-in-Picture** | Minimize YT Music Lumi and the **synced lyrics keep scrolling in a small floating window**. You won't find this in the official app. |
| 🎶 **Word-by-word karaoke lyrics** | Searches **11 lyrics providers** at once and prefers word-synced results. |
| 🎧 **Background play, screen off** | Music and videos keep playing when you lock your phone or switch apps. |
| 📥 **Offline downloads** | Save videos, songs and **entire playlists**. Music downloads **include lyrics** for offline use. |
| 🚫 **No ads** | No ads, analytics or trackers. |
| 📱 **Real Shorts feed** | Shorts come from YouTube's own recommendation engine, tailored to your account. |
| 👥 **Watch & Listen Together** | Watch videos or listen to songs **with friends at the same time**, each on their own phone, with just a room code. |
| 🔴 **Download live streams** | Record a **stream while it's live**, from the start of the live or from this second. |
| 🎨 **Light, dark or Lumi blue** | Pick a light or dark theme, and turn on **Lumi blue colors** for a look that matches the logo. |
| 🔁 **Auto-recovery** | Network dropped? Playback resumes and lyrics reload automatically once it's back. |
| 👤 **Your account, synced** | Sign in with Google to see your subscriptions, playlists, likes, Watch later and history. |

---

## 📸 Screenshots

<div align="center">

| ▶️ YouTube Lumi | 🎵 YT Music Lumi |
|:-:|:-:|
| <img src="docs/screenshots/youtube-beranda.jpg" width="250" alt="YouTube Lumi home"> | <img src="docs/screenshots/ytmusic-beranda.jpg" width="250" alt="YT Music Lumi home"> |
| **A home feed that feels like home.** Recommendations from your account, big thumbnails, category filters and **not a single ad**. | **Your music, ready to play.** Quick picks, moods and "Listen again" just like YT Music, in only **3 MB**. |

| 👥 Watch & Listen Together | 🎤 Lyrics in Picture-in-Picture |
|:-:|:-:|
| <img src="docs/screenshots/watch-together.jpg" width="250" alt="Watch Together"> | <img src="docs/screenshots/lirik-pip.jpg" width="250" alt="Floating lyrics on the home screen"> |
| **Distance is no longer an excuse.** Create a room, share the code, and watch or listen to the same thing **at the same second** from your own phones. | **Lyrics follow you everywhere.** Leave the app and the synced lyrics keep floating on your screen. **Not available in the official app.** |

</div>

---

## 📦 Two Apps

| | App | Package | Built for |
|:-:|---|---|---|
| ▶️ | **YouTube Lumi** | `com.lumitube.app` | Videos, Shorts, live streams, channels and subscriptions |
| 🎵 | **YT Music Lumi** | `com.lumitube.music` | Songs, albums, artists, playlists, lyrics and offline music |

Both apps can be installed side by side and work independently. You can also send a video from one app to the other.

---

## ✨ Features

### ▶️ YouTube Lumi

**🎬 Video player**
- YouTube-style controls: **double-tap** to skip ±10 seconds, **press and hold** either side for **2× speed**
- **Chapters** on the timeline and a **thumbnail preview** while scrubbing
- Quality, playback speed, loop, **subtitles with auto-translate**, and the original audio track
- **Picture-in-Picture** and background playback
- Full-screen mode with an optional **live chat** side panel, and the **video title** shown with the controls

**📱 Shorts**
- A full-screen vertical feed powered by **YouTube's own recommendations**
- The same controls as the video player: seek with preview, hold for 2×, captions and quality
- Comments, like, subscribe, share and download from inside the player
- Your place in the feed is kept when you open a channel and come back

**📺 Channels**
- Banner, avatar, @handle, subscriber and video counts, description and links
- Tabs: **Videos · Shorts · Live · Releases · Playlists · Posts** (only the ones the channel actually has)
- Sorting: **Latest · Popular · Oldest**
- An **About** page with clickable links, country, join date and total views
- Posts with **clickable links** and **full-screen, pinch-to-zoom images**
- Infinite scrolling with a clear *"no more content"* end marker

**🔎 Search**
- Filters: **All · Videos · Shorts** (Shorts show as a 3-column grid)
- Channel cards with **Subscribe** and **View channel** buttons
- Search suggestions and local search history
- Results stay in place after you watch a video

**📚 Library and account**
- Home feed with pull-to-refresh that keeps your scroll position
- Subscriptions, **history grouped by day** (All / Videos / Shorts), Liked videos and Watch later
- The **⋮ menu** on every video: Add to playlist · Download · Share · Listen in YT Music Lumi
- ⋮ menu on playlists: Download all · Share · Delete playlist
- A green **Members only** badge on videos available only to channel members
- Create playlists, reorder them by drag and drop, and delete your own (synced with YouTube; auto-generated playlists like Recap cannot be deleted)

**🔴 Live streams**
- Live badges, a jump-to-live button and **real-time live chat**
- **Download a stream while it's live**: record from the start of the live or from this second, then tap **Stop & save** at any time (or it saves automatically when the live ends)
- Live streams that can't be rewound: the red bar stays at the end and can't be dragged

### 🎵 YT Music Lumi

**🎤 Lyrics**
- **Synced word-by-word karaoke lyrics** from 11 providers: BetterLyrics, BetterLyrics QQ, YouLyPlus, LyricsPlus, Unison, SimpMusic, BiniLyrics, lrc.red, LRCLIB, KuGou and YouTube Music
- **🪟 Lyrics in Picture-in-Picture** while the app is minimized
- **Offline lyrics** for downloaded songs, with automatic backfill when you're back online
- Choose your preferred providers, or re-search lyrics for any song

**🎧 Listening**
- Search that mirrors YouTube Music: top result, Songs, Artists, Albums, Community playlists and Videos
- Radio queue, repeat one / repeat all
- **Auto-resume** after a network drop or an audio interruption
- **Sleep timer**

**📂 Library**
- Your playlists, albums and liked songs
- ⋮ menu on every song: Add to playlist · Download · Share
- ⋮ menu on playlists and albums (also in search results and in the top-right corner of album/playlist pages): save all their songs to your playlist · Download all · Share
- Drag-to-reorder, remove songs and delete your own playlists (auto-generated ones like Recap cannot be deleted, just like in YT Music)
- **Download a whole playlist** in one tap (saved as audio)
- Download options list **audio** first, then video

### 🧩 Shared by both apps

- 📥 A **Downloads** page with auto-collections (Songs / Videos) and downloaded playlists
- 🔔 A single, tidy progress notification for each batch download
- 👥 **Watch Together** (YouTube Lumi) and **Listen Together** (YT Music Lumi): create a room, share the 6-letter code, and everyone plays the same video or song in sync. By default only the room creator picks, with an option to let members pick too
- 🔗 Open YouTube / YouTube Music links directly in Lumi
- 🛡️ A built-in **crash reporter** with a one-tap *"Copy report"* button
- 🔒 **Members-only** videos and Shorts are hidden from Home
- 🌗 **Light/dark** themes (default: YouTube Lumi light, YT Music Lumi dark) and **Lumi blue colors** (on by default, can be turned off)
- 📶 A small **"You're offline"** pop-up when the internet drops, also in PiP mode
- ⏹️ A **Close** button in the player notification (the PiP window closes too)
- ⏸️ **Stop** a playlist download at any time from the app or the notification; finished items are kept
- ⚙️ **Settings on a single screen**, no scrolling

---

## ⬇️ Download & Install

1. Go to the **[Releases](https://github.com/vickyvam72/LumiTube/releases/latest)** page.
2. Under **Assets**, download one or both apps:
   - `YouTubeLumi-v2.2.apk` for YouTube
   - `YTMusicLumi-v2.2.apk` for YouTube Music
3. Open the downloaded file. If Android asks, allow **"Install unknown apps"** for your browser or file manager.
4. Tap **Install**. Done! 🎉

> 💡 **Updating:** Just install the new APK over the old one. Your login, downloads and settings are kept.

### 📋 Requirements
- Android **7.0 (API 24)** or newer
- About **10 MB** of free space per app (plus space for your downloads)
- An internet connection (except for downloaded content)

---

## 📖 User Guide

> 🗣️ The app is in **Indonesian** only. Button names below are English translations of the Indonesian labels.

### 0️⃣ First launch
On first launch LumiTube shows two short screens, each fitting on a single screen without scrolling:
1. **Welcome**: tap **Connect Google account** to sign in, or **Skip** for now.
2. **One more step**: allow **notifications** and **background running** (required). There are also two optional extras: **Autostart** for Xiaomi, Oppo, Vivo, Realme and Infinix phones, and **link setup** so YouTube links open in Lumi.

After that you land straight on Home.

### 1️⃣ Sign in (optional but recommended)
If you skipped it, open the **You** tab → **Sign in with Google**, then sign in on Google's own sign-in page.
After signing in you get your subscriptions, playlists, likes, Watch later, history and personalized Shorts.
Your session is stored **only on your device**.

### 2️⃣ Open YouTube links in Lumi
- **YouTube Lumi:** **You** tab → **Settings** → turn on **"Open YouTube links in this app"**
- **YT Music Lumi:** **You** tab → **Settings** → turn on **"Open YouTube Music links in this app"**

Then follow the 2-step on-screen guide: first release links from the official YouTube app, then connect them to Lumi. The **Reset links** button opens the same guide at any time.

### 🎨 Change appearance
Open **Settings**:
- **Theme**: Light or Dark.
- **Lumi blue colors**: a mostly blue look like the Lumi logo, including the progress bar and Shorts. On by default.

### 3️⃣ Turn on lyrics Picture-in-Picture 🎤🪟
YT Music Lumi → **Settings** → turn on **"Lyrics PiP when minimized"**.
Play a song and press Home: the lyrics keep running in a floating window.
Tip: turn on **"Close the app when PiP is closed"** if you want music to stop when you dismiss the window.

### 4️⃣ Player gestures
| Gesture | Action |
|---|---|
| Double-tap left / right | Rewind / forward 10 s |
| Press and hold left / right | Play at 2× speed while holding |
| Drag the timeline | Seek with a thumbnail preview |
| Swipe up / down (Shorts) | Next / previous Short |
| Swipe a panel down | Close comments, settings or queue |

### 5️⃣ Download for offline
- **Single item:** tap **⋮** → **Download** and pick a quality.
- **Whole playlist:** open the playlist → **Download all**, or tap **⋮** on a playlist/album (YT Music Lumi) → **Download**.
- **A stream that's live right now:** tap **Download** on the video page, choose **From the start of the live** or **Start from now**, then pick a quality. Recording stops by itself when the live ends, or tap **Stop & save** in the notification or on the Downloads page.
- Find everything under **Downloads**. Music downloads include lyrics.

### 6️⃣ Save to a playlist
Tap **⋮** → **Add to playlist** → choose **Watch later**, an existing playlist, or create a new one.

### 7️⃣ Watch Together / Listen Together 👥
1. Tap the **group** icon in the Home top bar (next to the download button).
2. Tap **Create room** to become the host, then **Share** to send the 6-letter code to your friends.
3. Your friends open the same icon, enter the code and tap **Join**.
4. Everything the host does (changing the video or song, play, pause, seek) is followed by the guests instantly. The icon turns red while you are in a room.
5. By default only the host picks the video or song. The host can turn on **"Members can also pick"** so friends in the room can pick, play and pause too.

Each phone streams from YouTube on its own, so guests don't need to sign in. Commands between phones are encrypted and relayed through [ntfy.sh](https://ntfy.sh).

### 8️⃣ Sleep timer ⏰
In the player, open the **⋮** menu (YT Music Lumi) or the ⚙️ settings (YouTube Lumi) → **Sleep timer** → pick a duration, or choose to stop when the current song or video ends.

---

## ❓ FAQ & Troubleshooting

<details>
<summary><b>A video won't play or keeps buffering</b></summary>

Tap the play button again. LumiTube automatically retries with other playback methods. If your network dropped, playback resumes once you're back online.
</details>

<details>
<summary><b>"Sign in to confirm you're not a bot"</b></summary>

YouTube shows this when one network makes a lot of requests. Signing in usually fixes it. Switching between Wi-Fi and mobile data also helps.
</details>

<details>
<summary><b>Lyrics aren't found for a song</b></summary>

Go to **Settings** → **Search lyrics for this song again**, or turn on more providers under **Lyrics providers**. Lyrics for very new or niche songs may not exist yet.
</details>

<details>
<summary><b>Will my searches show up in the official YouTube app?</b></summary>

Yes, if you are signed in. Searches you type in YouTube Lumi and YT Music Lumi are saved to your account's search history, just like the official apps, so they also show up in the official YouTube and YouTube Music apps. Automatic searches made by the app itself (for example to fill the Shorts feed) are not saved.
</details>

<details>
<summary><b>The app crashed. How do I report it?</b></summary>

On the next launch LumiTube shows the crash details. Tap **Copy report** and send it along with your issue.
</details>

<details>
<summary><b>Can I use the official apps alongside LumiTube?</b></summary>

Yes. LumiTube uses different package names, so all four apps can be installed at the same time.
</details>

---

## 🔒 Privacy

- ✅ **No** ads, analytics or third-party trackers
- ✅ Your Google session stays **on your device**
- ℹ️ Searches you type are saved to your account's search history, like the official apps
- ✅ Watch / Listen Together commands are encrypted; only people with the room code can read them
- ℹ️ Watched videos are recorded to your watch history, like the official apps

---

## 🛠️ Tech Stack

| Area | Technology |
|---|---|
| Language | **Kotlin** |
| UI | **Jetpack Compose**, Material 3 |
| Playback | **AndroidX Media3** — ExoPlayer and MediaSessionService |
| Networking | OkHttp, with chunked streaming and automatic retry |
| Images | Coil |
| Data | YouTube InnerTube API (WEB, MWEB and WEB_REMIX clients) |
| CI/CD | GitHub Actions — every push builds and publishes both APKs |

### 📁 Project structure
```
app/src/main/java/com/lumitube/app/
├── data/        # InnerTube client, parsers, repository, downloader, lyrics providers
├── player/      # PlaybackManager, PlaybackService, chunked data source
└── ui/
    ├── common/  # Shared components: login, playlists, sleep timer, live chat
    ├── youtube/ # YouTube Lumi screens: home, watch, Shorts, channel
    └── music/   # YT Music Lumi screens: player, lyrics, library
```

### 🏗️ Building from source
Both apps come from a single module, using two product flavors:

| Flavor | App |
|---|---|
| `youtube` | YouTube Lumi |
| `music` | YT Music Lumi |

```bash
git clone https://github.com/vickyvam72/LumiTube.git
cd LumiTube
gradle :app:assembleYoutubeRelease :app:assembleMusicRelease   # Gradle 8.11.1, JDK 17
```
The APKs are written to `app/build/outputs/apk/<flavor>/release/`.

> 🔐 Release builds are signed with a private key stored in **GitHub Actions Secrets** (`LUMI_KEYSTORE_BASE64`, `LUMI_KEYSTORE_PASSWORD`). The key is never committed to the repository. For a local build, place your own keystore at `keystore/lumitube.jks` and set `LUMI_KEYSTORE_PASSWORD`.

---

## 🙏 Acknowledgements

LumiTube stands on the shoulders of these amazing open-source projects and services. A huge **thank you** to their authors and contributors! 💖

### 🧩 Code, techniques and ideas
| Project | What LumiTube learned from it |
|---|---|
| [**ArchiveTune**](https://github.com/rukamori/ArchiveTune) by rukamori | Lyrics provider order, BetterLyrics / YouLy+ endpoints and nested-JSON lyrics parsing |
| [**BitChord**](https://github.com/kushagrasinghx/BitChord) by kushagrasinghx | Lyrics format detection (TTML / word-synced LRC) and the Apple Music–style "sweeping" lyric line |
| [**Metrolist**](https://github.com/MetrolistGroup/Metrolist) & [**InnerTubeX**](https://github.com/MetrolistGroup/innertubex) by MetrolistGroup | InnerTube client order and the player-cipher approach |
| [**zemer-cipher**](https://github.com/ZemerTeam/zemer-cipher) by ZemerTeam | Always-up-to-date player cipher configs (fetched at runtime) |
| [**NewPipe**](https://github.com/TeamNewPipe/NewPipe) by TeamNewPipe | Search suggestions, buffering strategy and the WebView JavaScript runner idea |
| [**yt-dlp**](https://github.com/yt-dlp/yt-dlp) | Marking videos as watched and player-version handling |
| [**ytmusicapi**](https://github.com/sigma67/ytmusicapi) by sigma67 | Playlist item reordering request format |
| [**Morphe**](https://github.com/MorpheApp/morphe-patches) by MorpheApp | Streaming client configuration |

### 🎤 Lyrics providers
[BetterLyrics](https://github.com/better-lyrics/better-lyrics) · [YouLy+ / LyricsPlus](https://github.com/ibratabian17/YouLyPlus) · [SimpMusic Lyrics](https://github.com/maxrave-dev/SimpMusic) · [LRCLIB](https://github.com/tranxuanthang/lrclib) · Unison · BiniLyrics · lrc.red · KuGou

### 🌐 Services
[ntfy](https://github.com/binwiederhier/ntfy) by binwiederhier: the public [ntfy.sh](https://ntfy.sh) pub/sub server that relays Watch / Listen Together commands

### 📚 Libraries
[Jetpack Compose](https://developer.android.com/jetpack/compose) · [AndroidX Media3](https://github.com/androidx/media) · [OkHttp](https://github.com/square/okhttp) · [Coil](https://github.com/coil-kt/coil) · [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)

> These projects are not affiliated with LumiTube. All credit for their work belongs to their respective authors.

---

## ⚖️ Disclaimer

LumiTube is an independent project for **personal and educational use**.
It is **not affiliated with, endorsed by, or sponsored by Google LLC, YouTube or YouTube Music**. All trademarks belong to their respective owners.
LumiTube relies on publicly accessible YouTube endpoints, which may change at any time. Use it at your own risk and in accordance with YouTube's Terms of Service.

## 📄 License

Copyright © 2026 **vickyvam72**.

LumiTube is free and open-source software, released under the **[GNU General Public License v3.0](LICENSE)**.
You may use, study, modify and redistribute LumiTube, as long as derivative works are also released under the same license (GPL-3.0) with their source code available.
LumiTube comes with **absolutely no warranty**. See the [LICENSE](LICENSE) file for the full text.

<div align="center">

---

Made with ❤️ in Indonesia · If you like LumiTube, leave a ⭐

</div>
