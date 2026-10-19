# 🚀 TDownloader v1.3.0

This release removes every login, replaces the per-site tabs with one smart home screen, and adds TikTok, X (Twitter), Facebook, and WhatsApp Status downloads.

---

## ✨ What's New

### 🛠️ New Features
*   **No Login for Instagram:** Public reels, videos, photos, and carousels now download straight from the link — no account, session ID, or in-app browser needed. The Instagram login card and its "login required" notice are gone.
*   **TikTok Downloads:** Public TikTok videos and photo posts download with no login. Short links (`vm.tiktok.com`, `vt.tiktok.com`) are resolved automatically.
*   **X (Twitter) Downloads:** Public X posts download with no login — videos at the best quality, GIFs, and photos at their original quality, including posts with several items. `t.co` short links work too.
*   **Facebook Downloads:** Public Facebook videos and reels, including `facebook.com/share/r/…` links, download with no login as a plain MP4 that plays on any phone.
*   **WhatsApp Status Saver:** A new **Status** tab shows the statuses you've viewed in WhatsApp or WhatsApp Business. Swipe through full-screen previews, tap the big yellow **Download** button, or long-press to select several. Access is a one-time grant to just the Statuses folder — nothing leaves your phone.
*   **Post Previews & Slide Picker:** For Instagram, TikTok, and X, see the owner, caption, and thumbnails before downloading, and untick any photo or video you don't want from a multi-item post.
*   **Audio-Only for Instagram, TikTok, and X:** Choose **Audio** to save just the soundtrack.
*   **Update Check:** If an old version stops working (for example, a site changes), it can be retired: that version shows a full-screen **Update required** screen with a button to the latest release instead of failing in confusing ways. Newer releases can also show a dismissible **Update available** prompt.

### 🎨 UI & Experience Upgrades
*   **One Smart Home Screen:** The per-site tabs are gone. Paste or share any link and TDownloader recognizes the site and offers only the options that apply — Video or Audio, or quality chips for YouTube.
*   **Live Download Cards:** Downloads appear as live progress cards on the home screen instead of a blocking dialog, so you can start another link while one runs. Errors stay on the card until you dismiss them.
*   **Library with Search & Filters:** History is now a Library with search plus Video / Audio / Photos and per-site filters. The home screen shows your three most recent downloads.
*   **Supported Platforms Row:** A compact, icons-only row replaces the old tab strip and fits on one line.
*   **Simpler Share Sheet:** Sites with a single quality now offer just **Video** and **Audio** instead of resolutions that don't exist.
*   **Footer:** "Built with ❤️ by Talha Sajid" now links to the portfolio.

### 🐛 Bug Fixes
*   **Photos and audio opened as video:** Downloaded photos and audio now open in the right app from the "Saved" notification and from your Library, instead of always being treated as video.
*   **Misleading Instagram error:** A private or removed post now says so, instead of telling you to log in.

---

## 📲 How to Update

1.  Download the new `tdownloader-v1.3.0.apk` below.
2.  Open the file and tap **Update** (you do not need to uninstall the previous version).

---

# 🚀 TDownloader v1.2.0

This release focuses on fixing real download reliability issues, adding Instagram image/carousel support, and giving you visibility into what's happening while a download runs.

---

## ✨ What's New

### 🛠️ New Features
*   **Instagram Image & Carousel Downloads:** Multi-image Instagram posts (carousels) now download completely — all images and videos in the post, not just the first item. Works around a known yt-dlp limitation by falling back to a dedicated image downloader when needed.
*   **Live Download Progress Modal:** Starting a download now shows a circular progress indicator with a live percentage, instead of instantly resetting the input. Tap **"Do it in background"** anytime to dismiss and keep working — the download keeps running regardless.
*   **History Thumbnails:** Every entry in Download History now shows a real preview — a video frame, the actual image, or a fallback icon for audio.
*   **Deleted-File Detection:** If a downloaded file is removed outside the app (Files app, gallery, etc.), History now marks it as **Deleted** instead of silently pointing at a broken link.
*   **New Platform Tabs:** Added WhatsApp Status Saver and Facebook tabs (with real brand icons) — marked **Coming Soon** while support is built out.

### 🎨 UI & Experience Upgrades
*   **Platform Icons:** Instagram, YouTube, TikTok, WhatsApp, and Facebook tabs now use real brand icons instead of plain text.
*   **Paste Button Highlight:** The paste icon is now yellow to make it clearer it's tappable.
*   **Cleaner Instagram Tab:** The "login required" notice now hides automatically once you're logged in.
*   **Generic Progress Labels:** Download notifications now say "Downloading" instead of "Video"/"Audio", since a single download can contain a mix of both.

### 🐛 Bug Fixes
*   **Fixed downloads not appearing in Files/Gallery on the first try** — a race between yt-dlp's merge step and media scanning meant some downloads were invisible until you downloaded the same link twice. Fixed by resolving the real output file(s) after the whole process completes.
*   **Fixed silent "stuck" progress modal** — some failure paths never told the UI a download had finished, leaving the progress dialog spinning forever even though the download had already succeeded or failed.

---

## 📲 How to Update

1.  Download the new `tdownloader-v1.2.0.apk` below.
2.  Open the file and tap **Update** (you do not need to uninstall the previous version).
