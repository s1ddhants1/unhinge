# Reverse Engineering Field Manual & Target Analysis — Unhinge

This manual documents the reverse-engineering methodology, APK decompilation workflows, internal obfuscation structures, SQLite database schemas, and anti-tamper defenses of **Hinge** (`co.hinge.app`).

---

## 1. Target Application Profile

| Attribute | Details |
| :--- | :--- |
| **Package Name** | `co.hinge.app` |
| **Analyzed Version** | v10.4.0 (versionCode `168201253`) |
| **Platform Compatibility** | `minSdk 32` (Android 12L), `targetSdk 36`, `compileSdk 37` |
| **Packaging Layout** | Split APK: `base.apk` (~41.6 MB) + `split_config.arm64_v8a.apk` (~6.6 MB native libraries) |
| **Runtime Architecture** | Kotlin 2.x, Jetpack Compose UI (Single-Activity + Navigation Compose), OkHttp 4.x / gRPC / Wire |
| **Dependency Injection** | Dagger-Hilt (`Hilt_AppActivity`), AndroidX Startup Initializers |

---

## 2. APK Decompilation & Exploration Workflow

To inspect new releases of Hinge on an authorized rooted device or workstation:

```bash
# 1. Pull the base APK and ABI splits from the connected device
su -c 'cp /data/app/~~*/co.hinge.app-*/base.apk /data/local/tmp/hinge_base.apk'
su -c 'cp /data/app/~~*/co.hinge.app-*/split_config.arm64_v8a.apk /data/local/tmp/hinge_split.apk'
adb pull /data/local/tmp/hinge_base.apk ./hinge_base.apk
adb pull /data/local/tmp/hinge_split.apk ./hinge_split.apk

# 2. Inspect manifest, badging, and permissions
aapt dump badging hinge_base.apk

# 3. Disassemble resources, smali, and manifest
apktool d -f hinge_base.apk -o hinge_apktool

# 4. Decompile bytecode to Java
jadx -d hinge_jadx --no-res -j 4 hinge_base.apk
```

---

## 3. Application Architecture & Obfuscation Layout

Hinge is compiled with Google D8/R8 with standard name mangling and dead-code stripping:

### 3.1 Namespace Structure
- **Preserved Feature Namespaces (`sources/co/hinge/`)**:
  Hinge retains top-level package paths for feature domains:
  - `co.hinge.app.ui.AppActivity`: The sole primary UI Activity.
  - `co.hinge.domain`: Business entities, candidate repositories, state machines.
  - `co.hinge.chat`: Real-time chat streaming, FCM push handlers, WorkManager message workers.
  - `co.hinge.likesyou`: Standouts, Incoming Likes, and grid feeds.
  - `co.hinge.metrics`: Internal metrics, WorkManager telemetry jobs (`SendMetricWork`).
  - `co.hinge.telemetry`: UBE (Unified Behavioral Events) and token persistence.
  - `co.hinge.billing`: Google Play Billing Client 9.1.0, subscription state caches (Hinge+, HingeX).
- **Mangled Namespaces (`sources/defpackage/`)**:
  Third-party SDK wrappers and core internal utilities are flattened into `defpackage.*`:
  - `defpackage.xbi`: Obfuscated Split.io client interface.
  - R8 synthetic lambdas (`*$$ExternalSyntheticLambda*`).

### 3.2 Native Split Binaries (`lib/arm64-v8a/`)
Extracted from `split_config.arm64_v8a.apk`:
- **`libPhoenixAndroid.so`** (~3.6 MB): The primary media encoding, camera pipeline, and native integrity layer.
- **`libd29c.so`** (~1.4 MB): Secondary cryptographic and data validation routines.
- **`libcrashlytics*.so`**: Native crash signal handlers.
- **`libdatastore_shared_counter.so`**: Inter-process locking primitive for AndroidX DataStore.

---

## 4. Internal Database Schema (`/data/data/co.hinge.app/databases/db`)

Hinge stores active candidate dossiers, prompt responses, feed positions, and user profile data in an unencrypted SQLite database at `/data/data/co.hinge.app/databases/db`.

Unhinge hooks into this database via [HostCandidateReader.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/data/HostCandidateReader.kt) in `OPEN_READONLY` mode:

```
+-----------------------------------------------------------------------------------+
|                                 HINGE SQLITE DB                                   |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  [profiles] (Demographics, lifestyle, verified status, employer, politics)        |
|      |                                                                            |
|      +-- (1:N) --> [subject_media] (Ordered candidate photos: userId, photoUrl)   |
|      |                                                                            |
|      +-- (1:N) --> [subject_answers] (Question answers: userId, answerData JSON)  |
|                         |                                                         |
|                         +-- (N:1) --> [prompts] (Prompt title text dictionary)    |
|                                                                                   |
|  [discover_subject] (Feed ordering: userId, batchId, positionInBatch)             |
|  [impressions] (Incoming likes: subjectId, initiatedWith)                         |
+-----------------------------------------------------------------------------------+
```

### 4.1 Schema Definitions

#### `profiles` Table
Contains full demographic dossiers for fetched candidates:
```sql
CREATE TABLE profiles (
    userId TEXT PRIMARY KEY,
    firstName TEXT,
    age INTEGER,
    height INTEGER,
    hometown TEXT,
    location TEXT,
    jobTitleText TEXT,
    datingIntentionText TEXT,
    relationshipTypeText TEXT,
    religionText TEXT,
    ethnicitiesText TEXT,
    selfieVerified INTEGER,
    circleMember INTEGER,
    educationHistoryText TEXT,
    employmentHistory TEXT,
    politicsText TEXT,
    smoking TEXT,
    drinking TEXT,
    marijuana TEXT,
    drugs TEXT,
    kids TEXT,
    familyPlans TEXT,
    pet TEXT,
    zodiacSign TEXT,
    didJustJoin INTEGER
);
```

#### `prompts` Table
Maps internal prompt UUIDs to question text:
```sql
CREATE TABLE prompts (
    id TEXT PRIMARY KEY,
    prompt TEXT NOT NULL
);
```

#### `subject_answers` Table
Contains candidate answers associated with prompts:
```sql
CREATE TABLE subject_answers (
    userId TEXT,
    questionId TEXT,
    answerData TEXT, -- JSON string containing {"response": "Candidate text response"}
    position INTEGER,
    PRIMARY KEY (userId, questionId)
);
```

#### `subject_media` Table
Ordered image assets for candidate cards:
```sql
CREATE TABLE subject_media (
    userId TEXT,
    photoUrl TEXT,
    position INTEGER,
    PRIMARY KEY (userId, position)
);
```

#### `discover_subject` Table
Maps candidate sequence and batch assignment in the active Discover feed:
```sql
CREATE TABLE discover_subject (
    userId TEXT,
    batchId TEXT,
    positionInBatch INTEGER
);
```

#### `impressions` Table
Tracks incoming likes from other users:
```sql
CREATE TABLE impressions (
    subjectId TEXT PRIMARY KEY,
    initiatedWith TEXT -- "like", "rose", "comment"
);
```

#### `discover_filter` Table
Server-synced Discover filter catalog with per-row paywall gating (verified
on-device, `co.hinge.app` v10.4.0):
```sql
CREATE TABLE `discover_filter` (
    `id` INTEGER NOT NULL,
    `filterId` TEXT NOT NULL,
    `pillType` TEXT NOT NULL,   -- "preference" (free) vs "filter" (gated)
    `permission` TEXT NOT NULL, -- "" (free) vs "filters_plus" (Hinge+ paywall)
    PRIMARY KEY(`id`)
);
```
Observed rows: `age` / `height` / `dating_intentions` (free, empty permission);
`active_today`, `new_here`, `filter_circle_members` (premium, `filters_plus`).
Free accounts lack `filters_plus` in `default.xml` `USER_PERMISSIONS`.

---

## 5. Surveillance Ecosystem & Tracking Footprint

Hinge integrates multiple telemetry, attribution, and analytics SDKs:

```
[co.hinge.app Execution]
        |
        +---> Firebase Analytics (Scion events, user properties)
        +---> Firebase Crashlytics (breadcrumbs, unsent crash reports)
        +---> Firebase Performance (traces, HTTP metrics)
        +---> AppsFlyer (install attribution, organic campaign tracking)
        +---> Incognia (device geolocation, behavioral fraud SDK)
        +---> Split.io (feature flag impressions & event streaming)
        +---> Hinge UBE (Unified Behavioral Events over gRPC)
        +---> Android WorkManager (SendMetricWork jobs)
        +---> Google Play Services DataTransport (CctTransportBackend batches)
```

For complete analysis of how each vector is suppressed, consult [docs/TELEMETRY_AND_PRIVACY.md](TELEMETRY_AND_PRIVACY.md).

---

## 6. Anti-Tamper & Security Defenses

Hinge incorporates several defensive mechanisms:

### 6.1 Package Visibility Probing (`<queries>`)
Hinge’s `AndroidManifest.xml` explicitly declares `<queries>` for approximately 100 package identifiers associated with root solutions, hooking frameworks, and debugging tools:
- **Root Managers**: `com.topjohnwu.magisk`, `io.github.huskydg.magisk`, `me.weishu.kernelsu`, `com.rifsxd.ksunext`, `me.bmax.apatch`.
- **Hooking Frameworks**: `de.robv.android.xposed.installer`, `org.meowcat.edxposed.manager`, `org.lsposed.manager`.
- **Emulators & Cheat Utilities**: Bluestacks, MuMu, Genymotion, LuckyPatcher, Freedom, GameGuardian.
- **Remote Access / Screen Sharing**: TeamViewer, AnyDesk, AirDroid, Vysor.

### 6.2 Play Integrity Attestation
Network attestation occurs via `attestation/data` (`ChallengeResponse`, `VerifyRequest`, `VerifyResponse`).
- Bypassing Play Integrity is not attempted in Unhinge; hooks operate purely on local client telemetry and in-memory UI manipulation to ensure that Google Play account standing remains untampered.

### 6.3 3D Selfie Liveness (FaceTec SDK)
- FaceTec biometric verification is isolated in `FaceTecSessionActivity`.
- Unhinge leaves FaceTec selfie and verification pipelines untouched to prevent account suspension during identity audits.
