# AGENTS.md — Unhinge Operational Guide

This document defines the atomic instructions, non-negotiable architectural mandates, and reference links for AI agents and developers working on **Unhinge**.

---

## 1. Scope & System Boundaries

- **Target Package**: Exclusively `co.hinge.app`.
- **Dynamic Isolation**: `Module.onPackageReady` MUST check `param.packageName == Consts.TARGET_PACKAGE` and `param.isFirstPackage`, immediately invoking `detach()` on non-target packages (LibXposed API 102+). Never hook system server, framework, or other applications.
- **Process Separation**:
  - **Hook Layer (`hook/`, `hook/privacy/`, `hook/ui/`)**: Runs inside `co.hinge.app`. Must be thread-safe, allocation-efficient, and strictly isolated using `hookTracked` with `ExceptionMode.PROTECTIVE`.
  - **In-App Overlay Layer (`hook/ui/`)**: Injects `HostAppAiFab` and `HostAppAiSheetContent` into Hinge's active activity with `ViewTreeLifecycleOwner` bridging.
  - **In-Process Database Layer (`data/HostCandidateReader.kt`)**: Opens Hinge's internal SQLite database exclusively in `SQLiteDatabase.OPEN_READONLY` mode. Never lock or modify the host database.
  - **Companion App Layer (`ui/`, `data/`, `ai/`, `model/`, `util/`)**: Runs in `io.github.s1ddhants1.unhinge`. Communicates with the module only via LibXposed Remote Preferences (`PreferencesManager`) with fallback `SharedPreferences` for integrated LSPatch/rootless environments.
- **Platform Matrix**:
  - Target Versions: `co.hinge.app` v10.4.0 (168201253) and newer releases.
  - Android OS: API 32 (12L) through API 36/37 (Android 15/16).
  - Hooking Framework: LibXposed API 101/102+ (LSPosed, Vector, LSPatch rootless).
  - Native ABI: `arm64-v8a` (16 KB page-size alignment compatible).

---

## 2. Atomic Operational Mandates

### 2.1 Obfuscation-Proof & Crash-Proof Hook Development
1. **Target Semantic SDK & Framework Invariants**: Whenever intercepting tracking systems (Firebase, AppsFlyer, Split.io, Incognia, OkHttp, WorkManager, Location, Contacts), target public SDK interfaces or stable framework contracts rather than volatile ProGuard/R8 class names (`defpackage.*`).
2. **Always Use `hookTracked()`**: Never invoke raw `XposedModule.hook()`. Use `module.hookTracked(executable, idPrefix = "...", deoptimize = true)`. Deoptimize any short getters/setters susceptible to ART compiler inlining.
3. **Isolate Failures with `attempt()`**: Wrap reflective lookups and dynamic hook attachments with `attempt("description", silent = true) { ... }`. Target app execution must never crash due to a failed hook or missing class.
4. **Protective Exception Mode**: Default all hooks to `XposedInterface.ExceptionMode.PROTECTIVE` to guarantee that hooker exceptions do not crash the host app process; use `PASSTHROUGH` explicitly only where simulated exceptions (such as `IOException` in network drops) must reach the host caller.

### 2.2 In-App Overlay & UI Injection Rules
1. **Lifecycle Decoupling**: Register `ActivityLifecycleCallbacks` and remove overlays on `onDestroy` to prevent WindowManager view leaks.
2. **Bridge ViewTree Owners**: Always bridge `ViewTreeLifecycleOwner`, `ViewTreeViewModelStoreOwner`, and `ViewTreeSavedStateRegistryOwner` on dynamically attached `ComposeView` elements.
3. **Strict Read-Only Database Queries**: Always use `SQLiteDatabase.OPEN_READONLY` when accessing Hinge's internal database files to prevent database locking or contention.

### 2.3 Reverse Engineering Workflow
1. **Query Existing Analysis First**: Always review [HINGE_ANALYSIS.md](HINGE_ANALYSIS.md) and [docs/REVERSE_ENGINEERING.md](docs/REVERSE_ENGINEERING.md) before decompiling or extracting APKs.
2. **No Machine-Specific Paths**: Never commit local workstation paths or raw APK binaries into version control.

### 2.4 Code Hygiene & Documentation Integrity
1. **Zero Dead Code**: Delete unused imports, orphaned methods, and abandoned experimental stubs before finishing any task.
2. **Zero Useless LLM Comments**: Strictly avoid comments that merely restate syntax. Only document non-obvious engineering rationale, ART compiler quirks, or reverse-engineered schemas.
3. **Continuous Documentation Synchronization**: Whenever modifying hooks, classes, preferences, or reverse-engineering targets, immediately update this guide and the referenced documents in `docs/`.

---

## 3. Build & Verification Commands

Execute and verify after EVERY modification:

```bash
# Run Kotlin compilation across debug sources
./gradlew compileDebugKotlin

# Run all unit tests (must pass cleanly)
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug

# Assemble release APK
./gradlew assembleRelease
```

---

## 4. Modular Documentation Index

Refer to the dedicated modular documentation files in `docs/` for deep technical details:

- **[System Architecture & Design](docs/ARCHITECTURE.md)**:
  Process boundaries, module lifecycle, runtime interception mechanics, in-app overlay injection, and companion application architecture.
- **[Hook Handlers & Interception Guide](docs/HOOKS.md)**:
  Detailed technical specifications, hooked classes, method signatures, parameter transformations, return values, and suppression strategies for all 13 privacy & telemetry hooks plus the host UI overlay hook.
- **[Reverse Engineering Field Manual & Target Analysis](docs/REVERSE_ENGINEERING.md)**:
  Target application profile, decompilation workflows, R8 obfuscation layout, internal SQLite database schemas, and anti-tamper security defenses.
- **[AI Prompt Wingman Architecture](docs/AI_WINGMAN.md)**:
  Floating Action Button mechanics, touch physics, snap-to-edge docking, ViewTree owner bridging, in-process candidate SQLite extraction, and production AI wingman architecture (`DeepLService`, `OpenRouterService`, `OpenRouterStreamingService`, `AiWingmanHelper`, `PromptEntry`).
- **[Database, Storage & Archival Systems](docs/DATABASE_AND_STORAGE.md)**:
  Companion application SQLite persistence (`candidate_archive.db`, version 3), root-assisted fallback extraction engine (`SuStorageReader`), and serialized candidate data models.
- **[Telemetry, Privacy & Surveillance Analysis](docs/TELEMETRY_AND_PRIVACY.md)**:
  Comprehensive surveillance audit of Hinge, network endpoint classification, third-party analytics SDK interception, and hardware/identity fingerprint spoofing guarantees.
