<p align="center">
  <img src="docs/banner.png" alt="Kleene Petze – messages never die" width="100%">
</p>

# Kleene Petze

<!-- Project status -->
<p align="center">
  <a href="https://github.com/pepperonas/kleene-petze/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/pepperonas/kleene-petze?logo=github&color=14B8A6"></a>
  <a href="https://github.com/pepperonas/kleene-petze/releases/latest"><img alt="Release date" src="https://img.shields.io/github/release-date/pepperonas/kleene-petze?color=14B8A6"></a>
  <a href="https://github.com/pepperonas/kleene-petze/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/pepperonas/kleene-petze/total?logo=github&label=downloads&color=0D9488"></a>
  <a href="https://github.com/pepperonas/kleene-petze/releases/latest"><img alt="Latest downloads" src="https://img.shields.io/github/downloads/pepperonas/kleene-petze/latest/total?label=latest%20downloads&color=0D9488"></a>
  <a href="https://github.com/pepperonas/kleene-petze/actions/workflows/release.yml"><img alt="Build" src="https://img.shields.io/github/actions/workflow/status/pepperonas/kleene-petze/release.yml?logo=githubactions&logoColor=white&label=release%20build"></a>
</p>
<p align="center">
  <a href="https://github.com/pepperonas/kleene-petze/commits/main"><img alt="Last commit" src="https://img.shields.io/github/last-commit/pepperonas/kleene-petze?logo=git&logoColor=white&color=F59E0B"></a>
  <a href="https://github.com/pepperonas/kleene-petze/commits/main"><img alt="Commit activity" src="https://img.shields.io/github/commit-activity/m/pepperonas/kleene-petze?color=F59E0B"></a>
  <a href="https://github.com/pepperonas/kleene-petze/issues"><img alt="Issues" src="https://img.shields.io/github/issues/pepperonas/kleene-petze?logo=github"></a>
  <img alt="Code size" src="https://img.shields.io/github/languages/code-size/pepperonas/kleene-petze?color=0F172A">
  <img alt="Repo size" src="https://img.shields.io/github/repo-size/pepperonas/kleene-petze?color=0F172A">
  <img alt="Top language" src="https://img.shields.io/github/languages/top/pepperonas/kleene-petze?logo=kotlin&logoColor=white&color=7F52FF">
</p>

<!-- Tech stack -->
<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white">
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-26%20(Android%208.0)-3DDC84?logo=android&logoColor=white">
  <img alt="targetSdk" src="https://img.shields.io/badge/targetSdk-35-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.10-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Material 3" src="https://img.shields.io/badge/Material%203-Expressive-757575?logo=materialdesign&logoColor=white">
</p>
<p align="center">
  <img alt="Gradle" src="https://img.shields.io/badge/Gradle-8.11.1-02303A?logo=gradle&logoColor=white">
  <img alt="AGP" src="https://img.shields.io/badge/AGP-8.7.2-3DDC84?logo=android&logoColor=white">
  <img alt="KSP" src="https://img.shields.io/badge/KSP-2.0.21--1.0.28-7F52FF?logo=kotlin&logoColor=white">
  <img alt="JDK" src="https://img.shields.io/badge/JDK-17-007396?logo=openjdk&logoColor=white">
  <img alt="Coroutines" src="https://img.shields.io/badge/Coroutines-1.9.0-7F52FF?logo=kotlin&logoColor=white">
</p>

<!-- Data & privacy -->
<p align="center">
  <img alt="Room" src="https://img.shields.io/badge/Room-2.6.1-FF6F00?logo=sqlite&logoColor=white">
  <img alt="SQLCipher" src="https://img.shields.io/badge/SQLCipher-4.6.1-1BA1E2">
  <img alt="Encryption" src="https://img.shields.io/badge/encryption-AES--256-success?logo=letsencrypt&logoColor=white">
  <img alt="Biometric lock" src="https://img.shields.io/badge/lock-Biometric-blueviolet">
  <img alt="On-device" src="https://img.shields.io/badge/data-100%25%20on--device-14B8A6">
  <img alt="No network" src="https://img.shields.io/badge/network-none-critical">
  <img alt="License" src="https://img.shields.io/badge/license-Proprietary-lightgrey">
  <img alt="Made by celox.io" src="https://img.shields.io/badge/made%20by-celox.io-0D9488">
</p>

Speichert eingehende Nachrichten-Benachrichtigungen **dauerhaft und verschlüsselt** – wie
der Samsung-Benachrichtigungsverlauf, aber ohne 24-Stunden-Verfall. Gelöschte WhatsApp-
Nachrichten bleiben so lesbar, weil die ursprüngliche Benachrichtigung in eine lokale,
verschlüsselte Datenbank geschrieben wird, sobald sie ankommt.

## Download

Die fertige, signierte APK gibt es unter **[Releases](https://github.com/pepperonas/kleene-petze/releases/latest)**.
APK herunterladen → auf dem Gerät öffnen → Installation aus unbekannter Quelle erlauben.

## Wie es funktioniert

Android liefert jede Benachrichtigung an einen `NotificationListenerService`
(`service/NotificationCaptureService.kt`). WhatsApp sendet beim Löschen einer Nachricht
**keine** zweite Benachrichtigung – die Originalnachricht ist also längst angekommen.
Kleene Petze parst sie sofort (bevorzugt über `MessagingStyle`, das Absender + echten
Zeitstempel jeder Einzelnachricht enthält) und speichert sie ab. Mehrfach gelieferte
Nachrichten werden über einen Inhalts-Hash dedupliziert.

**Korrekte Chat-Zuordnung:** Nachrichten werden über einen *stabilen* Chat-Schlüssel
(`conversationKey` aus `shortcutId` → `tag` → Titel) gruppiert, nicht über den
Anzeigenamen. Der ist bei 1:1-Chats oft leer und bei Gruppen manchmal nicht gesetzt –
würde man danach gruppieren, zerfielen Gruppen pro Absender bzw. vermischten sich mit
gleichnamigen Einzelchats. So landet jeder Kontakt/jede Gruppe verlässlich in genau
einem Verlauf. Die Übersicht zeigt pro Chat den **neuesten Titel** + letzte Nachricht;
der Verlauf rendert als Chat-Ansicht mit Datumstrennern, Sprecher-Gruppierung und
farbigen Absendern.

## Was geht – und was nicht

| Funktion | Status |
|---|---|
| Text-Nachrichten (1:1 & Gruppen) | ✅ zuverlässig |
| Absender + echter Zeitstempel | ✅ via MessagingStyle |
| Korrekte Chat-Gruppierung (stabiler `conversationKey`) | ✅ |
| Chat-Ansicht: Datumstrenner, Sprecher-Gruppierung, Avatare | ✅ |
| Volltextsuche (mit Treffer-Hervorhebung), App-Filter in der Übersicht | ✅ |
| Export (CSV/JSON, verlustfrei inkl. Erfassungszeit + Gelöscht-/Bearbeitet-Status), auch pro Chat | ✅ |
| Nachricht kopieren/teilen + Details (Long-Press auf die Sprechblase) | ✅ |
| Verschlüsselung (SQLCipher/AES-256), Biometrie-Sperre (re-lockt im Hintergrund) | ✅ |
| **Verschlüsseltes Backup/Restore** (`.kpvault`, Passphrase → PBKDF2 + AES-256-GCM; Import ist merge-sicher) | ✅ |
| Capture-Status (Zugriff/Dienst/letzte Erfassung) + Warn-Banner | ✅ |
| Optionale Aufbewahrungsdauer (30–365 Tage, Standard: unbegrenzt) | ✅ |
| **Gelöschte Nachrichten markieren** (Original hervorgehoben + 🗑-Badge, gesammelt in der „Aufgedeckt"-Ansicht) | ⚠️ nur wenn die Nachricht gelöscht wird, *während sie noch ungelesen im Benachrichtigungs-Shade liegt* (dann ersetzt WhatsApp den Text durch „…gelöscht", den wir der gespeicherten Originalnachricht zuordnen); Platzhalter in 10 Sprachen erkannt |
| **Bearbeitete Nachrichten aufdecken** (frühere Version bleibt sichtbar, ✏️-Markierung) | ⚠️ gleiche Bedingung: die Bearbeitung muss eine Benachrichtigung auslösen, solange der Chat ungelesen ist |
| **Medien** (Fotos, Sprach-/Videonachrichten) | ❌ technisch nicht möglich – stecken nicht in der Notification, Scoped Storage sperrt WhatsApps Medienordner |
| Bereits gelesene Nachrichten, die später gelöscht werden | ❌ erzeugen keine Benachrichtigung → Löschung nicht erkennbar |
| Stummgeschaltete Chats | ❌ erzeugen oft keine Benachrichtigung |
| Nachrichten empfangen, während der Chat offen ist | ❌ keine Benachrichtigung |

## Bauen

1. **Android Studio** (Ladybug/2024.2+) → *Open* → diesen Ordner wählen. Gradle-Sync
   lädt alle Abhängigkeiten (AGP 8.7, Kotlin 2.0, Compose, Room, SQLCipher).
2. Gerät/Emulator anschließen → *Run ▶*.

Oder per Terminal: `./gradlew assembleDebug` → APK unter `app/build/outputs/apk/debug/`.
(Eine `local.properties` mit `sdk.dir=...` wird von Android Studio automatisch angelegt.)

### Tests

Reine JVM-Unit-Tests (kein Emulator nötig):

```bash
./gradlew testDebugUnitTest
```

Abgedeckt: Dedup-Schlüssel (`messageContentId`, mit fixem SHA-256-Anker), Löschungs-Platzhalter
in 10 Sprachen (`Deletion`), CSV-/JSON-Export-Escaping inkl. Steuerzeichen (`ExportUtils`),
Backup-Serialisierung Round-Trip (`VaultCodec`), Backup-Verschlüsselung inkl. Falsch-Passphrase-
und Manipulations-Fällen (`VaultBackup`), Datums-/Zeit- und Identitäts-Helfer (`Format`) sowie
LIKE-Escaping und Such-Highlight-Ranges (`SearchUtils`).

## Einrichtung auf dem Samsung S24 Ultra (wichtig)

One UI killt Hintergrunddienste sehr aggressiv. Damit kein Mitschnitt verloren geht:

1. **Benachrichtigungszugriff erteilen** – beim ersten Start, oder
   *Einstellungen → Apps → Spezieller Zugriff → Benachrichtigungszugriff → Kleene Petze*.
2. **Akku-Optimierung ausnehmen** – im Onboarding-Schritt, oder
   *Einstellungen → Akku → Hintergrundnutzungslimits → „Nie in den Standby" → Kleene Petze hinzufügen*.
3. Optional: in *Einstellungen → Apps → Kleene Petze* die Option „Im Hintergrund aktiv lassen".

## Datenschutz / DSGVO

- Keine Netzwerkberechtigung, keine Cloud, kein Tracking. Alles bleibt auf dem Gerät.
- Datenbank verschlüsselt (SQLCipher, 256-bit). Schlüssel in `EncryptedSharedPreferences`
  (AES-256-GCM, Android Keystore). Backups (Cloud/Geräte­transfer) sind deaktiviert.
- Erfasst werden nur Benachrichtigungen, die **auf diesem Gerät** eingehen – also
  Nachrichten *an dich*. Beachte: gespeicherte Inhalte stammen von Dritten; deren
  Weiterverarbeitung/Weitergabe liegt in deiner Verantwortung als Betreiber.

## Projektstruktur

```
data/    CapturedMessage (Entity, mit conversationKey + editSuperseded), MessageDao,
         AppDatabase (v4), DatabaseProvider (SQLCipher + Migrationen),
         SettingsStore (DataStore), RetentionPruner (optionale Aufbewahrung)
notif/   MessageExtractor – Notification → CapturedMessage(s)
         MessageId – stabiler Dedup-Inhalts-Hash (messageContentId)
         Deletion – Lösch-Platzhalter-Erkennung (10 Sprachen)
service/ NotificationCaptureService – der Listener (Edit-Erkennung, Heartbeat, Status)
ui/      Compose-Screens (Onboarding, Home, Conversation, Flagged/„Aufgedeckt", Settings)
         + ViewModel, Components (Avatar), Format (Datum/Zeit, Farben, Initialen)
util/    PermissionUtils, ExportUtils, ShareExport, SearchUtils,
         VaultCodec + VaultBackup (verschlüsseltes Backup/Restore)
schemas/ Room-Schema-JSON (Referenz für handgeschriebene Migrationen)
src/test JUnit-Unit-Tests (MessageId, Deletion, ExportUtils, VaultCodec, VaultBackup,
         Format, SearchUtils)
```

## Release erstellen (Maintainer)

Releases werden signiert und automatisch von GitHub Actions gebaut
(`.github/workflows/release.yml`). Der Signing-Keystore liegt **nur** im privaten Repo
`pepperonas/keystore` und in den Repo-Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`) – nie in diesem Repo.

```bash
# versionCode (+1) und versionName in app/build.gradle.kts erhöhen, dann:
git tag v1.2.3 && git push origin v1.2.3
```

Der Workflow baut die signierte APK und hängt sie an einen neuen GitHub Release. Alle
Releases sind mit demselben Keystore signiert und damit als Update übereinander
installierbar.

---
© 2026 Martin Pfeffer | celox.io
