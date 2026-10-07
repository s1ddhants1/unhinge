# Telemetry, Privacy & Surveillance Analysis — Unhinge

This document provides a comprehensive audit of the tracking and surveillance mechanisms present in **Hinge** (`co.hinge.app`), mapping all network endpoints, third-party analytics SDKs, hardware fingerprinting vectors, and the suppression guarantees implemented by **Unhinge**.

---

## 1. Network Endpoint Classification

Unhinge enforces a strict separation between core dating functionality and surveillance infrastructure:

### 1.1 Whitelisted Endpoints (Preserved for Dating Operations)
These hosts are never intercepted or modified, ensuring that authentication, profile cards, messaging, media uploads, and match updates operate without hindrance:

| Endpoint Host | Purpose |
| :--- | :--- |
| `prod-api.hingeaws.net` | Primary backend API (matches, chats, profile data, likes, discovery feeds) |
| `media.hingenexus.com` / `api.prod.cdn.gcp.hingenexus.com` | Media CDN for candidate photos, videos, and profile media |
| `fcmregistrations.googleapis.com` | Firebase Cloud Messaging (instant match & message push notifications) |
| `maps.googleapis.com` | Google Places autocomplete and neighborhood location resolution |
| `firebaseremoteconfig.googleapis.com` | Remote app configuration parameters |

### 1.2 Suppressed & Dropped Endpoints (Surveillance & Metrics)
Requests to these hosts are intercepted at the OkHttp transport layer, gRPC client layer, or dropped via local SDK stubs:

| Endpoint Host | Service / SDK | Interception Strategy |
| :--- | :--- | :--- |
| `prod-ue1-metrics.hingeprod.net` | Hinge internal metrics backend | OkHttp / gRPC cancellation |
| `client-telemetry.hingeprod.net` | Hinge client telemetry | OkHttp host drop |
| `client-telemetry-preauth.hingeprod.net` | Pre-authentication telemetry | OkHttp host drop |
| `sdk.split.io`, `events.split.io`, `streaming.split.io` | Split.io feature flag metrics | OkHttp host drop & client method stubs |
| `*.appsflyer.com`, `appengage.*` | AppsFlyer install attribution | OkHttp host drop & SDK method stubs |
| `app-measurement.com` | Google Firebase Analytics (Scion) | SDK collection disabled & method drop |
| `crashlyticsreports-pa.googleapis.com` | Google Crashlytics report uploads | Report deletion & completed Task stub |

---

## 2. Tracking SDK Interception Specifications

### 2.1 Firebase Analytics & Scion Backend
- **Threat Vector**: Logs granular in-app user interactions (e.g. `swipe_card`, `view_profile`, `send_message`, `open_app`), session lengths, and user demographics.
- **Unhinge Interception**:
  - `setAnalyticsCollectionEnabled(false)` forced on startup.
  - Method calls to `logEvent`, `setUserProperty`, `setUserId`, and `setCurrentScreen` are discarded.
  - `AppMeasurementSdk` collection flags forced to `false`.

### 2.2 Firebase Crashlytics
- **Threat Vector**: Gathers stack traces, log breadcrumbs, device memory states, free storage, battery status, and user identifiers upon crashes or logged non-fatal exceptions.
- **Unhinge Interception**:
  - `setCrashlyticsCollectionEnabled(false)` enforced.
  - Methods `log`, `recordException`, `setCustomKey`, and `setUserId` are dropped.
  - `checkForUnsentReports` returns completed dummy tasks and dispatches `deleteUnsentReports()`.

### 2.3 Firebase Performance Monitoring
- **Threat Vector**: Traces app startup duration, fragment transition delays, and HTTP request metrics (timing, byte counts).
- **Unhinge Interception**:
  - Neutralizes `Trace.start()`, `putAttribute()`, `putMetric()`.
  - Stubs `AppStartTrace` methods to avoid timing telemetry.

### 2.4 AppsFlyer Attribution
- **Threat Vector**: Tracks marketing campaigns, deep link referrals, user acquisition channels, and fingerprints device identifiers across ad networks.
- **Unhinge Interception**:
  - Forces `setDisableAdvertisingIdentifiers(true)`, `setDisableNetworkData(true)`, and `anonymizeUser(true)`.
  - Rewrites `stop()` to `stop(true)`.
  - `getAppsFlyerUID()` returns `null`.

### 2.5 Split.io Analytics & Event Streaming
- **Threat Vector**: Logs feature treatment evaluations, behavioral impressions, and background event streams to `events.split.io`.
- **Unhinge Interception**:
  - Stubs telemetry flush methods on `defpackage.xbi`.
  - Neutralizes writes to `SplitRoomDatabase` and `ImpressionsObserver`.
  - Preserves `getTreatment()` evaluation logic so feature switches behave normally.

### 2.6 Incognia Geolocation & Fraud SDK
- **Threat Vector**: Analyzes cellular tower IDs, WiFi BSSID/SSID scans, and precise location patterns to create behavioral fraud risk scores.
- **Unhinge Interception**:
  - Stubs event logging (`sendCustomEvent`, `sendLoginEvent`, `sendPaymentEvent`).
  - Forces `setLocationEnabled(false)`.
  - `generateRequestTokenSync` returns an empty string `""`.

### 2.7 Hinge UBE (Unified Behavioral Events over gRPC)
- **Threat Vector**: Real-time gRPC stream logging micro-interactions and swipe velocities.
- **Unhinge Interception**:
  - Hooks `com.squareup.wire.GrpcClient.newCall`.
  - Detects paths matching `/ube.` or `Analytics/PostUbeEvent` and calls `call.cancel()`.

### 2.8 WorkManager Telemetry Workers
- **Threat Vector**: Enqueues persistent periodic background jobs (`SendMetricWork`, `SendUnauthenticatedMetricWork`) that survive app restarts.
- **Unhinge Interception**:
  - Hooks work execution methods `d()` / `doWork()`.
  - Returns `ListenableWorker.Result.success()` immediately so WorkManager clears the job queue.
  - Invalides `TelemetryTokenStore` to return `""`.

---

## 3. Hardware & Identity Fingerprint Spoofing

Unhinge neutralizes persistent hardware and user identifiers:

| Identifier | Native Android Source | Unhinge Spoofing Return Value | Purpose |
| :--- | :--- | :--- | :--- |
| **Google Advertising ID (GAID)** | `AdvertisingIdClient.getAdvertisingIdInfo` | Zeroed UUID `00000000-0000-0000-0000-000000000000` with limit-ad-tracking `true` | Prevents cross-app advertising profile linkage |
| **AppSet ID** | `AppSetIdInfo.getId` | Static dummy `("0", 1)` | Blocks Google Play family app identification |
| **Android ID** | `Settings.Secure.getString(..., ANDROID_ID)` | Emulator-style synthetic identifier | Prevents device tracking across reinstalls |
| **Hardware Serial & IMEI** | `Build.SERIAL`, `Build.getSerial()`, `TelephonyManager.getDeviceId` | Empty string `""` | Blocks baseband and hardware serialization |
| **Firebase Installations ID (FID)** | `FirebaseInstallations.getId()` | Synthetic randomized 22-char token | Neutralizes persistent Firebase tracking |

---

## 4. Permission & Environmental Privacy

### 4.1 Address Book & Contacts Protection
- **Target App Requirement**: Hinge requests contact access to allow users to block personal acquaintances.
- **Unhinge Interception**:
  - Hooks `ContentResolver.query` targeting contact provider URIs.
  - Returns an empty `MatrixCursor`.
  - The app perceives an empty address book, while manual entry for blocking acquaintances remains available.

### 4.2 Location Coordinate Fuzzing (Opt-In)
- **Mechanism**: Rounds `Location.getLatitude()` and `Location.getLongitude()` to **2 decimal places**.
- **Accuracy Resolution**: 2 decimal places corresponds to approximately **1.1 km** (~0.7 miles).
- **Benefit**: Retains discovery radius matching within the user's city or neighborhood while preventing pinpoint street-level or building-level tracking.
- **Configuration**: Disabled by default (`fuzz_location = false`) to allow precise discovery filtering if preferred by user.
