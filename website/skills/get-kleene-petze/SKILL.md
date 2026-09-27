---
name: get-kleene-petze
description: Download, verify and install the newest Kleene Petze APK — the free, open-source Android app that keeps message notifications in an encrypted on-device archive so deleted WhatsApp messages stay readable. Use when someone asks for the app, its latest version, a download link, or how to check the APK is genuine.
license: MIT
---

# Get Kleene Petze

Kleene Petze is a free, open-source (MIT) Android app for Android 8.0 and later that saves incoming message notifications permanently and encrypted on the device — like Android's notification history, but without the 24-hour expiry. Because WhatsApp posts no notification when a message is deleted, the copy captured on arrival survives: messages the sender deletes while they are still unread stay readable and are marked as deleted, and earlier versions of edited messages are kept too. It works for WhatsApp, WhatsApp Business, Signal, Telegram, Instagram, Messenger and Google Messages, or for every app. Messages are grouped into chats, searchable, and can be exported and imported as an encrypted file, JSON or CSV. The database is encrypted with SQLCipher (AES-256), an optional biometric lock protects the app, and nothing leaves the phone; the only network access is an update check that is off unless you switch it on. Distributed as a signed APK through GitHub Releases.

## 1. Find the newest release

`GET https://kleene-petze.celox.io/latest.json` returns `version`, `published`, `notes` and `assets[]`, each with `target`,
`name`, `url`, `size` (bytes) and `sha256`. It is refreshed from GitHub Releases every 15 minutes. On the
page itself, browsers with WebMCP expose the same data as the tools `get_latest_release`,
`get_download_url` and `get_checksums`.

## 2. Download

- Stable link, always the newest file for the visitor's platform: <https://kleene-petze.celox.io/download>
- APK: <https://kleene-petze.celox.io/download/android> — Android 8.0+

## 3. Verify

- The file's SHA-256 must equal the matching `assets[].sha256` in `latest.json`.
- Signing certificate SHA-256: `0560befbf32c64ede5a518a18bf6799c44f85b45853e97723914d4a84aafe379`
- Every release is signed with the same key. Compare with `apksigner verify --print-certs`.

## 4. Install

1. One file for every phone with Android 8.0 or later — there is nothing to pick.
2. Open the file. Android asks once whether your browser may install apps — allow it.
3. The first screen takes you there. Also exempt the app from battery optimization — Samsung in particular stops background services otherwise.

## Limits

- Android only; not on Google Play — distributed as an APK through GitHub Releases.
- Only messages that arrive as a notification on this phone are saved: muted chats and messages you receive while the chat is open often post none.
- A message deleted after you have already read it cannot be recovered — there is no notification left to keep.
- Voice messages, videos and original files are never part of a notification; pictures are kept only as the smaller preview.
- The interface is available in English and German only.
- It does not download or install updates by itself — it only tells you, and you install the new APK over the old one.

More: [product page](https://kleene-petze.celox.io/) · [Markdown version](https://kleene-petze.celox.io/index.md) · [changelog](https://kleene-petze.celox.io/changelog.md) · [source](https://github.com/pepperonas/kleene-petze)
