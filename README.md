<p align="center">
  <img src="TDownloader-logo.png" width="180" alt="TDownloader Logo" />
</p>

# TDownloader

A fast, minimal Android app for downloading videos and images from Instagram, YouTube, TikTok, X, and Facebook — directly to your gallery. No ads, no accounts, no servers.

Paste or share a link from any app → TDownloader recognises the site → download. Done.

<br>

## Features

- **One smart home screen** — no tabs to pick: paste a link and TDownloader detects the site, shows a preview, and offers only the options that apply
- **Share sheet integration** — share any link from Instagram, YouTube, TikTok, X, or Facebook and TDownloader appears instantly
- **Carousel previews** — see every photo or video of an Instagram, TikTok, or X post before downloading and untick the ones you don't want
- **Quality and audio options** — YouTube offers Best, 1080p, 720p, 480p, or Audio only; other sites offer Video or Audio
- **Inline progress + notification** — every running download shows as a live card on the home screen (start another while one runs), plus a persistent notification with speed, MB downloaded, and ETA
- **No logins** — public Instagram, TikTok, X, and Facebook posts download straight from a link, no account or session ID needed
- **WhatsApp Status Saver** — a dedicated Status tab shows the statuses you've viewed in WhatsApp (or WhatsApp Business); preview them, select several, and save to your Library. One-time folder access, nothing leaves your phone
- **Library with search and filters** — real thumbnails for every past download, filter by Video / Audio / Photos or by site, and files removed outside the app are automatically marked "Deleted"
- **Auto-updating yt-dlp** — extractors update silently on every launch so platforms never break
- **Dark UI** — yellow-accented dark theme, edge-to-edge, with real platform icons

<br>

## Supported Platforms

| Platform | Public Videos | Private Content |
|---|---|---|
| YouTube | ✅ No login needed | — |
| Instagram | ✅ No login needed (videos, images, and carousels) | ❌ Not supported |
| TikTok | ✅ No login needed (videos and photo posts) | ❌ Not supported |
| X (Twitter) | ✅ No login needed (videos, GIFs, and photos at original quality) | ❌ Not supported |
| Facebook | ✅ No login needed (public videos and reels) | ❌ Not supported |
| WhatsApp Status Saver | ✅ Statuses you've viewed (photos and videos) | — |

<br>

## Screenshots

> Coming soon

<br>

## Download

Get the latest APK from the [Releases](https://github.com/IamTalha-Sajid/tdownloader/releases) page.

### Install
1. Download the latest `tdownloader-vX.Y.Z.apk`
2. On your phone: **Settings → Apps → Special app access → Install unknown apps** → allow your browser or Files app
3. Open the downloaded APK and tap **Install**

<br>

## WhatsApp Statuses

Statuses have no link to paste, so they live in their own **Status** tab:
1. Open WhatsApp and view the statuses you want to keep (WhatsApp only stores a status on your phone once you've seen it).
2. Open TDownloader → **Status** → **Allow access**. Android opens the Statuses folder for you — tap **Use this folder**. This is a one-time step and only shares that folder.
3. Tap a status to preview it, or long-press to select several, then **Download**. Saved statuses go to `Downloads/TDownload` and appear in your Library.

<br>

## Instagram

No login or setup. Paste (or share) a public Instagram link and TDownloader reads the post's media directly. Reels, videos, photos and multi-item carousels all work. Private accounts can't be downloaded.

<br>

## Retiring old versions

The app reads [`update.json`](update.json) from this repo's `main` branch each time it opens:

```json
{
  "latest": "1.3.0",
  "minimum": "1.3.0",
  "url": "https://github.com/IamTalha-Sajid/tdownloader/releases/latest",
  "message": "Optional text shown to users."
}
```

- Installed version **below `minimum`** → the app is replaced by a full-screen "Update required" screen and refuses all downloads (including from the share sheet).
- Installed version **below `latest`** (but not below `minimum`) → a dismissible "Update available" prompt.
- If the file is unreachable, malformed, or the versions aren't valid numbers, **nothing is blocked**. The last answer is cached, so a retired version stays blocked offline.
- Only versions that include this check (1.3.0 and newer) can be retired this way. Raise `minimum` when a site change breaks downloads in an old release, and edit the file on `main`.

<br>

## Tech Stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Downloader | [youtubedl-android](https://github.com/yausername/youtubedl-android) (yt-dlp + Python + FFmpeg bundled), with [gallery-dl](https://github.com/mikf/gallery-dl) as a fallback for Instagram image/carousel posts |
| Min SDK | Android 8.0 (API 26) |
| Async | Kotlin Coroutines |
| Storage | DataStore (download history) |

<br>

## Building from Source

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 11+
- Android SDK with API 35

### Steps

```bash
git clone https://github.com/IamTalha-Sajid/tdownloader.git
cd tdownloader
```

Open the project in Android Studio → let Gradle sync → hit **Run**.

To build a release APK:
```
Build → Generate Signed Bundle / APK → APK → release
```

<br>

## How It Works

TDownloader bundles Python, yt-dlp, gallery-dl, and FFmpeg inside the APK — no external dependencies, no internet servers, everything runs on-device.

When you share a link:
1. `ShareActivity` (translucent) extracts the URL and shows a quality picker
2. `DownloadService` (foreground service) runs yt-dlp with your chosen format. Instagram, TikTok, and X links are resolved without a login by reading the post's public page data and downloading the media directly (audio-only extracts the soundtrack with yt-dlp's bundled ffmpeg). If a post can't be resolved that way, it falls back to yt-dlp, then gallery-dl for mixed image/video Instagram carousels. YouTube and Facebook go straight to yt-dlp
3. Progress is streamed to both a persistent notification and an in-app progress modal, with speed + ETA
4. On completion, every produced file is registered with MediaStore so it appears in your gallery instantly
5. A tappable notification opens the file directly

<br>

## Why ~200 MB APK?

The APK bundles a full Python runtime + FFmpeg + gallery-dl so yt-dlp works with zero setup on the phone, across every supported ABI. Breakdown:

| Component | Size |
|---|---|
| Python runtime | ~80 MB |
| FFmpeg | ~60 MB |
| yt-dlp + gallery-dl + app code | ~25 MB |
| Native libs (arm, arm64, x86, x86_64) | ~40 MB |

This is a one-time download. yt-dlp updates happen over the network at runtime (small delta, not a full APK update).

<br>

## License

This project is licensed under the **GPL-3.0 License** — inherited from [youtubedl-android](https://github.com/yausername/youtubedl-android).

See [LICENSE](LICENSE) for details.

<br>

## Disclaimer

This app is for **personal use only**. Downloading copyrighted content may violate the terms of service of the respective platforms. The developer is not responsible for misuse.

<br>

---

<p align="center">Built with ❤️ by <a href="https://github.com/IamTalha-Sajid">Talha Sajid</a></p>
