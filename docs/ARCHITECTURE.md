# Architektur & Systemlogik

ShortVideoBlocker blockiert YouTube Shorts und Instagram Reels in den **offiziellen Apps** über einen
`AccessibilityService`. Kein Root, keine gepatchten APKs.

## Getroffene Entscheidungen

| Thema | Entscheidung |
|---|---|
| Reaktion bei Erkennung | `GLOBAL_ACTION_BACK` + kurzer Hinweis (Toast) |
| Umfang v1 | Nur den Vollbild-Player (Shorts/Reels) blockieren; Tabs/Feed-Bereiche erst in v2 per Overlay |
| Ausnahmen | Einzeln geöffnete Videos (aus DM oder externem Link) erlaubt, Weiterwischen blockiert |
| Verteilung | Erst privat (Sideload), Architektur aber Play-konform (Offenlegung, keine Datensammlung) |
| Stack | Kotlin, minSdk 26, targetSdk 36, Jetpack Compose, kotlinx.serialization, Coroutines, manuelle DI |

## Akku-/Performance-Prinzip: gestufte Filterung

Der teure Teil ist jeder Zugriff auf den View-Baum (IPC in den Ziel-Prozess). Jede Stufe ist billiger als die nächste:

```
Android-System
  │  Stufe 0: packageNames = nur YouTube + Instagram, notificationTimeout = 150 ms
  ▼
EventGate (in-process, 0 IPC)
  │  Stufe 1: App aktiv in Einstellungen? Cooldown nach Aktion aktiv?
  │  Stufe 2: Debounce/Throttle pro App (trailing edge, max. ~3 Prüfungen/s, Backoff bei stabilem Zustand)
  ▼
Detector
  │  Stufe 3: findAccessibilityNodeInfosByViewId(...) gezielt (1–3 IPC-Calls, Abbruch beim 1. Treffer)
  │  Stufe 4: nur falls Regel es verlangt: begrenzte Breitensuche (Tiefe ≤ 6, ≤ 150 Knoten)
  ▼
SessionTracker (Zustandsautomat pro App) → reagiert nur auf Zustandswechsel
  ▼
ActionExecutor → GLOBAL_ACTION_BACK + Toast, dann 800 ms Cooldown
```

Service-Konfiguration ab Phase 1:

| Attribut | Wert |
|---|---|
| `packageNames` | `com.google.android.youtube, com.instagram.android` |
| `accessibilityEventTypes` | `typeWindowStateChanged \| typeWindowContentChanged \| typeViewScrolled` |
| `notificationTimeout` | `150` |
| `accessibilityFlags` | `flagReportViewIds` (`flagIncludeNotImportantViews` nur falls Phase 0 zeigt, dass nötig) |

Weitere Regeln: kein Disk-I/O im Event-Pfad (Settings als `StateFlow`-Snapshot), keine Timer, kein
Foreground-Service, keine WakeLocks.

> **Phase 0 weicht bewusst ab:** Der Inspector lauscht auf alle Events, ohne Paketfilter und ohne
> Zusammenfassen, um zu sehen, was die Apps tatsächlich senden. Für fremde Apps wird nur der Paketname
> bei Vordergrundwechseln protokolliert.

## Regel-Engine

Erkennungsmerkmale liegen als JSON (zunächst `assets/`, später optional remote – nur Daten, kein Code):

```json
{
  "version": 3,
  "apps": [{
    "package": "com.instagram.android",
    "screens": [{
      "type": "SHORT_VIDEO_PLAYER",
      "anyViewId": ["clips_viewer_view_pager"],
      "pagerViewId": "clips_viewer_view_pager",
      "fallback": { "descContains": ["Reels"], "maxDepth": 6 }
    }],
    "exemptEntryContexts": [{ "anyViewId": ["direct_thread_container"] }]
  }]
}
```

IDs oben sind Platzhalter; die echten kommen aus den Phase-0-Dumps. Der Detector arbeitet gegen das
Interface `UiNode` (nicht direkt `AccessibilityNodeInfo`) und ist damit auf der JVM testbar.

## Zustandsautomat pro App

```
            ┌────────── Player verschwindet ──────────┐
            ▼                                          │
       ┌─────────┐   Player erkannt,          ┌──────────────┐
       │ BROWSING│── Einstieg aus Feed/Tab ──▶│ BLOCK        │──▶ Back + Toast
       └─────────┘                            └──────────────┘
            │  Player erkannt, Einstieg aus DM oder externem Link
            ▼
     ┌───────────────┐  Scroll-Event am Pager   ┌──────────────┐
     │ ALLOWED_SINGLE│── (nach 1 s Schonfrist) ─▶│ BLOCK        │
     └───────────────┘                          └──────────────┘
```

| Einstieg | Erkennung | Ergebnis |
|---|---|---|
| Aus Instagram-DM-Thread | letzter erkannter Screen vor Player = DM-Thread | ALLOWED_SINGLE |
| Aus anderer App (Link) | vorheriges Vordergrund-Paket ≠ Ziel-App | ALLOWED_SINGLE |
| Shorts-/Reels-Tab, Feed | alles andere | BLOCK |

Schutz vor Endlosschleifen: Cooldown 800 ms; ist der Player danach noch da, max. ein zweites Back,
danach Zustand `STUCK` mit Hinweis „Regeln evtl. veraltet“.

## Module

```
app/src/main/java/io/github/yungamp/shortvideoblocker/
├─ ui/          Compose: Onboarding, Einstellungen, (Debug) Inspector
├─ service/     BlockerAccessibilityService, ServiceState  (+ ab Phase 1: EventGate, SessionTracker, ActionExecutor)
├─ detection/   UiNode, AccessibilityUiNode                (+ ab Phase 1: Detector, RuleRepository)
├─ inspector/   Phase 0: TreeDumper, EventLog, Overlay, DumpRepository, Szenarien
└─ settings/    InspectorSettings (Phase 0: SharedPreferences; Phase 3: DataStore)
```

## Phasen

| Phase | Inhalt | Status |
|---|---|---|
| 0 | Projektgerüst + Inspector, Dumps auf echtem Gerät sammeln | ✅ umgesetzt, Dumps ausstehend |
| 1 | EventGate, Detector, Back + Toast für beide Apps | offen |
| 2 | SessionTracker mit DM/Link-Ausnahme und Weiterwisch-Erkennung | offen |
| 3 | Onboarding, Einstellungen (DataStore), Statistik, Feinschliff | offen |
| 4 | Optional: Overlay für Tabs, Remote-Regeln, Play-Release | offen |

## Bekannte Einschränkungen

- Bei Sideload (Android 13+) muss „Eingeschränkte Einstellungen zulassen“ aktiviert werden.
- Mit „Erweitertem Schutz“ (Advanced Protection, Android 17) sperrt Android den Dienst für Nicht-Bedienungshilfe-Apps.
- View-IDs ändern sich mit App-Updates → Regeln müssen gepflegt werden.
