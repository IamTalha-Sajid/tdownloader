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
