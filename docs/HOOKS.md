# Hook Handlers & Interception Guide — Unhinge

This guide documents the technical specifications, target classes, intercepted method signatures, parameter transformations, return values, and suppression strategies for all 13 privacy & telemetry hooks and the host UI overlay hook in **Unhinge**.

---

## 1. Hook Summary Matrix

| Handler | Primary Target Class | Key Methods Intercepted | Action / Strategy | Default Pref Key |
| :--- | :--- | :--- | :--- | :--- |
| **`PrivacyFirebaseHook`** | `com.google.firebase.analytics.FirebaseAnalytics`, `AppMeasurementSdk` | `setAnalyticsCollectionEnabled`, `logEvent`, `setUserProperty`, `setUserId` | Force collection `false`, drop event/property logging | `block_firebase_analytics` |
| **`PrivacyCrashlyticsHook`** | `com.google.firebase.crashlytics.FirebaseCrashlytics` | `setCrashlyticsCollectionEnabled`, `log`, `recordException`, `sendUnsentReports` | Force collection `false`, drop reports, return completed dummy Task | `block_crashlytics_upload` |
| **`PrivacyPerfHook`** | `com.google.firebase.perf.metrics.Trace`, `AppStartTrace` | `start`, `stop`, `putAttribute`, `putMetric`, `log*` | Neutralize performance trace timings & network metric emission | `block_perf` |
| **`PrivacyAppsFlyerHook`** | `com.appsflyer.AppsFlyerLib`, `com.appsflyer.internal.AFa1tSDK` | `setDisableAdvertisingIdentifiers`, `anonymizeUser`, `stop`, `getAppsFlyerUID` | Force opt-out flags `true`, return `null` UID, short-circuit `stop(true)` | `block_appsflyer` |
| **`PrivacyIncogniaHook`** | `com.incognia.Incognia` | `init`, `setLocationEnabled`, `send*Event`, `generateRequestTokenSync` | Neutralize fraud events, return empty token `""`, disable location | `block_incognia` |
| **`PrivacySplitHook`** | `io.split.android.client.SplitClient`, `ImpressionsObserver`, `SplitRoomDatabase` | `flush`, `destroy`, `track*`, `log*`, `save*` | Neutralize event emission & impression database writes | `block_split_telemetry` |
| **`PrivacyUbeHook`** | `com.squareup.wire.GrpcClient` | `newCall`, `newCall$wire_grpc_client` | Cancel gRPC calls targeting `/ube.` and `Analytics/PostUbeEvent` | `block_ube` |
| **`PrivacyOkHttpHook`** | `okhttp3.internal.connection.RealCall` | `execute`, `enqueue` | Throw `IOException` or invoke `Callback.onFailure` for telemetry domains | `block_okhttp_telemetry` |
| **`PrivacyMetricWorkersHook`**| `co.hinge.metrics.impl.jobs.SendMetricWork[d]`, `TelemetryTokenStore` | `d`, `doWork`, `TelemetryTokenStore.get*` | Return `ListenableWorker.Result.success()`, clear telemetry token | `block_metric_workers` |
| **`PrivacyGmsComponentsHook`**| Android ComponentManager / Measurement | `AppMeasurementService`, `TransportBackendDiscovery`, etc. | Runtime suppression marker for GMS telemetry daemon services | `block_gms_measurement` |
| **`PrivacyContactsHook`** | `android.content.ContentResolver` | `query(Uri, ...)` | Match `contacts` URIs and return empty `MatrixCursor` | `block_contacts` |
| **`PrivacyLocationHook`** | `android.location.Location` | `getLatitude()`, `getLongitude()` | Round double coordinates to 2 decimal places (~1.1 km city radius) | `fuzz_location` (opt-in) |
| **`PrivacyDataTransportHook`**| `com.google.android.datatransport.cct.CctTransportBackend` | `send(Event)` | Intercept telemetry batches and return synthetic `BackendResponse.ok()` | `block_datatransport` |
| **`HostAppAiFab` Hook** | `co.hinge.app.ui.AppActivity` | `onResume()` | Attach in-app draggable AI Floating Action Button overlay to DecorView | `show_host_app_fab` |

---

## 2. Detailed Technical Specifications

### 2.1 Firebase Analytics Suppression (`PrivacyFirebaseHook.kt`)
- **Target**: `com.google.firebase.analytics.FirebaseAnalytics`, `com.google.android.gms.measurement.api.AppMeasurementSdk`
- **Suppression Mechanism**:
  1. `setAnalyticsCollectionEnabled`: Replaces incoming argument with boolean `false`.
  2. Event & Profile Dropping: Intercepts `logEvent`, `setUserProperty`, `setUserProperties`, `setUserId`, `resetAnalyticsData`, `setSessionTimeoutDuration`, `setMinimumSessionDuration`, `setConsent`, `setDefaultEventParameters`, and `setCurrentScreen`, immediately returning default primitives without executing target code.
  3. Scion Backend: Intercepts `AppMeasurementSdk.logEvent` and sets `setMeasurementEnabled(false)`, `setDataCollectionEnabled(false)`.

### 2.2 Firebase Crashlytics Suppression (`PrivacyCrashlyticsHook.kt`)
- **Target**: `com.google.firebase.crashlytics.FirebaseCrashlytics`
- **Suppression Mechanism**:
  1. Intercepts `setCrashlyticsCollectionEnabled` and forces argument to `false`.
  2. Drops `log`, `recordException`, `setCustomKey`, `setCustomKeys`, `setUserId`, and `sendUnsentReports`.
  3. When methods return a `com.google.android.gms.tasks.Task`, returns `Tasks.forResult(false)` to prevent NPEs in calling coroutines or callback listeners.
  4. On `checkForUnsentReports`, returns `Tasks.forResult(false)` and asynchronously dispatches `deleteUnsentReports()`.

### 2.3 Firebase Performance Metrics Suppression (`PrivacyPerfHook.kt`)
- **Target**: `com.google.firebase.perf.metrics.Trace`, `com.google.firebase.perf.metrics.AppStartTrace`
- **Suppression Mechanism**:
  1. `Trace`: Neutralizes `start`, `stop`, `putAttribute`, `putMetric`, `incrementMetric`, and `removeAttribute`.
  2. `AppStartTrace`: Blocks `on*`, `register*`, `unregister*`, `log*`, `set*`, `put*`, `increment*`, and `mark*` methods while allowing static `getInstance` resolution to avoid startup linkage errors.

### 2.4 AppsFlyer Attribution Suppression (`PrivacyAppsFlyerHook.kt`)
- **Target**: `com.appsflyer.AppsFlyerLib`, `com.appsflyer.internal.AFa1tSDK`
- **Suppression Mechanism**:
  1. For `setDisableAdvertisingIdentifiers`, `setDisableNetworkData`, `anonymizeUser`, `setCollectAndroidId`, and `setCollectImei`, forces parameter to `true`.
  2. Intercepts `stop()` and rewrites invocation to `stop(true)`.
  3. `getAppsFlyerUID()` returns `null`.
  4. Intercepts all declared tracking methods, returning type-safe defaults (`defaultFor(returnType)`).

### 2.5 Incognia Geolocation & Fraud SDK Suppression (`PrivacyIncogniaHook.kt`)
- **Target**: `com.incognia.Incognia`
- **Suppression Mechanism**:
  1. Suppresses `init`, `disable`, `notifyAppInForeground`, `sendCustomEvent`, `sendLoginEvent`, `sendOnboardingEvent`, `sendPaymentEvent`, `clearAccountId`, and `setAccountId`.
  2. Forces `setLocationEnabled(false)`.
  3. `generateRequestTokenSync`: Returns an empty string `""`. Hinge backend handles an empty token gracefully as an un-fingerprinted client without hanging or ANR.

### 2.6 Split.io Feature Flag Telemetry Suppression (`PrivacySplitHook.kt`)
- **Target**: Semantic SDK interfaces `io.split.android.client.SplitClient`, `io.split.android.client.service.impressions.ImpressionsObserver`, `io.split.android.client.service.events.EventsTracker`, `io.split.android.client.storage.db.SplitRoomDatabase`
- **Suppression Mechanism**:
  1. `SplitClient` telemetry sync methods (`flush`, `destroy`) are stubbed to return default values.
  2. Room database writes and tracker dispatch for impression tracking (`track*`, `log*`, `save*`) are dropped, while feature evaluation methods (`getTreatment`) remain 100% operational so app feature toggles do not break.

### 2.7 Hinge UBE (Unified Behavioral Events) Suppression (`PrivacyUbeHook.kt`)
- **Target**: `com.squareup.wire.GrpcClient`
- **Suppression Mechanism**:
  1. Hooks `newCall` and `newCall$wire_grpc_client`.
  2. Inspects gRPC method descriptor path.
  3. If path contains `/ube.` or `Analytics/PostUbeEvent`, immediately invokes `call.cancel()` and logs cancellation to Logcat.

### 2.8 OkHttp Network Host Interception (`PrivacyOkHttpHook.kt`)
- **Target**: `okhttp3.internal.connection.RealCall`
- **Suppression Mechanism**:
  1. Intercepts synchronous `execute()` and asynchronous `enqueue(Callback)`.
  2. Evaluates the request destination URL against `Consts.TELEMETRY_HOST_SUBSTRINGS`:
     - `sdk.split.io`, `auth.split.io`, `telemetry.split.io`, `streaming.split.io`, `events.split.io`
     - `appsflyer.com`, `appengage.`
     - `app-measurement.com`
     - `crashlyticsreports-pa.googleapis.com`
  3. For sync calls, registered with **`ExceptionMode.PASSTHROUGH`** to directly propagate `IOException("Unhinge: telemetry host dropped")` to the caller. This ensures the LibXposed runtime does not catch and bypass the exception under `PROTECTIVE` mode.
  4. For async calls, invokes `callback.onFailure(call, IOException(...))` and cancels execution, cleanly alerting OkHttp dispatcher without crashing the caller.

### 2.9 WorkManager Telemetry Worker Neutralization (`PrivacyMetricWorkersHook.kt`)
- **Target**: `co.hinge.metrics.impl.jobs.SendMetricWork`, `co.hinge.metrics.impl.jobs.SendUnauthenticatedMetricWork`, `co.hinge.telemetry.token.TelemetryTokenStore`
- **Suppression Mechanism**:
  1. Hooks execution methods `d()` or `doWork()`.
  2. Bypasses suspend-continuation methods to prevent Kotlin coroutine state machine corruption.
  3. Returns `ListenableWorker.Result.success()` immediately so WorkManager considers the metric successfully delivered and purges it from the local work database.
  4. Returns `""` from `TelemetryTokenStore` to invalidate any queued outbound metric authentication requests.

### 2.10 Contacts Access Redirection (`PrivacyContactsHook.kt`)
- **Target**: `android.content.ContentResolver`
- **Suppression Mechanism**:
  1. Intercepts `query(Uri, ...)`.
  2. When the query URI references `contacts` or `com.android.contacts`, constructs and returns an empty `MatrixCursor(arrayOf("_id", "display_name"))`.
  3. Hinge perceives an empty address book, protecting phone contacts while preserving dating friend-block features via manual entry.

### 2.11 Location Coordinate Fuzzing (`PrivacyLocationHook.kt`)
- **Target**: `android.location.Location`
- **Suppression Mechanism**:
  1. Intercepts `getLatitude()` and `getLongitude()`.
  2. Rounds returned coordinates using `round(coord * 100.0) / 100.0`.
  3. **Precision**: 2 decimal places provides approximately **1.1 km** resolution. This preserves regional dating pool discovery and distance radius filtering while hiding exact residential or work street addresses.
  4. **Configuration**: Opt-in feature, disabled by default.

### 2.12 Google DataTransport Interception (`PrivacyDataTransportHook.kt`)
- **Target**: `com.google.android.datatransport.cct.CctTransportBackend`
- **Suppression Mechanism**:
  1. Intercepts `send(Event)`.
  2. Returns a reflective `BackendResponse.ok()` instance without transmitting data payload over network.

### 2.13 In-App Host FAB Lifecycle & Overlay Hook (`HostAppAiFab`)
- **Target**: `co.hinge.app.ui.AppActivity.onResume()` + `Application.ActivityLifecycleCallbacks`
- **Injection Mechanism**:
  1. Validates that current activity class is `co.hinge.app.ui.AppActivity`.
  2. Checks `prefs.showHostAppFab`.
  3. Resolves root decor view and adds the draggable overlay button.
  4. On activity teardown (`onDestroy`), detaches the overlay container to prevent WindowManager leaks.
  5. **Dynamic Screen Clues Extraction**: On FAB tap, scans the view hierarchy and `AccessibilityNodeInfo` for visible prompt text, photo accessibility descriptions (`"[Name]'s photo"`), and skip actions (`"Skip [Name]"`), without referencing any obfuscated classes.
  6. **Multi-Feed Candidate Targeting**: Passes extracted clues to `HostCandidateReader.readTargetCandidate()` to semantically match and display whatever candidate profile is currently visible on screen (including Standouts, Discover, and Likes You).
