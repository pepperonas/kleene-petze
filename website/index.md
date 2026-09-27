<!--# block name="none" --><!--# endblock -->
# Kleene Petze — Keep Deleted WhatsApp Messages Readable

> Kleene Petze is a free, open-source (MIT) Android app for Android 8.0 and later that saves incoming message notifications permanently and encrypted on the device — like Android's notification history, but without the 24-hour expiry. Because WhatsApp posts no notification when a message is deleted, the copy captured on arrival survives: messages the sender deletes while they are still unread stay readable and are marked as deleted, and earlier versions of edited messages are kept too. It works for WhatsApp, WhatsApp Business, Signal, Telegram, Instagram, Messenger and Google Messages, or for every app. Messages are grouped into chats, searchable, and can be exported and imported as an encrypted file, JSON or CSV. The database is encrypted with SQLCipher (AES-256), an optional biometric lock protects the app, and nothing leaves the phone; the only network access is an update check that is off unless you switch it on. Distributed as a signed APK through GitHub Releases.

This is the Markdown version of https://kleene-petze.celox.io/ for agents and text tools. A short summary with every link lives at https://kleene-petze.celox.io/llms.txt.

## Download

- **Newest release:** https://kleene-petze.celox.io/download (picks the file for your platform; always the current release)
- **Current version:** <!--# include virtual="/ssi/version.txt" stub="none" --> · released <!--# include virtual="/ssi/date.txt" stub="none" -->
- **Release data as JSON:** https://kleene-petze.celox.io/latest.json
- **Requirements:** APK: Android 8.0+

Files in the current release:

<!--# include virtual="/ssi/files.md" stub="none" -->

## Features

- **Deleted stays readable** — WhatsApp posts no notification when a message is deleted, so the copy saved on arrival survives. It is marked *deleted* in the chat.
- **Edits uncovered** — When a message is edited, the earlier version stays in the archive, marked *edited*. The *Uncovered* view lists everything deleted or edited across all chats.
- **Encrypted, on your phone** — SQLCipher with AES-256, the key in the Android Keystore, backups disabled. An optional biometric lock also hides the app from the recent-apps preview.
- **Chats, search, pictures** — Messages are sorted into chats like in the messenger, with full-text search. The preview picture a notification carries can be kept too.
- **Export and import** — The whole archive as an encrypted file, JSON or CSV — and back. An import only adds: nothing is overwritten, the same file twice changes nothing.
- **Keeps listening** — Android silently disconnects notification listeners after reboots, updates and battery savers. Kleene Petze reconnects by itself and shows its status in Settings.

## Install

1. **Download the APK** — One file for every phone with Android 8.0 or later — there is nothing to pick.
2. **Allow the install** — Open the file. Android asks once whether your browser may install apps — allow it.
3. **Grant notification access** — The first screen takes you there. Also exempt the app from battery optimization — Samsung in particular stops background services otherwise.

## Verify

<!--# include virtual="/ssi/checksums.md" stub="none" -->
- Signing certificate SHA-256: `0560befbf32c64ede5a518a18bf6799c44f85b45853e97723914d4a84aafe379`
- Every release is signed with the same key. Compare with `apksigner verify --print-certs`.

## FAQ

**Is Kleene Petze free?** Yes. It is free and open source under the MIT licence, with no ads, no account and no tracking.

**Can it show every deleted message?** Only messages that arrived as a notification on this phone. If you had already read a message before it was deleted, or the chat is muted, there was no notification to keep.

**Does anything leave my phone?** No messages, ever. The only network access is the optional update check: switched on, it reads the newest version number from this page once a day — without any identifier. Switched off, the app makes no network request at all.

**Which phones does it run on?** Android 8.0 or later. On Samsung and other phones with aggressive battery savers, exempt the app from battery optimization so the system does not stop it.

**How do I update?** Switch on *Nach Updates suchen* in Settings: the app then looks once a day for a new release and notifies you. Download the APK from this page and install it over the existing app; your archive and settings stay.

**Is the file genuine?** Compare the SHA-256 checksum and the signing certificate shown on this page. Android also refuses to install an update signed with a different key over the app.

## Limits

- Android only; not on Google Play — distributed as an APK through GitHub Releases.
- Only messages that arrive as a notification on this phone are saved: muted chats and messages you receive while the chat is open often post none.
- A message deleted after you have already read it cannot be recovered — there is no notification left to keep.
- Voice messages, videos and original files are never part of a notification; pictures are kept only as the smaller preview.
- The user interface is German.
- It does not download or install updates by itself — it only tells you, and you install the new APK over the old one.

## Links

- Source code: https://github.com/pepperonas/kleene-petze
- Changelog: https://kleene-petze.celox.io/changelog.md
- Licence (MIT): https://github.com/pepperonas/kleene-petze/blob/main/LICENSE
- Support the project: https://www.paypal.com/donate/?business=martin.pfeffer@celox.io&currency_code=EUR&item_name=Kleene%20Petze
- Author: Martin Pfeffer, https://celox.io — Imprint https://celox.io/impressum/ · Privacy https://celox.io/datenschutz/
