# Changelog

Alle nennenswerten Änderungen an Kleene Petze. Format nach
[Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionierung nach
[Semantic Versioning](https://semver.org/lang/de/).

## [1.10.0] - 2026-09-27

### Neu
- **Material 3 Expressive**: echtes `MaterialExpressiveTheme` mit physikbasierter Bewegung
  (Federn statt fester Dauern), große flexible Kopfzeilen, Einstellungen als Karten, die
  gestaffelt hereinfedern, Bildschirmübergänge und federnde Druck-Rückmeldung. Respektiert
  „Animationen entfernen“.
- **Erscheinungsbild**: Hell / Dunkel / System, optional Farben vom Hintergrundbild (Android 12+).
- **Über die App**: Version, Website, Quelltext, Änderungen, Lizenz — und ein PayPal-Spendenknopf.
- **Optionale Update-Prüfung** (standardmäßig **aus**): einmal am Tag fragt die App
  `kleene-petze.celox.io` nach der neuesten Version und meldet sie per Benachrichtigung und
  Hinweis auf der Startseite. Dafür hat die App jetzt die Berechtigung `INTERNET` — genutzt
  ausschließlich von dieser Prüfung; ausgeschaltet geht die App nie ins Netz, und erfasste
  Nachrichten verlassen das Gerät nie.
- Die Petze selbst begrüßt dich beim ersten Start; Symbol mit Themed-Icon-Ebene (Android 13+).
- Lizenz: **MIT**.

### Behoben
- **App-Sperre verschluckte Export und Import**: Der Datei-Dialog schickt die App in den
  Hintergrund, die Sperre warf dabei die ganze Oberfläche weg — das Ergebnis ging verloren, übrig
  blieb eine leere Datei. Die Oberfläche bleibt jetzt unter der Sperre erhalten; auch die
  Navigation springt nach dem Entsperren nicht mehr auf die Startseite zurück.
- **Export/Import nach Drehen des Geräts**: Der Zustand liegt jetzt im ViewModel und übersteht
  Drehen und Verlassen der Einstellungen.
- **Abgebrochener Export sah wie eine gültige Sicherung aus**: Die Datei wird bei einem Fehler
  gelöscht und nie „fertig“ verschlüsselt.
- **Export konnte Nachrichten doppelt schreiben oder auslassen**, wenn währenddessen neue
  eintrafen oder alte gelöscht wurden (Paging über Schlüssel statt Versatz).
- **Chats blitzten vor der Sperre kurz auf**; mit aktiver Sperre zeigt auch die App-Übersicht
  keine Inhalte mehr (`FLAG_SECURE`), und TalkBack liest unter der Sperre nichts vor.
- **Sperre öffnete sich bei vorübergehenden Sensorfehlern** ohne Nachfrage — jetzt nur, wenn das
  Gerät dauerhaft keine Sperre kennt.
- Die Sperre einzuschalten sperrt dich nicht mehr sofort aus.
- Unverschlüsselte Chat-Exporte blieben unbegrenzt im Cache liegen.
- Bilder wurden bei jeder neuen WhatsApp-Benachrichtigung erneut dekodiert.
- Die Update-Prüfung (WorkManager) kann dem Erfassungs-Wächter keine Job-ID mehr wegnehmen.
- Der Wächter-Job wird ersetzt, wenn sich seine Parameter in einer neuen Version ändern.
- Veralteter Hinweis „Medien können nicht gesichert werden“ korrigiert.

## [1.9.1] - 2026-08-10
### Behoben
- Seit 1.7.2 wurde nichts mehr erfasst: der Fortschrittsbalken-Filter hielt jede Nachricht für
  einen Upload, weil `NotificationCompat` die Fortschritts-Extras immer setzt.

## [1.9.0] - 2026-08-05
### Neu
- Bilder aus Benachrichtigungen werden gesichert (Vorschau, verschlüsselt in der Datenbank);
  Bilder ohne Text gehen nicht mehr verloren.

## [1.8.0] - 2026-08-03
### Neu
- Erfassung verbindet sich nach Neustart, App-Update und alle 15 Minuten selbst neu.

## [1.7.2] - 2026-07-30
### Neu
- Version in den Einstellungen; Upload-Fortschritt wird nicht mehr als Nachricht gesichert.

## [1.7.0] - 2026-07-30
### Neu
- Export und Import des ganzen Archivs als verschlüsselte Datei, JSON oder CSV.

## [1.6.4] - 2026-07-30
### Behoben
- Anruf-Hinweise im Benachrichtigungstitel werden erkannt.

## [1.6.3] - 2026-07-29
### Behoben
- WhatsApp-Sprach- und Videoanrufe landen nicht mehr als eigener Chat im Archiv.

## [1.6.2] - 2026-07-29
### Behoben
- Einzelne WhatsApp-Gruppen wurden nicht erfasst; Dienst-Benachrichtigungen werden gefiltert.

## [1.6.0] - 2026-07-11
### Neu
- Bearbeitete Nachrichten, Ansicht „Aufgedeckt“, Erfassungs-Status, verschlüsselte Sicherung.

## [1.5.0] - 2026-07-02
### Behoben
- Absturz bei Sonderzeichen in Chatnamen und Suchtreffern; Datenbank-Schema v3 mit Migration.

## [1.4.0] - 2026-06-27
### Neu
- Federnde Bewegungen (M3-Expressive-Anmutung).

## [1.3.0] - 2026-06-27
### Neu
- Gelöschte Nachrichten werden hervorgehoben.

## [1.2.0] - 2026-06-26
### Neu
- Chats werden über einen stabilen Schlüssel gruppiert; neuer Name „Kleene Petze“.

## [1.1.0] - 2026-06-25
### Neu
- Chat-Ansicht für das Archiv.

## [1.0.0] - 2026-06-21
### Neu
- Erste Version: WhatsApp-Benachrichtigungen dauerhaft und verschlüsselt sichern.
