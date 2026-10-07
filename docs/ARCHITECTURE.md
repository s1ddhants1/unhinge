# Architecture & System Design — Unhinge

This document provides a comprehensive technical specification of **Unhinge**, detailing process boundaries, module lifecycle, runtime interception mechanics, in-app overlay injection, and companion application architecture.

---

## 1. Process Separation & System Boundaries

Unhinge operates across two strictly isolated Android processes:

```
+-----------------------------------------------------------------------------------------+
|                                    Target Process                                       |
|                                   (co.hinge.app)                                        |
|                                                                                         |
|  [App.onCreate] <----------- [Module.kt] (Entry Hook, PRIORITY_HIGHEST, deoptimized)     |
|                                     |                                                   |
|         +---------------------------+---------------------------+                       |
|         |                                                       |                       |
|         v                                                       v                       |
|  [Privacy Suppression Hooks]                             [Host UI Injection]            |
|  - PrivacyFirebaseHook (Analytics events & properties)   - HostAppAiFab (Draggable FAB) |
|  - PrivacyCrashlyticsHook (Exception & breadcrumb drop)  - HostAppAiSheetContent (M3)   |
|  - PrivacyPerfHook (Traces & HTTP metrics)               - HostCandidateReader          |
|  - PrivacyAppsFlyerHook (Attribution & campaigns)          (Direct in-process SQLite    |
|  - PrivacyIncogniaHook (Fraud & geolocation SDK)            read from internal DB)      |
|  - PrivacySplitHook (Feature flag telemetry)                                            |
|  - PrivacyUbeHook (Hinge internal telemetry)             [Feed Navigation]              |
|  - PrivacyOkHttpHook (Metrics interceptor blocking)      - HostFeedNavigationHook       |
|  - PrivacyMetricWorkersHook (WorkManager jobs drop)        (Native rewind/undo hook)    |
|  - PrivacyGmsComponentsHook (GAID, AppSet, ANDROID_ID)   - FeedNavigator               |
|  - PrivacyContactsHook (Contact query isolation)           (Native UI motion dispatch)  |
|  - PrivacyLocationHook (Fuzzing / coordinate rounding)                                  |
|  - PrivacyDataTransportHook (GMS event transport drop)                                  |
+-----------------------------------------------------------------------------------------+
                                            ^
                                            | IPC (LibXposed Dynamic Remote Preferences)
                                            v
+-----------------------------------------------------------------------------------------+
|                                  Companion App Process                                  |
|                             (io.github.s1ddhants1.unhinge)                              |
|                                                                                         |
|  [App.kt] --------------> Application entry point & service monitoring                  |
|  [MainActivity.kt] -----> Host for Material 3 Jetpack Compose Navigation                |
|  [MainViewModel.kt] ----> StateFlow coordinator for candidate feeds & telemetry metrics  |
|  [CandidateArchiveDb] --> Local SQLite persistence of visited candidate dossiers        |
|  [SuStorageReader] -----> Root-based fallback extractor for out-of-process sync         |
|  [OpenRouterService] ---> OpenRouter JSON-Schema opener generation engine                 |
|  [OpenRouterStreaming] -> OpenRouter SSE streaming chunk parser                          |
|  [ZenRouter] -----------> OpenCode Zen router & free-tier client emulator               |
|  [Multi-Protocol LLM] --> OpenAiResponses, AnthropicMessages & GoogleGemini services    |
|  [AiWingmanHelper] -----> Central AI coordinator, in-memory cache & UI StateFlow stream  |
|  [PreferencesManager] --> Centralized preference management & fallback persistence      |
+-----------------------------------------------------------------------------------------+
```

### 1.1 Process Boundaries & Rules
1. **Target App Hook Layer (`hook/`, `hook/privacy/`, `hook/ui/`)**:
   - Executes entirely inside the address space of `co.hinge.app`.
   - Must remain strictly thread-safe, non-blocking, and allocation-efficient.
   - All reflective lookups and method interceptions must be guarded by `attempt("...", silent = true)` to ensure that host application stability is never compromised under any runtime exception.
   - Database operations against Hinge's internal SQLite database (`/data/data/co.hinge.app/databases/db`) are opened in `SQLiteDatabase.OPEN_READONLY` mode to eliminate write contention and lock contention.
2. **Companion App Layer (`ui/`, `data/`, `ai/`, `model/`)**:
   - Executes inside `io.github.s1ddhants1.unhinge`.
   - Renders the primary user interface using Jetpack Compose and Material 3 Expressive styling.
   - Communicates configuration updates to the hook layer via LibXposed Dynamic Remote Preferences (`PreferencesManager`), falling back to local `SharedPreferences` in rootless LSPatch integrated environments.

---

## 2. Module Lifecycle & Initialization (`Module.kt`)

[Module.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/Module.kt) extends `XposedModule` and coordinates runtime injection via a two-phase architecture:

```
[Target App Launch]
        |
        v
[Module.onPackageReady]
        |
        +---> Check: param.isFirstPackage && param.packageName == "co.hinge.app"
        |     |
        |     +---[False]---> If API >= 102: detach() -> RETURN
        |     |
        |     +---[True]----> Continue
        v
[Phase 1: Early Bytecode Hooking (onPackageReady)]
        |
        +---> Initialize RemotePreferences & PreferencesManager
        +---> Install 12 Pure Bytecode Hooks (Firebase, Crashlytics, Perf,
        |     AppsFlyer, Incognia, Split, Ube, OkHttp, Location, Contacts,
        |     MetricWorkers, DataTransport)
        |     * Preempts ContentProvider.onCreate() cold-start leakage
        v
[Phase 2: Context-Bound Hooking (App.onCreate)]
        |
        +---> Hook App.onCreate() [PRIORITY_HIGHEST, deoptimize = true]
                    |
                    v (Inside App.onCreate with Application Context)
        [Module.applyContextHooks(ctx, cl, prefs)]
                    |
                    +---> If integrated / fallback: loadFromFallbackStorage(ctx)
                    +---> Disable GMS measurement components via PackageManager
                    +---> Register ActivityLifecycleCallbacks for HostAppAiFab
                    +---> Fallback: Hook AppActivity.onResume() if callbacks unavailable
```

### 2.1 Dynamic Package Isolation
In compliance with LibXposed API 102+, `Module.onPackageReady` strictly enforces target matching:
```kotlin
if (!param.isFirstPackage || param.packageName != Consts.TARGET_PACKAGE) {
    if (apiVersion >= XposedInterface.API_102) {
        attempt("detach non-target package", silent = true) { detach() }
    }
    return
}
```
This guarantees that Unhinge never hooks system server, Android framework classes, or unrelated applications.

### 2.2 Hot Reloading (`onHotReloading` / `onHotReloaded`)
Unhinge implements seamless hot reload support:
1. **`onHotReloading`**:
   - Bundles a timestamp into `param.savedInstanceState`.
   - Cleans up `OnSharedPreferenceChangeListener` in `PreferencesManager`.
   - Clears `hookHandles` cache and resets static identifiers (such as synthetic GMS IDs).
2. **`onHotReloaded`**:
   - Iterates through `param.oldHookHandles` and safely unhooks prior generations.
   - Resolves the active `Application` via `ActivityThread.currentApplication()`.
   - Re-applies Phase 1 bytecode hooks and Phase 2 context hooks immediately without requiring a full target process restart.

---

## 3. Hook Infrastructure & Tracked Execution (`HookRegistry.kt`)

All method interceptions in Unhinge route through the tracked hook builder in [HookRegistry.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/hook/HookRegistry.kt):

### 3.1 Protective & Passthrough Exception Isolation
Every hook is registered with configurable `ExceptionMode`:
- **`ExceptionMode.PROTECTIVE`** (Default): Safely swallows and logs unexpected hooker exceptions, preserving host stability.
- **`ExceptionMode.PASSTHROUGH`**: Directly propagates exceptions thrown by the hooker to the caller. Used in `PrivacyOkHttpHook` so `IOException` is received by OkHttp's caller as a simulated network drop instead of being intercepted and bypassed by the LibXposed runtime.

```kotlin
val builder = hook(executable)
    .setPriority(priority)
    .setExceptionMode(exceptionMode)
``````
If an exception occurs within a hook callback or within the original method intercepted by the hook, the ART hooking framework intercepts the fault, preventing host application crashes and allowing graceful fallbacks.

### 3.2 ART Compiler Inlining & Deoptimization
Short methods, primitive getters, and boolean flags are aggressive candidates for ART profile-guided compilation (PGO) inlining. When `deoptimize = true` is supplied to `hookTracked()`, Unhinge explicitly invokes `deoptimize(executable)` to ensure the method remains at an interpreted or JIT-hookable boundary.

### 3.3 Hook ID Normalization & Memory Tracking
To support hot reload and granular hook lifecycle auditing, hook identifiers are sanitized via regex `[^A-Za-z0-9_.#-]`, hashed with parameter signatures, truncated to 128 characters, and tracked in a thread-safe `ConcurrentHashMap<String, XposedInterface.HookHandle>`.

---

## 4. Host App UI Overlay Architecture (`hook/ui/`)

Unhinge injects an interactive Material 3 AI Wingman interface directly on top of Hinge's active activity:

### 4.1 Activity Attachment & View Hierarchy Bridging
When `co.hinge.app.ui.AppActivity` reaches `onResume`, [HostAppAiFab.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/hook/ui/HostAppAiFab.kt) checks `prefs.showHostAppFab` and mounts a floating overlay:
1. Obtains the root `window.decorView as ViewGroup`.
2. Injects a full-screen pass-through `FrameLayout` (`TAG_FAB_CONTAINER`).
3. Attaches an elevated, circular FAB with gradient background (`#ED5564` to `#FF6584`), ripple effects, and custom touch listeners.

### 4.2 Touch Physics & Edge Docking
The FAB supports intuitive gestures:
- **Drag Tracking**: Measures raw touch offsets against initial view margins.
- **Click Detection**: Differentiates between taps and drags using a movement threshold (`moveThreshold = 10 * density`).
- **Edge Snapping**: On `ACTION_UP` or `ACTION_CANCEL`, computes the nearest horizontal display boundary (left or right) and animates the button into a docked margin position using an exponential decay interpolation (`150ms`).

### 4.3 In-Process Compose Dialog & ViewTree Owners
When the FAB is clicked, it opens a modal `Dialog` hosting [HostAppAiSheetContent.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/hook/ui/HostAppAiSheetContent.kt) inside a `ComposeView`.
Because the host activity may not expose Jetpack Compose `ViewTreeOwners` to dynamically injected subtrees, Unhinge bridges them reflectively:
```kotlin
ViewTreeLifecycleOwner.set(decor, activity as LifecycleOwner)
ViewTreeViewModelStoreOwner.set(decor, activity as ViewModelStoreOwner)
ViewTreeSavedStateRegistryOwner.set(decor, activity as SavedStateRegistryOwner)
```
This enables full Compose rendering, animations, theme cascading (`UnhingeTheme`), and clipboard integration within the host app process.

---

## 5. In-Process SQLite Candidate Extraction (`HostCandidateReader.kt`)

Rather than relying on shell execution or root commands, the injected hook layer directly opens Hinge's active database:
- **Path**: `/data/data/co.hinge.app/databases/db`.
- **Mode**: `SQLiteDatabase.OPEN_READONLY`.
- **Data Extracted**:
  - `prompts`: Maps question identifiers to question prompt text.
  - `subject_answers`: Parses question IDs and JSON payload `{"response": "..."}` ordered by position.
  - `subject_media`: Reads ordered photo URLs for each subject.
  - `discover_subject`: Extracts feed batch order.
  - `impressions`: Identifies incoming likes and initiated interactions.
  - `profiles`: Joins 25+ demographic and lifestyle attributes into [CachedCandidateProfile](../app/src/main/java/io/github/s1ddhants1/unhinge/model/CompanionModels.kt).

---

## 6. Companion Application Architecture (`ui/`, `data/`)

The companion manager app provides deep inspection, local archival, telemetry statistics, and AI model orchestration:

1. **State Management**:
   - [MainViewModel.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ui/MainViewModel.kt) coordinates UI state via `StateFlow`.
   - Dispatches background data loading to `Dispatchers.IO`.
2. **Settings Subsystem (`ui/component/settings/`)**:
   - Organized following the modular subpage architecture of InstaEclipse.
   - [SettingsScreen.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ui/component/settings/SettingsScreen.kt) orchestrates smooth `AnimatedContent` subpage transitions based on `SettingsSubpage`.
   - Categories:
     - **Appearance**: ThemeMode (`SYSTEM`, `LIGHT`, `DARK`) selection via `SingleChoiceSegmentedButtonRow` and AMOLED pure black toggle.
     - **AI Prompt Wingman**: Configured via standardized `SettingsSectionCard` and `SettingsActionRow` architecture with interactive dialogs for API Key (masked), API base URL, Model selection with quick-preset chips, Model Parameters sliders (temperature, top-P, reasoning effort), and custom system prompt editor.
     - **Privacy & Telemetry**: 13 modular switches controlling tracking SDK suppression, device ID spoofing, and location fuzzing.
3. **Archival Persistence**:
   - [CandidateArchiveDb.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/data/CandidateArchiveDb.kt) maintains a persistent local SQLite ledger (`candidate_archive.db`, version 3) with full-text search, rating annotations, and review flags.
4. **Root Fallback Extractor**:
   - [SuStorageReader.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/data/SuStorageReader.kt) provides root-based shell extraction of `/data/data/co.hinge.app/shared_prefs/` and database dumps when inspecting telemetry outside of the target process.
5. **Standardized Design System & Typography (`ui/component/DesignTokens.kt`, `ui/theme/Typography.kt`)**:
   - `ShapeTokens`: Unified corner radii (`Card` = 18dp, `CardNested` = 14dp, `Pill` = 12dp, `Badge` = 8dp, `Search` = 16dp).
   - `UnhingeTypography`: Standardized Material 3 typography scale (strictly eliminating ad-hoc `.sp` font sizes, line heights, and ALL-CAPS text across companion app and hook overlay).
   - Reusable components: `UnhingeDoubleBezelCard`, `UnhingeBadge`, `UnhingeSearchBar`, `EmptyStateView`, and `HingePhotoViewer`.
