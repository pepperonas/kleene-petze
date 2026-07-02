# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Kleene Petze (display name; package/`applicationId` stay `io.celox.notifvault`, including the `NotifVaultApp`
/ `NotifVaultTheme` internal names — renaming them would break update-installs, signing and stored data) is
an Android app that permanently and encryptedly archives incoming messaging notifications — like Samsung's
notification history but without the 24h expiry. Its core trick:
WhatsApp sends **no** notification when a message is deleted, so the original notification (already captured
on arrival) survives deletion. Everything is on-device; the app has **no `INTERNET` permission**, no cloud,
no tracking, and backups are disabled (`allowBackup="false"`).

Single Gradle module (`:app`), Kotlin + Jetpack Compose, minSdk 26 / target+compile 35, JDK 17.

## Build & run

```bash
./gradlew assembleDebug        # APK → app/build/outputs/apk/debug/
./gradlew installDebug         # build + install to connected device/emulator
./gradlew lint                 # Android lint
./gradlew testDebugUnitTest    # JVM unit tests (MessageId, Deletion, ExportUtils, Format, SearchUtils)
./gradlew testDebugUnitTest --tests "io.celox.notifvault.notif.MessageIdTest"   # single test class
```

There is **no `local.properties`** committed — Android Studio creates it, or copy `local.properties.example`
and set `sdk.dir`. The repo is `github.com/pepperonas/kleene-petze` (public).

## Release

Releases are cut by tag: bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`, commit and
push, then `git tag vX.Y.Z && git push origin vX.Y.Z` — `.github/workflows/release.yml` builds the signed
APK and publishes it as a GitHub Release asset (`kleene-petze-vX.Y.Z.apk`). Verify the run succeeded
(`gh run watch`/`gh run list`); a "job was not acquired by Runner" failure is a GitHub infra flake →
`gh run rerun`. Local `assembleRelease` signs via the gitignored `keystore.properties` + `release.jks`
(secrets live only in the private `pepperonas/keystore` repo and the Actions secrets — never commit
`*.jks`/`keystore.properties`/`local.properties`; check `git status` before pushing).

## Architecture (data flow)

The whole app is one pipeline: a system notification → a stored, encrypted row → Compose UI reads it back.

1. **`service/NotificationCaptureService`** — a `NotificationListenerService` that receives *every* posted
   notification. Filters by `SettingsStore` (capture-all toggle, else monitored package allowlist), hands
   the `StatusBarNotification` to `MessageExtractor`, and inserts the results. Runs work on a private IO
   `CoroutineScope`. On `onListenerConnected` it snapshots the current shade; on disconnect it calls
   `requestRebind` because Samsung One UI aggressively kills listeners. It applies the extractor's
   `messages` (insert) and `deletions` (`dao.markDeleted`).

2. **`notif/MessageExtractor`** — `extract()` returns an **`ExtractResult(messages, deletions)`**. Skips
   `FLAG_GROUP_SUMMARY`. **Prefers `NotificationCompat.MessagingStyle`** (gives per-message sender + real
   timestamp + bundled back-history); falls back to title/`EXTRA_TEXT_LINES`/`EXTRA_TEXT`. **Derives a stable
   `conversationKey`** for grouping — `notification.shortcutId` → `sbn.tag` → display title — because the title
   (`conversationTitle`) is null for most 1:1 WhatsApp chats and sometimes missing for groups; grouping by
   title mixed distinct chats and split groups per-sender. The title is display-only. **Deletion detection:**
   when a still-unread message is deleted, WhatsApp re-posts the notification with the text replaced by a
   placeholder (`notif/Deletion.isDeletionPlaceholder`, unit-tested) while keeping the original sender +
   timestamp; the extractor emits a `DeletionMark(conversationKey, sender, messageTime)` instead of storing
   it, so the already-stored original is flagged (`deletionSuspected`). Messages deleted *after* being read
   produce no notification → undetectable (hard platform limit).

3. **De-duplication is the key invariant.** `CapturedMessage.id` is a SHA-256 of
   `"$pkg|$conversationKey|$sender|$text|$messageTime"` — computed by `messageContentId(...)` in
   `notif/MessageId.kt` (extracted as a framework-free, unit-tested seam; see `MessageIdTest`, which pins the
   exact field order/separator with a fixed hash). Inserts use `OnConflictStrategy.IGNORE`, so a message
   re-delivered inside many successive notifications collapses to exactly one row. Don't change the hash
   inputs or conflict strategy — it silently re-duplicates the vault.

4. **`data/`** — Room (`AppDatabase` **v2**, single `messages` table) over **SQLCipher**. `DatabaseProvider`
   is a singleton that loads the native `sqlcipher` lib, builds the DB with a 32-byte random passphrase stored
   in `EncryptedSharedPreferences` (AES-256-GCM, Android Keystore). Uses `fallbackToDestructiveMigration()`
   with **no registered migration on purpose** — v2 added `conversationKey`, and since the old rows were
   grouped by the unreliable title we took a clean slate. If you bump the schema again and want to keep data,
   add a real `Migration`. `MessageDao` groups/filters by **`conversationKey`** (the overview's bare columns
   resolve to the `MAX(messageTime)` row → latest title + last message; `SUM(deletionSuspected)` →
   `deletedCount` per chat). `markDeleted(key, sender, time)` flags a stored original when a deletion
   placeholder arrives. `SettingsStore` (DataStore) holds the
   monitored-package set, capture-all flag, and biometric-lock flag; `KNOWN_MESSENGERS` is the Settings toggle
   list, `DEFAULT_PACKAGES` the WhatsApp default.

5. **`ui/`** — Compose. `MainActivity` is a **`FragmentActivity`** (required by `BiometricPrompt`); it gates
   the app behind biometric/device-credential unlock when enabled (and **re-locks on `ON_STOP`**), then a
   `NavHost` routes onboarding → home → chat → settings. Nav args are the `URLEncoder`-escaped
   **`conversationKey` + package** (not the title; the chat screen derives the title from its latest message).
   `ConversationScreen` renders a chat archive (date separators, sender-run grouping, per-sender colors via
   `Format.identityColor`); `HomeScreen` shows colored `Avatar`s + search with match highlighting; destructive
   actions (delete chat / clear all) require confirmation dialogs. `VaultViewModel` (`AndroidViewModel`) owns
   the DAO Flows as `StateFlow`s and the **debounced** search query. **Motion:** `theme/Motion.kt` is a small
   spring-physics system (M3-Expressive-style tokens — `spatial` may overshoot for position/size/shape,
   `effects` is high-damping for color/alpha; the public `MaterialExpressiveTheme`/`MotionScheme` only ship on
   material3 1.5.0-alpha, so we stay on stable 1.3.x and roll our own). Use these specs, not fixed `tween`s,
   for custom animations: `Components.clickableScale` (spring press feedback on rows), `LazyColumn` item
   spring placement via `Modifier.animateItem(...)`, and the Settings per-app list / Onboarding cards reveal.
   Shared bits: `Components.kt` (`Avatar`, `clickableScale`),
   `Format.kt` (date/time, `identityColor`, `initials`). Theme in `ui/theme/`.

6. **`util/`** — `PermissionUtils` (notification-access check via `enabled_notification_listeners`, battery-
   optimization exemption), `ExportUtils` (hand-rolled CSV/JSON serialization), and `SearchUtils.escapeLike`
   (escapes LIKE `%`/`_`, paired with `ESCAPE '\'` in `MessageDao.search`; unit-tested).

`NotifVaultApp` (Application) warms up `DatabaseProvider` at startup so the listener can write immediately.

## Things to keep in mind

- **Media can't be captured** — photos/voice/video aren't in the notification payload and Scoped Storage
  blocks WhatsApp's media folder. Don't attempt to add media capture; it's a documented hard limitation.
- **Don't add network permissions or dependencies.** The privacy guarantee (offline-only) is a feature.
- UI strings and user-facing text are **German** (`res/values/strings.xml`); match that.
- Release builds currently have `isMinifyEnabled = false`; if enabling R8, SQLCipher/Room may need
  `proguard-rules.pro` keep rules.
