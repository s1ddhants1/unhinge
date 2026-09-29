# Unhinge

[![Build APKs](https://github.com/s1ddhants1/unhinge/actions/workflows/build.yml/badge.svg)](https://github.com/s1ddhants1/unhinge/actions/workflows/build.yml)
[![Nightly Release](https://img.shields.io/github/v/release/s1ddhants1/unhinge?include_prereleases&label=nightly&color=ED5564)](https://github.com/s1ddhants1/unhinge/releases/tag/nightly)
[![LibXposed API](https://img.shields.io/badge/LibXposed-API%20102-blue.svg)](https://libxposed.github.io/api/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-purple.svg)](https://kotlinlang.org/)
[![Material 3](https://img.shields.io/badge/Material%203-Expressive-coral.svg)](https://m3.material.io/)

Unhinge is an open-source privacy-enhancing [LibXposed](https://github.com/libxposed) module and companion management app for Hinge (`co.hinge.app`). It combines zero-breakage telemetry suppression with a discreet in-app **AI Prompt Wingman** and an archival manager.

---

## Features

### Privacy & Telemetry Suppression
- **Zero-Breakage Choke Points**: Drops invasive telemetry uploads while keeping all dating features (matching, chatting, media upload, push notifications, billing) completely stable.
- **15 Modular Protection Layers**: Granularly toggled via RemotePreferences without restarting the app.

| # | Layer | Mechanism (Hook Point) | Usability Guard |
|---|---|---|---|
| 1 | Firebase Analytics / Scion | `FirebaseAnalytics.logEvent` / `setUserProperty` / `setUserId` / screen methods dropped; `setAnalyticsCollectionEnabled` forced `false` | RemoteConfig, Installations, FCM untouched |
| 2 | Firebase Auto Screen & Dwell | Measurement components disabled (`AppMeasurementService`, `AppMeasurementReceiver`); DataTransport CCT fake OK | Dating/push unaffected; pure measurement components |
| 3 | Crashlytics | Collection forced off; `log` / `recordException` / `sendUnsentReports` dropped | Dating flows untouched |
| 4 | Firebase Performance | `Trace.start/stop/put*/incrementMetric` no-op; `AppStartTrace` record callbacks no-op | Network auto-instrumentation kept |
| 5 | AppsFlyer (Attribution) | `init` / `start` / `logEvent` / `logSession` / ad-revenue / install receivers no-op on `AppsFlyerLib` and `AFa1tSDK` | `getInstance` kept; privacy setters forced `true` |
| 6 | Incognia (Risk & Location) | `init` / `sendCustomEvent` / `notifyAppInForeground` no-op; token sync returns `""` | Login continues (server treats token as absent) |
| 7 | Split.io (Flags & Telemetry) | `flush` / impression-upload no-op | `getTreatment` flag evaluation untouched |
| 8 | Hinge UBE (1st-Party Graph) | `GrpcClient.newCall` cancelled for UBE gRPC paths (`/ube.v*/Analytics/PostUbeEvent`) | Core `prod-api.hingeaws.net` untouched |
| 9 | OkHttp Host Blocklist | `RealCall.execute`/`enqueue` blocked for metrics domains (`hingeprod.net`, `split.io`, `appsflyer.com`, `incognia.com`, `app-measurement.com`) | Core API / CDN / maps / FCM hosts untouched |
| 10 | Metric Workers | `SendMetricWork[d]` / `SendUnauthenticatedMetricWork[d]` → `Result.success()` without uploading | Notification workers untouched |
| 11 | Advertising ID | `AdvertisingIdClient.getAdvertisingIdInfo` → zeroed ID + limit-ad-tracking `true` | Attribution/fraud only |
| 12 | Hardware Identifiers | AppSet → `("0", 1)`; `ANDROID_ID` → emulator-style fake; IMEI/Serial → `""` | Dating unaffected |
| 13 | Firebase Installations ID | `getId` → fake FID | FCM token path untouched |
| 14 | Contacts Privacy | `ContentResolver.query` on contact URIs → empty `MatrixCursor` | Blocklist UI is manual-entry |
| 15 | Location Fuzzing (Opt-in) | `Location.getLatitude/Longitude` rounded to 2 decimals (~1.1 km) | **OFF by default** to preserve discovery |

### AI Prompt Wingman
- **In-App Floating Action Button (FAB)**: Draggable, edge-snapping circular FAB injected directly over Hinge's active activity.
- **Direct Candidate DB Extraction**: Automatically reads the active candidate profile, demographics, questions, answers, and photos directly from Hinge's internal SQLite database—no root shell commands required.
- **OpenRouter & OpenAI Architecture**: Server-Sent Events (SSE) streaming, structured output schema, and exponential backoff retry.
- **Persona Tones**: Tailors prompt replies across 4 distinct styles (**Witty**, **Thoughtful**, **Flirty**, and **Funny**) with one-tap clipboard copying.
- **LRU In-Memory Cache**: Prevents duplicate LLM queries when cycling through candidates.

### Material 3 Expressive Design
- **Connected Settings Cards**: `Material3SettingsGroup` with custom 24dp/6dp corner radius rhythm.
- **Modal Bottom Sheet**: 28dp top corner radius with custom drag handle and smooth swipe transitions.
- **Pure Black OLED Mode**: True `#000000` AMOLED dark theme for power savings and ultra-high contrast.
- **Dynamic Monet & Coral Theming**: Expressive Material You theming seeded with coral (`#ED5564`).

### Local Candidate Archival & Search
- Permanent local SQLite storage of visited candidate profiles, prompts, and photos.
- Fast multi-token search, facet filtering (Liked You, In Feed, Standouts, Passed, With Comments), and full photo preview modals.

---

## Project Layout

```
unhinge/
├── .github/
│   ├── scripts/
│   │   └── parse_changelog.sh       # Changelog parser for semver releases
│   └── workflows/
│       ├── build.yml                # Main CI: Release/Debug builds + Nightly release
│       ├── build_pr.yml             # PR verification with cached ephemeral keystore
│       ├── build_quick.yml          # Fast manual release build (skips lint)
│       └── release.yml              # Tagged production release pipeline
├── app/
│   ├── build.gradle.kts             # AGP 9.3.2, Kotlin 2.4.10, LibXposed 102
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/io/github/s1ddhants1/unhinge/
│       │   ├── Module.kt            # LibXposed entry point & FAB lifecycle hook
│       │   ├── ai/                  # OpenRouter AI client & caching
│       │   ├── data/                # Host SQLite reader & local archival DB
│       │   ├── hook/                # Suppression hooks (Firebase, AppsFlyer, OkHttp)
│       │   ├── hook/ui/             # Injected FAB & AI bottom sheet overlay
│       │   ├── model/               # Candidate data models & serialization
│       │   ├── ui/                  # Companion app screens & Compose navigation
│       │   └── util/                # PreferencesManager & LSPatch helpers
│       └── resources/META-INF/xposed/
│           ├── module.prop          # API 101/102 metadata
│           ├── java_init.list       # Entry point registration
│           └── scope.list           # Target scope: co.hinge.app
├── docs/                            # Modular technical architecture & reverse engineering
│   ├── AI_WINGMAN.md                # In-app overlay, touch physics, and LLM streaming
│   ├── ARCHITECTURE.md              # Process boundaries, lifecycle, and system design
│   ├── DATABASE_AND_STORAGE.md      # SQLite schemas, archival engine, and data models
│   ├── HOOKS.md                     # Technical specs for all 13 privacy & telemetry hooks
│   ├── REVERSE_ENGINEERING.md       # Target APK layout, database tables, and anti-tamper
│   └── TELEMETRY_AND_PRIVACY.md     # Surveillance ecosystem audit and spoofing guarantees
├── gradle/
│   ├── libs.versions.toml           # Version catalog
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── .gitignore
├── AGENTS.md                        # Master operational guidelines & mandates for AI agents
├── build.gradle.kts                 # Root Gradle build script
├── changelog.md                     # Version ledger (---vX.Y.Z)
├── gradle.properties
├── gradlew
├── gradlew.bat
├── HINGE_ANALYSIS.md                # Target APK reverse-engineering & hook mapping
├── README.md
└── settings.gradle.kts              # Root project settings
```

---

## Building & Installing

### Prerequisites
- JDK 25 (Eclipse Adoptium Temurin)
- Android SDK (API 37)

### Command-line Build
```bash
# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

Compiled APKs will be located at:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release-unsigned.apk`

---

## Security & Privacy Notice
- **Keystores & Credentials**: Signing keystores, `.jks`, and private keys are strictly excluded via `.gitignore` and never committed to the repository.
- **OpenRouter API Key**: Stored locally in device private shared preferences or entered on demand in the AI Settings dialog.

---

## License
This project is open-source under the Apache 2.0 License.
