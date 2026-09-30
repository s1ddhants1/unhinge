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
3. **Candidate Screen Visibility & Standouts Discrimination**:
   - Evaluates active feed and candidate presence via unified screen scan (`scanScreen`).
   - Automatically visible on active candidate profile screens across Discover, individual Standout profiles, and Likes You profiles, while hiding instantly (`View.GONE`) when navigating to Messages, Chats, Profile, or Settings.
   - **Standouts Overview Suppression**: Specifically hides the FAB on the Standouts carousel overview (`Send a Rose` / `roses remaining` indicators present without a `Back` navigation button), while dynamically displaying it as soon as the user taps into any individual Standout candidate dossier (`hasBack || hasProfile`).
4. **In-Process Compose Virtual Node Traversal & Accessibility Activation**:
   - Jetpack Compose (`AndroidComposeView`) suppresses virtual accessibility node generation when `AccessibilityManager.isEnabled` is `false`.
   - Unhinge hooks `AccessibilityManager.isEnabled()` early in bytecode to return `true`, and invokes `ensureAccessibilityEnabled` to notify all `AccessibilityStateChangeListener` instances.
   - Traverses Compose's in-process `AccessibilityNodeProvider` (`HOST_VIEW_ID`) and child virtual IDs via `AccessibilityNodeInfo.getVirtualDescendantId()` without requiring an external AccessibilityService connection.
   - Accurately detects the active bottom navigation tab via the selected container's horizontal center-x ratio within the bottom nav band.
   - Operates 100% via public framework contracts (`View`, `TextView`, `AccessibilityNodeInfo`, `AccessibilityNodeProvider`, `AccessibilityManager`), strictly avoiding obfuscated ProGuard/R8 class references (`defpackage.*`) to ensure resilience across Hinge updates.
5. **Multi-Tier Semantic Candidate Resolution**:
   - `HostCandidateReader.readTargetCandidate(context, screenClues)` scores all active candidate profiles loaded from Hinge's local SQLite database (`prompts`, `subject_answers`, `standouts_content`, `discover_subject`).
   - Scoring algorithm (`scoreCandidate`):
     - **Prompt Answers**: +1000 points for exact substring or container matches; +600 points for partial multi-word overlap (>= 50% of significant words).
     - **Prompt Questions**: +200 points for matching prompt title strings.
     - **Name Patterns**: +800 points for photo descriptions (`"[Name]'s photo"`) or skip button labels (`"Skip [Name]"`).
     - **Exact Name & Age**: +250 points for standalone name match, +400 points for name + age match (`"[Name], [Age]"`).
     - **Demographics**: +150 points for job title, +150 points for school, +100 points for location match.
     - **Context Alignment**: +100 bonus for matching feed type; -100 penalty for cross-feed mismatch (e.g., discover candidate scored on standouts page).
   - Graceful Contextual Fallback: If no on-screen text matches with a positive score, defaults to the first candidate in the active context (e.g. first standout candidate on Standouts page, first incoming like on Likes You page, or current Discover candidate).
6. **Prompt Answer Unescaping (`parsePromptAnswer`)**:
   - Hinge stores prompt responses in SQLite `subject_answers` and `player_answers` as serialized JSON payloads (`{"response":"Line 1\n\nLine 2"}`).
   - `HostCandidateReader.parsePromptAnswer` parses the JSON via `JSONObject` and unescapes literal escaped control characters (`\n`, `\r`, `\t`, `\"`, `\\`), ensuring multiline prompts render with genuine paragraph linebreaks instead of raw `\n` character literals.

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
   - Central coordinator orchestrating requests, tunable generation parameters, caching, and state transitions.
   - Exposes `status: StateFlow<WingmanStatus>` (`Idle`, `Generating`, `Success`, `Error`).
   - Exposes `suggestedReplyFlow: MutableStateFlow<String?>` for progressive UI streaming updates per prompt.
   - Exposes `hasActiveSuggestions: StateFlow<Boolean>` for reactive UI indicators.
   - Manages concurrent in-memory caching (`replyCache`) preventing redundant network calls.
   - Accepts caller-supplied `temperature`, `topP`, and `maxTokens` (defaults `0.85` / `0.95` / `250`, adjustable via Settings → Model Parameters) instead of fixed tone presets.

2. **[OpenRouterStreamingService.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/OpenRouterStreamingService.kt)**:
   - OkHttp-backed Server-Sent Events (SSE) streaming client.
   - Emits structured `StreamChunk` events (`Content(delta)`, `Complete(fullText)`, `Error(throwable)`).
   - Ingests streaming chunks, parses JSON deltas, validates JSON-schema line outputs, and falls back to text parsing if needed.

3. **[OpenRouterService.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/OpenRouterService.kt)**:
   - Synchronous/standard HTTP client for OpenRouter and OpenAI-compatible API providers.
   - Generates charismatic, tailored opening lines matching the selected candidate prompt and bio answers.
   - Employs strict JSON-schema requests (`lines` array structure) with caller-supplied temperature, top-P, max tokens, system instructions, and provider routing.
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

1. **Authentic Hinge Brand Typography (`HingeFonts.kt`)**:
   - Exact brand typefaces matching [Hinge Brand Guidelines](https://hinge.co/en-gb/brand-resources):
     - **Modern Era** (Family Type): Used for the sheet architecture:
       - Headlines, candidate names, age, avatar initial, and section badges: `modernEraBold` (Bold, 20-21sp).
       - Question titles: `modernEraBold` (Bold, 15sp, natural casing matching Hinge's `style/HingeQuestion`).
       - Drafted comment bubble openers and custom AI responses: `modernEraMedium` (Medium, 15-16sp).
       - Subtitles, captions, helper notices, and interactive "Ask AI" input: `modernEraRegular` (Regular, 13-14sp).
     - **Tiempos Headline** (Kris Sowersby / Klim Type Foundry):
       - Candidate prompt answers: `tiemposRegular` rendered at native Regular weight (`FontWeight.Normal`), 26sp, 34sp line height matching Hinge's live profile layout.
       - Candidate bio fallback text: `tiemposRegular` (24sp, 32sp line height, `FontWeight.Normal`).
   - **Cross-Process Dynamic Font Resolver**: Injected Compose overlays run within the host `Activity` context (`co.hinge.app.ui.AppActivity`). Referencing compile-time R constants from the module causes `Resources$NotFoundException`. `HingeFonts.kt` dynamically resolves host runtime font resources via `context.resources.getIdentifier(fontName, "font", context.packageName)`, falling back to the companion app package context and finally graceful fallback typefaces with `ConcurrentHashMap` caching.
2. **Authentic Prompt Cards & Multi-Opener Draft Bubbles**:
   - Individual candidate prompts styled as large rounded cards (22dp corner radius) with subtle hairline borders.
   - Tailored conversation openers embedded directly within each card as a drafted comment bubble (`#F4F4F2` in light mode, `#262626` in dark mode).
   - **Multi-Opener History & Stepper Navigation**:
     - Tapping the Refresh/Regenerate button triggers fresh generation with `forceRefresh = true` bypassing session caching.
     - Feeds `avoidReplies` into LLM prompts at elevated creativity temperature (0.85) to guarantee distinct, fresh alternatives on every regeneration.
     - Each prompt accumulates generated openers into `PromptEntry.repliesFlow`, displaying an interactive stepper (`< 2 of 3 >`) with previous/next navigation arrows.
     - Single-tap "Copy" button automatically copies the currently active opener in the stepper.
     - Per-entry loading state (`isGeneratingFlow`) displays a spinning indicator on the prompt's refresh button without disrupting other cards or blanking existing text.
3. **Interactive "Ask AI" Input Bar**:
   - Replaces static bulk-generation buttons with an interactive text input with placeholder `"Ask AI"`.
   - Floating rounded capsule with soft-keyboard elevation (`imePadding` + `SOFT_INPUT_ADJUST_RESIZE`).
   - Injects rich candidate profile context (demographics, verified status, prompt Q&As, bio) and the user's Model Parameters settings (temperature, top-P, max tokens) into custom queries.
   - Streams custom responses live via `OpenRouterStreamingService.streamChat` / `AiWingmanHelper.streamCustomChat`.
   - Renders interactive response cards (`CustomAiResponseCard`) supporting instant 1-tap clipboard copying, regeneration, and dismissal.
4. **Capsule Action Controls**:
   - High-contrast capsule buttons (`RoundedCornerShape(50)`) matching Hinge's native like and comment submission controls (Copy, regenerate, Ask AI send).
5. **Interactive Swipe-to-Dismiss Architecture**:
   - Native bottom sheet physics matching Android's `BottomSheetBehavior`:
     - Real-time touch tracking via `Animatable(0f)` offset and `Modifier.draggable` attached to the drag handle, candidate identity header, and empty state containers.
     - `NestedScrollConnection` integration: downward overscroll on the `LazyColumn` when at the top (`firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0`) pulls the sheet down directly, and upward drags collapse the offset before list content scrolls.
     - Release physics: drags beyond threshold (>25% sheet height) or downward flings (>1000px/s) smoothly animate the sheet off-screen before invoking `onDismiss()`; sub-threshold drags spring back up to `0dp`.
     - Close "X" button triggers the identical downward exit animation prior to dismissal.
     - Window-level integration: `dialog.setCanceledOnTouchOutside(true)` and `android.R.style.Animation_InputMethod` for native slide-up entrance and outside-touch dismissal.
6. **Clean Header & Visual Discipline**:
   - Candidate identity header displays candidate first name and age in Modern Era Bold, with photo thumbnail and subtitle (work, location) without selfie verification badges for an uncluttered layout.
   - Opener action button cleanly labeled "Copy" with temporary confirmation state.
   - Zero emojis across all UI copy, badges, toasts, and comments.
   - Zero haptic feedback vibrations for clean, distraction-free interactions.
7. **System Bar & Window Isolation**:
   - `UnhingeTheme` strictly restricts mutating `isAppearanceLightStatusBars` and `isAppearanceLightNavigationBars` to the companion app process (`view.context.packageName == "io.github.s1ddhants1.unhinge"` with `setSystemBars = true`).
   - `HostAppAiFab.showAiSheet` passes `setSystemBars = false` and snapshots the host activity's status bar appearance on display, restoring it on dismiss to guarantee Hinge's status bar icons never turn white on a white background.


