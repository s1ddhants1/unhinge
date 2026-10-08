# Hook Handlers & Interception Guide — Unhinge

This guide documents the technical specifications, target classes, intercepted method signatures, parameter transformations, return values, and suppression strategies for all privacy & telemetry hooks, the host UI overlay hook, the feed navigation hook, and the entitlement unlock hooks in **Unhinge**.

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
| **`HostFeedNavigationHook`** | `android.database.sqlite.SQLiteDatabase` | `rawQueryWithFactory(…)` | Inject SQL OFFSET into `discover_subject` queries for free feed browsing | `enable_feed_navigation` |
| **`HostUndoHook`** | `android.app.SharedPreferencesImpl` | `getInt`, `getLong`, `contains`, `getAll` | Return `999` for `local/apiAvailableSkipUndos` for unlimited native rewind | `enable_feed_navigation` |

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

### 2.13 In-App Host FAB Lifecycle & Grounded Overlay Hook (`HostAppAiFab`)
- **Target**: `co.hinge.app.ui.AppActivity.onResume()` + `Application.ActivityLifecycleCallbacks`
- **Injection Mechanism**:
  1. Validates that current activity class is `co.hinge.app.ui.AppActivity`.
  2. Checks feature toggles (`prefs.showHostAppFab`, `prefs.showAvailableLikes`, `prefs.enableFeedNavigation`).
  3. Injects overlay container (`TAG_FAB_CONTAINER`) directly into `(activity.findViewById(android.R.id.content) as? ViewGroup)` (`ContentFrameLayout`), bypassing `AbstractComposeView` and `FragmentContainerView` restrictions.
  4. **Native Pass "X" Button Grounding (`findPassButtonBounds`)**: Traverses Compose's `AccessibilityNodeProvider` (`HOST_VIEW_ID`) on the active `ComposeView` to locate Hinge's native Pass button (`contentDescription.startsWith("Skip")`). Uses the measured `Rect` to symmetrically mirror the AI FAB at identical diameter (186px / 62dp), elevation, and vertical coordinates.
  5. **Dynamic Lifecycle & Recomposition Sync**: Uses both `OnGlobalLayoutListener` and a throttled `OnPreDrawListener` (100ms) on `decor.viewTreeObserver` to track Compose canvas redraws. On activity teardown (`onDestroy`), detaches the overlay container and cleans up all listeners.
  6. **Contextual Visibility**: Controls automatically switch to `View.GONE` when not on an active candidate profile (Standouts carousel, Matches, Profile tabs) and restore to `View.VISIBLE` on Discover.
  7. **Real-Time Available Likes Extraction (`HostLikesReader`)**: Reads Hinge's live remaining likes (`localAvailableLikes`/`apiAvailableLikes`) and roses (`localAvailableSuperlikes`/`apiAvailableSuperLikes`) directly from Hinge's private SharedPreferences (`default.xml`). Uses a strong-referenced `OnSharedPreferenceChangeListener` to immediately reflect decrements upon sending likes. Injects an authentic pill badge (`TAG_AVAILABLE_LIKES`) docked 6dp above the AI FAB with authentic Hinge vector icons (`HingeIcons`), dynamic warning color tiers, and interactive summary tooltips.

### 2.14 Non-Destructive Feed Navigation (`HostFeedNavigationHook`)
- **Target**: `android.database.sqlite.SQLiteDatabase.rawQueryWithFactory(...)`, `androidx.room.RoomDatabase`, and `android.app.SharedPreferencesImpl.getStringSet(...)`.
- **State Manager**: `FeedNavigator` (singleton, in-memory `AtomicInteger navOffset`, `WeakReference` SQLite and Room InvalidationTracker references, `ThreadLocal` re-entrancy guard).
- **Interception Mechanism**:
  1. **Non-Destructive In-Memory Paging (Zero Touch Synthetic Motion, Zero Profile Rejection)**:
     - **Forward (`navigateForward`)**: Increments in-memory `navOffset`, signals Room's `InvalidationTracker` (`notifyObserversByTableNames("discover_subject")` and `refreshVersionsAsync()`), and touches `discover_subject` in SQLite. Hinge's Room query re-runs with `LIMIT ... OFFSET $navOffset`, advancing the candidate view in-place.
     - **Back (`navigateBack`)**: Decrements in-memory `navOffset` and signals Room's `InvalidationTracker`, returning to previous candidates seamlessly.
     - **Zero writes to `pending_ratings`**: Candidates are never marked as skipped or rejected during browsing. Multi-step forward and backward browsing (e.g. forward 10, back 10) works without limitation.
  2. **Query Offset Injection**:
     - Hooks `rawQueryWithFactory` on `SQLiteDatabase`.
     - Detects Hinge's candidate queries matching `discover_subject` (including queries filtering `NOT IN pending_ratings`).
     - Appends or modifies query suffix with `LIMIT ... OFFSET $navOffset`.
  3. **Native Action Compatibility**:
     - When the user decides to like or pass the active candidate at `navOffset > 0`, Hinge natively executes its rating pipeline.
     - `HostFeedNavigationHook` detects the insert on `pending_ratings` and invokes `FeedNavigator.onRatingInserted()`, clamping `navOffset` to prevent out-of-bounds queue indices.
     - Legitimate user ratings flow directly through OkHttp to Hinge's servers without network interception drops.
  4. **Unlimited Native Rewind Entitlement Injection**:
     - Hooks `SharedPreferencesImpl.getStringSet` for key `"USER_PERMISSIONS"` and injects `"undo_skip_replenish_unlimited"`.
     - `HostAppAiFab.ensureUnlimitedUndos(activity)` persists `"undo_skip_replenish_unlimited"` into Hinge's `default.xml` user permissions set.
     - `HostUndoHook` sets `localAvailableSkipUndos` and `apiAvailableSkipUndos` to 999.
  5. **Session Reset & Screen Lifecycle**:
     - On user reset, tab change away from Discover, or activity pause, `FeedNavigator.reset()` resets `navOffset` back to 0 and re-triggers Room invalidation.
  6. **Unified Navigation Capsule**: Centered horizontally between the native Pass button and AI FAB (`[ ‹  pos / total  › ]`, 36dp height, 18dp corner radius, 12dp elevation). Chevrons dynamically enable/disable based on navigation position. Suppressed on Standouts and non-candidate screens.
- **Framework Stability**: Targets public Android framework contracts (`SharedPreferencesImpl`, `SQLiteDatabase`, `SQLiteOpenHelper`) and public AndroidX Room interfaces (`RoomDatabase`, `InvalidationTracker`) exclusively — no obfuscated R8 class names. Resilient across all Hinge updates.

