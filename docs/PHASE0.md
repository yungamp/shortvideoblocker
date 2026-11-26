# Phase 0: Inspector, Dumps auf deinem Gerät aufnehmen

Ziel: Herausfinden, woran man Shorts und Reels in **deinen** App-Versionen erkennt (View-IDs, Events),
und ob die Erkennung günstig genug ist. Phase 0 blockiert noch nichts.

## 1. APK installieren

**Variante A: GitHub Actions (ohne PC)**
1. Im Repo unter **Actions → Android build** den neuesten Lauf öffnen.
2. Unten bei *Artifacts* `shortvideoblocker-debug-apk` herunterladen, entpacken, `app-debug.apk` öffnen und installieren.

**Variante B: Android Studio oder Kommandozeile**
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> Hinweis: Jeder CI-Lauf signiert mit einem anderen Debug-Schlüssel. Wenn sich ein Update nicht
> installieren lässt, zuerst die alte Version deinstallieren.

## 2. Bedienungshilfe aktivieren

1. App öffnen → **Bedienungshilfen öffnen** → *ShortVideoBlocker (Inspector)* → einschalten.
2. Ist der Schalter ausgegraut („Eingeschränkte Einstellung“), dann in der App **App-Info öffnen** →
   ⋮ oben rechts → **Eingeschränkte Einstellungen zulassen** und noch einmal versuchen.
3. In der App sollte jetzt „✅ Aktiv und verbunden“ stehen.

## 3. Dumps aufnehmen

1. YouTube oder Instagram öffnen. Links erscheint ein roter, verschiebbarer **SVB-Knopf**.
2. Den Bildschirm in den gewünschten Zustand bringen (siehe Checkliste in der App).
3. SVB antippen → passenden Eintrag wählen → kurz warten, bis der Toast „Gespeichert …“ erscheint.
   Bitte dabei nicht scrollen; der Dump dauert etwa eine Sekunde.
4. Die Checkliste in der App zeigt ✓ für erledigte Szenarien.

Am wichtigsten sind:

| YouTube | Instagram |
|---|---|
| Shorts über Tab | Reels-Tab |
| Short aus Startseite | Reel aus Feed |
| Short nach Wischen | Reel nach Wischen |
| Short über Link | DM-Chat mit Reel |
| Normales Video | Reel aus DM |
| Startseite | Story (darf nicht blockiert werden) |

Tipp: Für „nach Wischen“ einmal wischen und **direkt danach** dumpen. Der Event-Log im Dump zeigt
dann, welche Scroll-Events beim Wischen gekommen sind.

## 4. Dumps teilen

App → **Alle als ZIP teilen** → z. B. an dich selbst schicken und dann hier im Chat hochladen oder
ins Repo committen (`dumps/`).

## Was ein Dump enthält

- Den View-Baum aller Fenster der Ziel-App, **zweimal**: nur „wichtige“ Views und alle Views
  (beantwortet die Frage, ob `flagIncludeNotImportantViews` nötig ist).
- Die Liste der IDs, die nur in der zweiten Variante vorkommen.
- Messungen, wie lange gezielte `findAccessibilityNodeInfosByViewId`-Aufrufe dauern.
- Die letzten etwa 400 Accessibility-Events der Ziel-Apps, mit Quell-ID, Scroll-Indizes und -Deltas.
  Für andere Apps nur Vordergrundwechsel, als Paketname.
- App-Version, Gerätemodell und Android-Version.

**Datenschutz:** Standardmäßig ist „Texte schwärzen“ an. Sichtbarer Text (Nachrichten, Titel,
Kommentare) wird dann durch seine Länge ersetzt, z. B. `<23 Zeichen>`. Inhaltsbeschreibungen
(`desc`) bleiben erhalten, weil sie für die Erkennung wichtig sind (z. B. „Reels“). Sie können aber
Benutzernamen enthalten, etwa „Profilbild von …“. Schau dir die `.txt`-Dateien vor dem Teilen kurz an.
