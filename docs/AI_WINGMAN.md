# AI Prompt Wingman Architecture — Unhinge

This document provides a technical breakdown of the **AI Prompt Wingman** subsystem in Unhinge, detailing in-app overlay injection, touch physics, real-time candidate SQLite extraction, OpenRouter/OpenAI streaming integration, and prompt engineering.

---

## 1. Subsystem Architecture

```
+-----------------------------------------------------------------------------------------+
|                                    Target Process                                       |
|                                   (co.hinge.app)                                        |
|                                                                                         |
|  [AppActivity.onResume]                                                                 |
|         |                                                                               |
|         v                                                                               |
|  [HostAppAiFab]                                                                         |
|         |                                                                               |
|         +---> DecorView Injection (Floating Action Button with snap-to-edge physics)    |
|         |                                                                               |
|         +---> User Taps FAB                                                             |
|                    |                                                                    |
|                    v                                                                    |
|         [HostCandidateReader]                                                           |
|                    |                                                                    |
|                    +---> Reads /data/data/co.hinge.app/databases/db (OPEN_READONLY)      |
|                    +---> Extracts active candidates, photos, answers, demographics      |
|                    |                                                                    |
|                    v                                                                    |
|         [HostAppAiSheetContent] (Compose Dialog with ViewTree Owner Bridging)           |
|                    |                                                                    |
|                    +---> User Selects Candidate Prompt                                  |
|                    |                                                                    |
|                    v                                                                    |
|         [AiWingmanHelper] (StateFlow Coordinator & In-Memory Cache)                         |
|                    |                                                                    |
|                    +---> [OpenRouterStreamingService] (Server-Sent Events SSE Stream)   |
|                    |     OR                                                             |
|                    +---> [OpenRouterService] (JSON-Schema Completion Engine)            |
|                    |     OR                                                             |
|                    +---> [DeepLService] (DeepL Free/Pro Translation Engine)             |
|                    |                                                                    |
|                    v                                                                    |
|         [Streaming Suggestions Rendered in Compose Sheet]                               |
|                    |                                                                    |
|                    +---> One-Tap Clipboard Copy                                         |
+-----------------------------------------------------------------------------------------+
```

---

## 2. In-App Action Button (`HostAppAiFab.kt`)

[HostAppAiFab.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/hook/ui/HostAppAiFab.kt) provides the visual entry point inside Hinge (`co.hinge.app`).

### 2.1 Native Visual Integration & Symmetrical Positioning
1. **Design System Matching**: Visually identical to Hinge's native circular action buttons (such as the pass "X" button):
   - 60dp circular container with 16dp elevation (`w66.f85026c`).
   - Clean white (`#FFFFFF`) surface in light mode, `#1A1A1A` in dark mode.
   - 1dp subtle border stroke (`#EBEBEB` / `#2A2A2A`).
   - Pure black (`#1A1A1A`) in light mode / white (`#FFFFFF`) in dark mode AI Sparkle icon centered (28dp).
   - 20dp margin (`b5h.f4820j`) matching Hinge's Design System tokens.
2. **Horizontal Mirroring**: Positioned on the exact same vertical level as Hinge's pass "X" button (`location[1] + discoverView.height - fabSize - 20dp`), but mirrored to the right edge of the screen (`location[0] + discoverView.width - fabSize - 20dp`).
3. **Discover-Only Visibility**:
   - Monitored dynamically via reflective examination of `supportFragmentManager` fragments (`DiscoverRootFragment` / `DiscoverFragment`).
   - Automatically becomes visible exclusively when the Discover feed is active, and hides instantly (`View.GONE`) when navigating to Standouts, Likes You, Messages, Chats, Profile, or Settings.
4. **Current Candidate Focus**:
   - `HostCandidateReader.readCurrentDiscoverCandidate` queries `discover_subject` candidates excluding those in `pending_ratings`.
   - Limits AI openers and candidate dossiers strictly to the active candidate currently visible on screen.

---

## 3. Host ViewTree Owner Bridging

Rendering Jetpack Compose dynamically inside a non-Compose host activity requires bridging AndroidX ViewTree ownership. [HostAppAiFab.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/hook/ui/HostAppAiFab.kt) dynamically registers owners before `dialog.setContentView(composeView)`:

```kotlin
ViewTreeLifecycleOwner.set(decor, activity as LifecycleOwner)
ViewTreeViewModelStoreOwner.set(decor, activity as ViewModelStoreOwner)
ViewTreeSavedStateRegistryOwner.set(decor, activity as SavedStateRegistryOwner)
```

This guarantees:
- Lifecycle-aware coroutine scopes (`LaunchedEffect`, `rememberCoroutineScope`) execute properly.
- State preservation across dialog orientation changes.
- Compose rendering operates within the host window context without requiring host app code modifications.

---

## 4. In-Process Candidate Extraction (`HostCandidateReader.kt`)

[HostCandidateReader.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/data/HostCandidateReader.kt) queries `/data/data/co.hinge.app/databases/db` in `OPEN_READONLY` mode asynchronously on `Dispatchers.IO` to prevent main UI thread lockups during sheet invocation:

1. **Question Dictionary**: Reads `SELECT id, prompt FROM prompts` into `promptTitles`.
2. **Subject Answers**: Queries `SELECT userId, questionId, answerData FROM subject_answers ORDER BY position ASC`.
   - Extracts answer text via regex: `Regex("\"response\"\\s*:\\s*\"([^\"]+)\")`.
3. **Subject Media**: Reads `SELECT userId, photoUrl FROM subject_media ORDER BY position ASC`.
4. **Discover Order**: Reads `SELECT userId FROM discover_subject ORDER BY batchId ASC, positionInBatch ASC`.
5. **Incoming Likes**: Reads `SELECT subjectId, initiatedWith FROM impressions`.
6. **Profile Join**: Queries `SELECT userId, firstName, age, height, hometown, location, jobTitleText, datingIntentionText, relationshipTypeText, religionText, ethnicitiesText, selfieVerified, circleMember, educationHistoryText, employmentHistory, politicsText, smoking, drinking, marijuana, drugs, kids, familyPlans, pet, zodiacSign, didJustJoin FROM profiles`.

Constructs a rich [CachedCandidateProfile](../app/src/main/java/io/github/s1ddhants1/unhinge/model/CompanionModels.kt) with complete demographics, photos, and answers.

---

## 5. Dating Wingman AI Pipeline Architecture

The AI pipeline in Unhinge is designed for high-performance dating prompt suggestions, conversation openers, and playful banter, communicating with LLMs via OpenRouter/OpenAI-compatible APIs and streaming responses.

### 5.1 Pipeline Components

1. **[AiWingmanHelper.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/AiWingmanHelper.kt)**:
   - Central coordinator orchestrating requests, wingman tone modes, caching, and state transitions.
   - Exposes `status: StateFlow<WingmanStatus>` (`Idle`, `Generating`, `Success`, `Error`).
   - Exposes `suggestedReplyFlow: MutableStateFlow<String?>` for progressive UI streaming updates per prompt.
   - Exposes `hasActiveSuggestions: StateFlow<Boolean>` for reactive UI indicators.
   - Manages concurrent in-memory caching (`replyCache`) preventing redundant network calls.
   - Supports tailored wingman opener tones: Witty & Playful, Charming & Flirty, Intellectual & Curious, Sarcastic & Bold.

2. **[OpenRouterStreamingService.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/OpenRouterStreamingService.kt)**:
   - OkHttp-backed Server-Sent Events (SSE) streaming client.
   - Emits structured `StreamChunk` events (`Content(delta)`, `Complete(fullText)`, `Error(throwable)`).
   - Ingests streaming chunks, parses JSON deltas, validates JSON-schema line outputs, and falls back to text parsing if needed.

3. **[OpenRouterService.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/OpenRouterService.kt)**:
   - Synchronous/standard HTTP client for OpenRouter and OpenAI-compatible API providers.
   - Generates charismatic, tailored opening lines matching the selected candidate prompt, bio answers, and tone.
   - Employs strict JSON-schema requests (`lines` array structure) with temperature, system instructions, and provider routing.
   - Extracts detailed error diagnostics (`apiErrorMessage()`) when providers return rate limits or authentication failures.

4. **[DeepLService.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/DeepLService.kt)**:
   - Optional DeepL REST API client supporting both DeepL Free (`:fx` API domain auto-detection to `api-free.deepl.com`) and DeepL Pro (`api.deepl.com`).
   - Built-in exponential backoff retry for transient 5xx server errors.

5. **[PromptEntry.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/PromptEntry.kt)**:
   - Clean data carrier encapsulating prompt text and reactive `suggestedReplyFlow` for candidate suggestions.

6. **[Timber.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/Timber.kt)**:
   - Lightweight, zero-dependency Android `Log` proxy allowing structured logging without external runtime dependencies.

---

## 6. Native Hinge Bottom Sheet UI (`HostAppAiSheetContent.kt`)

[HostAppAiSheetContent.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/hook/ui/HostAppAiSheetContent.kt) delivers a seamless in-app bottom sheet modeled strictly after Hinge's editorial design system:

1. **Editorial Serif Typography**:
   - Prompt answers, candidate names, and card headlines rendered in high-end `FontFamily.Serif` (21-24sp).
   - Prompt questions rendered in uppercase, tracked sans-serif (11-12sp, letter spacing 0.8sp).
2. **Authentic Prompt Cards & Draft Bubbles**:
   - Individual candidate prompts styled as large rounded cards (22dp corner radius) with subtle hairline borders.
   - Tailored conversation openers embedded directly within each card as a drafted comment bubble (`#F4F4F2` in light mode, `#262626` in dark mode).
   - Per-prompt refresh button allowing instant single-prompt regeneration.
3. **Capsule Pill Controls**:
   - Persona and tone selection via capsule pill tags (`RoundedCornerShape(50)`).
   - High-contrast black/white pill buttons matching Hinge's native like and comment submission controls.
4. **Haptic & Visual Discipline**:
   - Zero emojis across all UI copy, badges, toasts, and comments.
   - Zero haptic feedback vibrations for clean, distraction-free interactions.

