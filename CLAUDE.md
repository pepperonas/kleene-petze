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
./gradlew testDebugUnitTest    # 122 JVM unit tests (MessageId, Grouping, Deletion, Noise, ExportUtils, ExportNaming, VaultCodec, VaultBackup, BackupMerge, RetentionPolicy, Format, SearchUtils)
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
   notification. Filters by `SettingsStore` (capture-all toggle, else monitored package allowlist; both
   cached in-memory via `stateIn`, not re-collected per notification), hands the `StatusBarNotification`
   to `MessageExtractor`, and inserts the results. Work runs through a **single-consumer `Channel` queue**
   (strictly in post order — a deletion placeholder must never be applied before the insert of the
   original it flags; parallel per-notification coroutines could interleave at suspension points). On
   `onListenerConnected` it snapshots the current shade; on disconnect it calls `requestRebind` because
   Samsung One UI aggressively kills listeners. It applies the extractor's `messages` (insert) and
   `deletions` (`dao.markDeleted`). **Edit detection:** for every row `insertAll` *actually* inserted
   (rowId != −1) it calls `dao.markEditSuperseded(...)` — an edit re-arrives with the same
   conversationKey+sender+messageTime but new text (new content hash → new row), so older siblings get
   flagged `editSuperseded`; a brand-new message matches nothing. A deletion placeholder after an edit
   flags *all* stored versions (intended). **Health:** the companion `listenerConnected` StateFlow feeds
   the Home banner and the Settings status section (UI runs in the same process), and a throttled
   heartbeat (≤ 1 write/min) persists `SettingsStore.lastCaptureAt`. `onCreate` also kicks
   `RetentionPruner.pruneIfDue` (the service runs even when the UI never opens).

2. **`notif/MessageExtractor`** — `extract()` returns an **`ExtractResult(messages, deletions)`**. Skips
   `FLAG_GROUP_SUMMARY` and **non-message notifications** (see noise filter below). **Prefers
   `NotificationCompat.MessagingStyle`** (gives per-message sender + real
   timestamp + bundled back-history); falls back to title/`EXTRA_TEXT_LINES`/`EXTRA_TEXT`. **Derives a stable
   `conversationKey`** for grouping — `notification.shortcutId` → `sbn.tag` → (groups: notification slot) →
   display title — because the title
   (`conversationTitle`) is null for most 1:1 WhatsApp chats and sometimes missing for groups; grouping by
   title mixed distinct chats and split groups per-sender. The title is display-only. Those resolution rules
   live in **`notif/Grouping.kt`** (`stableKeyOf`/`senderNameOf`/`displayTitleOf`/`conversationKeyOf`/
   `slotKeyOf`/`chatIdentityOf`) —
   framework-free so `GroupingTest` can pin them without a `StatusBarNotification`; the extractor only wires
   the notification fields into them. **Group chats without shortcutId/tag (v1.6.2 fix):** `chatIdentityOf`
   resolves the MessagingStyle path's key+title together and **never falls back to the sender for a group**.
   WhatsApp omits `conversationTitle` on some posts/versions; the old title fallback then yielded the *sender*,
   which split the group per sender **and merged those parts into the 1:1 chats with the same people** — so
   affected groups never appeared as their own chat (this is why *some* groups looked untracked). The group
   name is no fallback either (present in one post, missing in the next → the chat would split), so the key
   is `slotKeyOf(pkg, sbn.id)` — the notification slot the messenger re-uses for that chat, exactly as stable
   as the in-place-update mechanism the whole app already relies on. 1:1 chats keep the legacy sender
   fallback (there the sender *is* the chat, and existing vaults stay grouped as they are). Group titles
   resolve `conversationTitle` → `EXTRA_TITLE` → app label.
   **Noise filter (`notif/Noise.kt`, `NoiseTest`):** two independent, deliberately conservative filters —
   structural `isNonMessageNotification(ongoing, foregroundService, category)` drops `FLAG_ONGOING_EVENT` /
   `FLAG_FOREGROUND_SERVICE` posts and the `service`/`progress`/`transport`/`call`/`navigation`/`sys`
   categories (language-independent; WhatsApp's permanent "Überprüfe auf neue Nachrichten" is a
   foreground-service notification, so the platform flags it), and textual `isServiceNoiseText` matches the
   concrete service phrases (`SERVICE_NOISE_MARKERS`, 14 languages + backup/restore progress) as a safety net
   for builds that set neither. A false positive silently drops a real message → keep phrases distinctive.
   **Deletion detection:**
   when a still-unread message is deleted, WhatsApp re-posts the notification with the text replaced by a
   placeholder (`notif/Deletion.isDeletionPlaceholder`, unit-tested) while keeping the original sender +
   timestamp; the extractor emits a `DeletionMark(conversationKey, sender, messageTime)` instead of storing
   it, so the already-stored original is flagged (`deletionSuspected`). This works **only in the
   MessagingStyle path** (the placeholder keeps the original's timestamp, so the stored row can be
   matched); in the title/text fallback only the new post's time is known, which never matches — there
   placeholders are simply skipped. Messages deleted *after* being read produce no notification →
   undetectable (hard platform limit).

3. **De-duplication is the key invariant.** `CapturedMessage.id` is a SHA-256 of
   `"$pkg|$conversationKey|$sender|$text|$messageTime"` — computed by `messageContentId(...)` in
   `notif/MessageId.kt` (extracted as a framework-free, unit-tested seam; see `MessageIdTest`, which pins the
   exact field order/separator with a fixed hash). Inserts use `OnConflictStrategy.IGNORE`, so a message
   re-delivered inside many successive notifications collapses to exactly one row. Don't change the hash
   inputs or conflict strategy — it silently re-duplicates the vault.

4. **`data/`** — Room (`AppDatabase` **v4**, single `messages` table) over **SQLCipher**. `DatabaseProvider`
   is a singleton that loads the native `sqlcipher` lib, builds the DB with a 32-byte random passphrase stored
   in `EncryptedSharedPreferences` (AES-256-GCM, Android Keystore). **Destructive migration only from v1**
   (`fallbackToDestructiveMigrationFrom(1)` — intentional clean slate, old rows were grouped by the
   unreliable title); every later bump needs a real `Migration` (v2→3: `MIGRATION_2_3` swaps the index set
   for a composite `conversationKey+packageName+messageTime`; v3→4: `MIGRATION_3_4` adds the
   `editSuperseded` column). `exportSchema = true` writes the expected
   schema JSON to `app/schemas/` — hand-written migration DDL must match it exactly (verify index names
   there). `MessageDao` groups/filters by **`conversationKey`** (the overview's bare columns
   resolve to the `MAX(messageTime)` row → latest title + last message; `SUM(deletionSuspected)` /
   `SUM(editSuperseded)` → `deletedCount`/`editedCount` per chat). `markDeleted(key, sender, time)` flags a
   stored original when a deletion placeholder arrives; `markEditSuperseded(key, pkg, sender, time, newId)`
   flags older versions of an edited message; `flagged()` feeds the global "Aufgedeckt" view;
   `pruneOlderThan` + `RetentionPruner` implement the optional retention policy (default off, throttled to
   one run per day via `lastPruneAt`); `applyDeletedFlags`/`applyEditedFlags` merge flags on backup restore.
   The two decisions that are pure logic live next to them as testable seams: **`RetentionPolicy`**
   (`isDue`/`cutoff`/`OPTIONS` — a `lastPruneAt` in the *future*, i.e. the clock moved back, also counts as
   due so pruning can't stall) and **`BackupMerge.plan(messages, rowIds)`** (which rows were genuinely
   imported vs. which already-present rows need their deleted/edited flags re-applied; a short rowId list
   degrades to "skipped" instead of crashing a restore).
   **`NoiseCleanup.runOnce`** purges rows captured *before* the noise filter existed (`idsAndTexts()` +
   `deleteByIds` chunked, matched in Kotlin because SQLite's `LIKE`/`LOWER` are ASCII-only and would trip
   over the umlauts); it claims its `SettingsStore` flag before deleting so it can never become a full scan
   on every start. `SettingsStore` (DataStore) holds the monitored-package set, capture-all flag,
   biometric-lock flag,
   `lastCaptureAt` heartbeat, `retentionDays` (0 = forever), `lastPruneAt` and `noiseCleanupDone`;
   `KNOWN_MESSENGERS` is the Settings toggle list, `DEFAULT_PACKAGES` the WhatsApp default.

5. **`ui/`** — Compose. `MainActivity` is a **`FragmentActivity`** (required by `BiometricPrompt`); it gates
   the app behind biometric/device-credential unlock when enabled (and **re-locks on `ON_STOP`**), then a
   `NavHost` routes onboarding → home → chat / flagged → settings. Nav args are the **`conversationKey` +
   package** (not the title; the chat screen derives the title from its latest message), encoded **exactly
   once** with `Uri.encode` — Navigation Uri-decodes route args itself, so there is deliberately no manual
   decode (a second `URLDecoder` pass corrupted keys containing `+` and crashed on `%`).
   `ConversationScreen` renders a chat archive (date separators, sender-run grouping, per-sender colors via
   `Format.identityColor`; deleted bubbles in `errorContainer`, edit-superseded ones in `tertiaryContainer`;
   **long-press** a bubble → copy [sensitive-flagged clipboard on API 33+] / share / details incl.
   `capturedAt`; ⋮ menu exports just this chat). `HomeScreen` shows colored `Avatar`s + search with match
   highlighting, a **capture-health banner** (access revoked → error + button; access ok but listener
   unbound → quiet hint), **per-app `FilterChip`s** (only when ≥ 2 apps have chats) and 🗑/✏️ badges.
   `FlaggedScreen` ("Aufgedeckt") lists all deleted/edited originals globally. `SettingsScreen` adds a
   Status section (access / listener / last capture), the retention picker, and **encrypted backup/restore**
   via SAF (`CreateDocument`/`OpenDocument` + passphrase dialogs; results in a dialog). Destructive
   actions (delete chat / clear all / retention) require confirmation. `VaultViewModel` (`AndroidViewModel`)
   owns the DAO Flows as `StateFlow`s, the **debounced** search query, and `importBackup` (insert-IGNORE
   merge, plan via `BackupMerge`, flag re-apply chunked at `BackupMerge.FLAG_CHUNK` = 500 ids per UPDATE). **Motion:** `theme/Motion.kt` is a small
   spring-physics system (M3-Expressive-style tokens — `spatial` may overshoot for position/size/shape,
   `effects` is high-damping for color/alpha; the public `MaterialExpressiveTheme`/`MotionScheme` only ship on
   material3 1.5.0-alpha, so we stay on stable 1.3.x and roll our own). Use these specs, not fixed `tween`s,
   for custom animations: `Components.clickableScale` (spring press feedback on rows), `LazyColumn` item
   spring placement via `Modifier.animateItem(...)`, and the Settings per-app list / Onboarding cards reveal.
   Shared bits: `Components.kt` (`Avatar`, `clickableScale`),
   `Format.kt` (date/time, `identityColor`, `initials`). Theme in `ui/theme/`.

6. **`util/`** — `PermissionUtils` (notification-access check via `enabled_notification_listeners`, battery-
   optimization exemption), `ExportUtils` (hand-rolled, **lossless** CSV/JSON serialization incl.
   control-char escaping, capture time and deleted/edited verdicts), `ShareExport` (shared
   serialize-on-IO + FileProvider share-sheet helper used by the full and per-chat exports),
   `ExportNaming` (file names for chat exports/backups — a chat title is remote-controlled text, so
   everything but letters/digits/umlauts collapses to `_`: no path separator, no `..`, length-capped),
   `VaultCodec` + `VaultBackup` (**encrypted vault backup**: versioned escaped-TSV payload → gzip →
   AES-256-GCM, key from PBKDF2WithHmacSHA256 [210k iterations]; file layout `"KPV1" | salt(16) | iv(12) |
   ciphertext`, extension `.kpvault`; wrong passphrase/tampering throws via the GCM tag; both framework-free
   and unit-tested — restore is idempotent because ids are content hashes + insert IGNORE), and
   `SearchUtils`: `escapeLike` (escapes LIKE `%`/`_`, paired with `ESCAPE '\'` in `MessageDao.search`) +
   `findMatches` (case-insensitive highlight ranges via `indexOf(ignoreCase)` — never index into a
   `lowercase()` copy, case folding can change string length).

`NotifVaultApp` (Application) warms up `DatabaseProvider` at startup (on a background thread — Keystore
work would delay app start) so the listener can write immediately, then runs `RetentionPruner.pruneIfDue`
and `NoiseCleanup.runOnce`.

## Things to keep in mind

- **Media can't be captured** — photos/voice/video aren't in the notification payload and Scoped Storage
  blocks WhatsApp's media folder. Don't attempt to add media capture; it's a documented hard limitation.
- **Don't add network permissions or dependencies.** The privacy guarantee (offline-only) is a feature.
- **Keep decision logic in framework-free seams.** There are no instrumented tests here (no emulator in
  the loop), so anything worth verifying — grouping, dedup, retention, restore-merge, file names, codecs —
  lives in plain Kotlin objects/functions that `src/test` can call directly; the Android class around them
  (service, screen, pruner) just wires values in. New logic follows that split.
- UI strings and user-facing text are **German** (`res/values/strings.xml`); match that.
- Release builds currently have `isMinifyEnabled = false`; if enabling R8, SQLCipher/Room may need
  `proguard-rules.pro` keep rules.
