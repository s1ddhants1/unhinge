# Database, Storage & Archival Systems — Unhinge

This document details the local data persistence, candidate archival SQLite database, root-assisted extraction engine, and serialized data models in **Unhinge**.

---

## 1. Storage Overview

Unhinge maintains two storage channels:

```
+-----------------------------------------------------------------------------------------+
|                                    Target Process                                       |
|                                   (co.hinge.app)                                        |
|                                                                                         |
|  [Hinge Internal Database] (/data/data/co.hinge.app/databases/db)                        |
|         ^                                                                               |
|         | Read-Only In-Process Queries (SQLiteDatabase.OPEN_READONLY)                   |
|         v                                                                               |
|  [HostCandidateReader]                                                                  |
+-----------------------------------------------------------------------------------------+
                                            |
                                            v (Data Passing via UI Dialog / Bridge)
+-----------------------------------------------------------------------------------------+
|                                  Companion App Process                                  |
|                             (io.github.s1ddhants1.unhinge)                              |
|                                                                                         |
|  [CandidateArchiveDb] (Local candidate_archive.db, version 3)                           |
|         - Stores persistent candidate dossiers across dating sessions                   |
|         - Full-text token search, facet filtering, review annotations                    |
|                                                                                         |
|  [SuStorageReader] (Root fallback inspection engine)                                    |
|         - Copies /data/data/co.hinge.app/shared_prefs/*.xml                             |
|         - Copies /data/data/co.hinge.app/databases/db for companion inspection          |
|                                                                                         |
|  [Coil Image Cache]                                                                     |
|         - Memory & disk caching of candidate photos (SubcomposeAsyncImage)              |
+-----------------------------------------------------------------------------------------+
```

---

## 2. Local Candidate Archival Database (`CandidateArchiveDb.kt`)

[CandidateArchiveDb.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/data/CandidateArchiveDb.kt) manages a persistent SQLite database (`candidate_archive.db`, version 3) stored in the companion app's private storage directory.

### 2.1 Table Schema: `archived_candidates`

```sql
CREATE TABLE IF NOT EXISTS archived_candidates (
    userId TEXT PRIMARY KEY,
    firstName TEXT,
    age INTEGER,
    height INTEGER,
    hometown TEXT,
    location TEXT,
    jobTitle TEXT,
    datingIntention TEXT,
    relationshipType TEXT,
    religion TEXT,
    ethnicity TEXT,
    isSelfieVerified INTEGER,
    isCircleMember INTEGER,
    isStandout INTEGER,
    isDiscover INTEGER,
    isLiveInFeed INTEGER,
    ratingStatus TEXT,          -- "liked", "passed", "standout", "bookmarked"
    likeComment TEXT,           -- User-authored review comment
    school TEXT,
    employer TEXT,
    politics TEXT,
    smoking TEXT,
    drinking TEXT,
    marijuana TEXT,
    drugs TEXT,
    kids TEXT,
    familyPlans TEXT,
    pet TEXT,
    zodiac TEXT,
    isNewHere INTEGER,
    isYourTypeLately INTEGER,
    isSecondChance INTEGER,
    isIncomingLike INTEGER,
    incomingComment TEXT,
    incomingLikeType TEXT,
    photosJson TEXT,            -- JSON array of image URLs
    promptsJson TEXT,           -- JSON array of CandidatePromptItem objects
    lastUpdatedTimestamp INTEGER
);
```

### 2.2 Archival Operations
- **`upsertCandidate(profile)`**: Inserts or updates an individual candidate profile without overwriting existing rating annotations or custom comments.
- **`upsertBatch(profiles)`**: Executes atomic batch updates inside a SQLite transaction (`beginTransaction` / `endTransaction`) for fast synchronizations.
- **`updateRating(userId, status, comment)`**: Updates the local review state of a candidate (e.g. marking as Passed, Liked, or Bookmarked).
- **`searchCandidates(query, facetFilter)`**: Performs multi-token filtering across names, occupations, colleges, prompts, and demographics.

---

## 3. Root Fallback Extractor (`SuStorageReader.kt`)

When running in companion mode without direct Xposed framework injection into the target process, [SuStorageReader.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/data/SuStorageReader.kt) uses root privilege (`su`) to inspect Hinge:

### 3.1 Shared Preferences Extraction
1. Executes `su -c 'ls -1 /data/data/co.hinge.app/shared_prefs'` to enumerate active XML files.
2. Extracts and parses:
   - `default.xml`: Core account identifiers, first name, last active timestamps.
   - `unhinge_insights.xml`: Telemetry flags and tracking IDs.
3. Parses XML using `XmlPullParser` into strongly-typed primitives (`boolean`, `string`, `int`, `long`, `float`).

### 3.2 Database Duplication & Querying
1. Executes `su -c 'cp /data/data/co.hinge.app/databases/db /data/data/io.github.s1ddhants1.unhinge/cache/hinge_dump.db'`.
2. Changes permissions: `su -c 'chmod 666 /data/data/io.github.s1ddhants1.unhinge/cache/hinge_dump.db'`.
3. Opens the temporary database using `SQLiteDatabase.openDatabase(..., OPEN_READONLY)` to read candidate profiles and feed queues safely outside of Hinge's process locks.

---

## 4. Data Models & Serialization (`CompanionModels.kt`)

Key models defined in [CompanionModels.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/model/CompanionModels.kt):

### `CachedCandidateProfile`
```kotlin
@Serializable
data class CachedCandidateProfile(
    val userId: String,
    val firstName: String,
    val age: Int,
    val height: Int,
    val hometown: String,
    val location: String,
    val jobTitle: String,
    val datingIntention: String,
    val relationshipType: String,
    val religion: String,
    val ethnicity: String,
    val isSelfieVerified: Boolean,
    val isCircleMember: Boolean,
    val photos: List<String> = emptyList(),
    val prompts: List<CandidatePromptItem> = emptyList(),
    val isStandout: Boolean = false,
    val isDiscover: Boolean = true,
    val isLiveInFeed: Boolean = true,
    val school: String = "",
    val employer: String = "",
    val politics: String = "",
    val smoking: String = "",
    val drinking: String = "",
    val marijuana: String = "",
    val drugs: String = "",
    val kids: String = "",
    val familyPlans: String = "",
    val pet: String = "",
    val zodiac: String = "",
    val isNewHere: Boolean = false,
    val isYourTypeLately: Boolean = false,
    val isSecondChance: Boolean = false,
    val isIncomingLike: Boolean = false,
    val incomingComment: String = "",
    val incomingLikeType: String = "",
    val lastActiveStatusId: Int? = null // 1 = Active now, 2 = Active today
)
```

### `CandidatePromptItem`
```kotlin
@Serializable
data class CandidatePromptItem(
    val question: String,
    val answer: String
)
```

### `CompleteHingeData`
Aggregated container used by the companion app dashboard:
- `isRootGranted: Boolean`
- `activeCandidateCount: Int`
- `candidates: List<CachedCandidateProfile>`
- `telemetry: RawHingeTelemetry`
- `sharedPrefs: List<RawPrefFile>`

---

## 5. Backup, Export & Restore Systems (`BackupRestoreManager.kt`)

[BackupRestoreManager.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/data/BackupRestoreManager.kt) enables lossless JSON-based export and import of all companion configuration preferences and historical candidate dossiers via Android's Storage Access Framework (SAF).

### 5.1 Bundle Schema: `UnhingeBackupBundle`
```kotlin
@Serializable
data class UnhingeBackupBundle(
    val version: Int = 1,
    val app: String = "Unhinge",
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: UnhingeSettingsBackup? = null,
    val candidates: List<CachedCandidateProfile>? = null
)
```

### 5.2 Functional Capabilities
- **Complete Backup**: Bundles both `UnhingeSettingsBackup` (telemetry shields, AI wingman configuration, appearance flags) and the complete `archived_candidates` SQLite table.
- **Selective Export**: Supports discrete exports for settings-only and candidate-archives-only.
- **Resilient Restoration**: `importFromJson()` performs transactional upserts (`CONFLICT_REPLACE`) into `CandidateArchiveDb`, flushes restored settings to fallback SharedPreferences and LibXposed remote preferences, and ignores unknown forward-version keys without failure.
- **Archive Pruning**: Integrated UI dialog allows safe bulk pruning of local candidate records with instant ViewModel state invalidation.

