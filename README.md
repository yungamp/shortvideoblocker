# ShortVideoBlocker

Android-App, die **YouTube Shorts** und **Instagram Reels** in den offiziellen Apps blockiert, über
einen `AccessibilityService`, ohne Root und ohne gepatchte APKs.

**Status: Phase 0 (Inspector).** Die App blockiert noch nichts. Sie liest auf Knopfdruck die
Bildschirmstruktur von YouTube und Instagram aus, damit wir zuverlässige und sparsame Erkennungsregeln
bauen können.

- [Architektur & Systemlogik](docs/ARCHITECTURE.md)
- [Phase 0: Dumps aufnehmen](docs/PHASE0.md)

## Bauen

Voraussetzungen: JDK 17+ und das Android SDK (Platform 36).

```bash
./gradlew testDebugUnitTest assembleDebug
```

Die APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`. Jeder Push baut sie auch per
GitHub Actions (Artifact `shortvideoblocker-debug-apk`).
