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

### 2.1 Native Pass Button Grounded Injection & Symmetrical Mirroring
1. **Pass "X" Button Grounding (`findPassButtonBounds`)**:
   - Rather than overlaying arbitrarily or estimating margins, Unhinge dynamically traverses Compose's `AccessibilityNodeProvider` on the fragment's active `ComposeView` (`HOST_VIEW_ID`) to locate Hinge's native Pass "X" button composable (`contentDescription.startsWith("Skip")`, `left < 360`, `top > 800`).
   - Retrieves exact on-screen bounds (`Rect(left, top, right, bottom)`), yielding sub-pixel dimensions (e.g. 186x186px / 62dp diameter).
2. **Horizontal Mirroring & ContentFrame Injection**:
   - Injected directly into `(activity.findViewById(android.R.id.content) as? ViewGroup)` (`ContentFrameLayout`). Bypasses `AbstractComposeView` (which throws `UnsupportedOperationException: Cannot add views to ComposeView`) and `FragmentContainerView` (which throws `IllegalStateException`).
   - Symmetrical right-edge mirroring:
     - `fabSize = passRect.width()`
     - `targetX = containerWidth - (passRect.left - containerScreenX) - fabSize`
     - `targetY = passRect.top - containerScreenY`
   - AI FAB adopts identical diameter, elevation, and vertical plane as the Pass "X" button.
3. **Recomposition Tracking via `OnPreDrawListener`**:
   - Attaches a throttled `OnPreDrawListener` (100ms interval) to `decor.viewTreeObserver` in addition to layout listeners.
   - Intercepts Jetpack Compose canvas recompositions and profile card swaps immediately before drawing, eliminating flicker and position drift.
4. **Complete Contextual Suppression**:
   - Controls are automatically suppressed (`View.GONE`) when not on an active candidate profile (Standouts overview carousel, Matches tab, Profile tab, or empty feed).
   - Dynamic reappearance (`View.VISIBLE`) when returning to Discover or entering an active candidate card.
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

### 2.2 In-App Available Likes Counter & Quota Pill
- **Real-Time Storage Interception (`HostLikesReader.kt`)**: Reads remaining likes count (`localAvailableLikes` / `apiAvailableLikes`) and roses count (`localAvailableSuperlikes` / `apiAvailableSuperLikes`) from Hinge's private `default.xml` SharedPreferences in-process.
- **Instant Reactive Updates**: Binds an Android `OnSharedPreferenceChangeListener` to automatically refresh the badge immediately upon user like interactions, eliminating polling lag.
- **Overlay Pill Placement**: Docked directly above the action button at `y = targetY - pillHeight - 6dp` (or positioned at the action button slot when FAB is disabled), styled with authentic Hinge tokens (`createNavPillDrawable`, 26dp height, 12dp elevation).
- **Authentic Hinge Vector Icons (`HingeIcons.kt`)**: Extracted directly from Hinge's native vector assets (`ic_heart_full` and `ic_rose_branded_small`):
  - Vibrant coral (`#ED5564`) under normal quota.
  - Warning amber (`#FFA000`) when quota is low (<= 2 likes remaining).
  - Alert red (`#E53935`) when exhausted (0 likes remaining).
  - Secondary rose counter with authentic rose icon (`ic_rose_branded_small`) when roses are available.
- **Interactive Tooltip**: Tapping the pill displays a summary toast with remaining likes and roses.
- **Bottom Sheet Integration**: The AI Wingman sheet header also features a persistent likes chip next to the dismiss button.

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

1. **[AiHttpClient.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/AiHttpClient.kt)**:
   - Centralized network provider configuring a shared `OkHttpClient` instance with optimized HTTP connection pooling (5 idle connections, 5-minute keep-alive) and shared Kotlinx `Json` parser (`ignoreUnknownKeys = true`, `isLenient = true`).
   - Centralizes transient HTTP failure detection (`isTransientHttpCode`, `isTransientFailure`) and retry policies across all LLM clients.

2. **[LlmClient.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/LlmClient.kt) & [LlmClientRegistry.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/LlmClientRegistry.kt)**:
   - Polymorphic client abstraction declaring `generate(request: WingmanLlmRequest)`, `streamGeneration(request: WingmanLlmRequest)`, and `streamChat(request: ChatLlmRequest)`.
   - `LlmClientRegistry` maps `LlmProtocol` wire specifications (`OPENAI_CHAT_COMPLETIONS`, `OPENAI_RESPONSES`, `ANTHROPIC_MESSAGES`, `GOOGLE_GEMINI`) to corresponding client implementations without monolithic branching.

3. **[OpenAiChatClient.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/OpenAiChatClient.kt)**:
   - Unified Chat Completions engine implementing `LlmClient` for OpenRouter, DeepSeek, and standard OpenAI-compatible endpoints.
   - Combines both Server-Sent Events (SSE) streaming and standard structured JSON line completion in a single cohesive, maintainable client.
   - [OpenRouterService.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/OpenRouterService.kt) and [OpenRouterStreamingService.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/OpenRouterStreamingService.kt) serve as thin backward-compatible delegates.

4. **[AiWingmanHelper.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/AiWingmanHelper.kt)**:
   - Central coordinator orchestrating requests, tunable generation parameters, caching, and state transitions.
   - Delegates endpoint resolution to `LlmProtocolRouter.kt` and thread-safe reply caching to `AiWingmanCache.kt`.
   - Exposes `status: StateFlow<WingmanStatus>` (`Idle`, `Generating`, `Success`, `Error`).
   - Exposes `hasActiveSuggestions: StateFlow<Boolean>` for reactive UI indicators.
   - Provides `steerOpener()`: Launches real-time streaming follow-up chat refinements on specific drafted openers using candidate profile details, prompt text, and user steering direction.

5. **[PromptEntry.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/PromptEntry.kt)**:
   - Clean data carrier encapsulating prompt text, `suggestedReplyFlow`, multi-reply stepper history (`repliesFlow`, `activeReplyIndexFlow`), and follow-up conversation history (`followUpMessagesFlow`, `isSteeringFlow`).
   - Tracks `OpenerChatMessage` records (`isUser`, `text`, `timestamp`) for iterative opener steering dialogues.

4. **[LlmProtocol.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/LlmProtocol.kt)**:
   - Wire-protocol enum (`openai-chat-completions`, `google-openai`); brand labels only decide endpoint/model defaults.
   - `infer(provider, apiKey)` resolves the protocol explicitly — a pasted Gemini key (`AIzaSy…`) routes to Google's OpenAI-compatible bridge even when the brand selector lags.

5. **[PromptRepository.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/PromptRepository.kt)**:
   - Dynamic prompt loading, template management, and OTA synchronization hub.
   - Decouples all prompt templates, rules, archetypes, and negative constraints from Kotlin code.
   - Loads templates with multi-layered fallback: Companion/Xposed `PreferencesManager` -> Android `assets/prompts/` -> Local repo files -> Builtin safety fallback.
   - Formats opener user prompts (`formatOpenerUserPrompt`), opener system prompts (`formatOpenerSystemPrompt`), and Ask AI profile prompts (`formatAskAiSystemPrompt`) with variable substitution (`{prompts}`, `{lineCount}`, `{profile}`, `{avoid}`, `{custom_instructions}`).
   - Supports Over-The-Air (OTA) template updates via `fetchRemoteTemplate` from GitHub or custom URLs with zero app redeployment.

6. **[WingmanPrompts.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/WingmanPrompts.kt)**:
   - Zero hardcoded prompt rules or strings; delegates formatting and template resolution directly to `PromptRepository`.
   - Opener generation encodes the 5 empirically verified human dating archetypes (*Mock Skeptic / Playful Tease*, *Niche Item Callback*, *Relatable Micro-Debate*, *Chaos / Backstory Inquirer*, *Collaborative Condition*) documented in **[PROMPT_RESEARCH_AND_TYPOLOGY.md](PROMPT_RESEARCH_AND_TYPOLOGY.md)**.
   - Replaces artificial word/character limits with **adaptive energy & length matching** (punchy for short answers, proportional warmth/depth for stories and detailed takes).
   - Strictly enforces negative constraints: bans AI preambles (*"bold claim"*, *"plot twist"*), throat-clearing affirmations, synthetic slang (*"elite"*, *"sus"*), pseudo-observational snark (*"doing heavy lifting"*, *"bet you"*), and defensive job application / wishlist tropes (*"sounds like a full time job"*, *"submit my resume"*).
   - Enforces strict topical isolation (never dragging unrelated profile location or job fields into prompt openers).

7. **[PromptEntry.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/PromptEntry.kt)**:
   - Clean data carrier encapsulating prompt text and reactive `suggestedReplyFlow` for candidate suggestions.

8. **[Timber.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/Timber.kt)**:
   - Lightweight, zero-dependency Android `Log` proxy allowing structured logging without external runtime dependencies.

9. **[ZenRouter.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/ZenRouter.kt)**:
   - Canonical routing hub and OpenCode client emulator for Zen endpoints (`/inference/v1/responses`, `/inference/v1/chat/completions`, `/inference/v1/messages`, `/inference/v1/models/<id>`).
   - Recognizes zero-cost OpenCode Zen free models requiring zero API keys and automatically defaulting `Authorization: Bearer public`:
     - `space-bunny-free` (default high-capability reasoning model)
     - `cat-jam-free`
     - `deepseek-v4.1-flash-free`
     - `step-5-preview-free`
     - `grok-code`
     - `muse-spark-1.3-contributor-free` (Muse Spark 1.3 Free; aliases `muse-spark-1.3-free`)
     - `big-pickle`
     - `fledge-alpha-free`
     - `ling-3.1-flash-free`
     - `longcat-2.5-preview-free`
     - `mimo-v2.6-flash-free`
     - `mimo-v2.5-free`
     - `nemotron-3-ultra-free`
     - `nemotron-3.5-lightning-free`
   - Generates reverse-engineered OpenCode timestamp-encoded session IDs (`ses_` + 12 hex chars `~((now * 4096) + p)` + 14 random base62 chars), W3C traceparents, client header (`x-opencode-client: cli`), project identifier (`x-opencode-project: fbfbb8ecca77fbf4f585927700f1baa979cb6a94`), session affinity headers (`x-opencode-session-id`, `X-Session-Id`, `x-session-id`, `x-session-affinity`, `x-opencode-session`), and dynamic user-agent (`opencode/latest/<version>/cli`, auto-detected with fallback to `2.0.30`).
   - Emulates the full 12 OpenCode CLI tools (`edit`, `glob`, `grep`, `question`, `read`, `shell`, `skill`, `subagent`, `webfetch`, `websearch`, `write`, `execute`) in `DefaultZenChatTools` (Chat Completions format) and `DefaultZenResponsesTools` (Responses format with top-level `name`/`parameters`) to satisfy OpenCode's gateway agent validation on keyless free-tier requests.

10. **[OpenAiResponsesService.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/OpenAiResponsesService.kt)**:
   - Client for OpenAI Responses API (`POST /inference/v1/responses`) utilized by Muse Spark, GPT, and Grok on Zen.
   - For free-tier models (`muse-spark-1.3-contributor-free`), automatically injects `DefaultZenResponsesTools`, `prompt_cache_key = sessionId`, `include = ["reasoning.encrypted_content"]`, and consumes streaming SSE deltas (`stream = true`) to comply with OpenCode gateway free tier constraints.
   - Handles `instructions` + `input` structure, parses `output[].content[].text` (or streaming `response.output_text.delta`), and propagates reasoning effort configuration (`reasoning: { effort: reasoningEffort }`).

11. **[AnthropicMessagesService.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/AnthropicMessagesService.kt)**:
   - Client for Anthropic Messages API (`POST /inference/v1/messages`) used by Claude and Qwen on Zen.
   - Handles `system` + `messages` format, `anthropic-version: 2023-06-01`, and `x-api-key` alongside Bearer authorization.

11. **[GoogleGeminiService.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/GoogleGeminiService.kt)**:
   - Client for Google Gemini native API (`POST /inference/v1/models/<id>:generateContent` and `:streamGenerateContent`) used for Gemini models on Zen.
   - Handles `system_instruction` + `contents[].parts[].text` and parses `candidates[].content.parts[].text`.

### 5.2 OpenCode Zen Free Model Emulation Mechanics

1. **Free-Tier Authentication Gate & 12-Tool Emulation**:
   - Zen's reverse proxy enforces strict agent validation on free-tier model requests. Calling free models with standard generic headers or partial toolsets results in `HTTP 403 FreeTierError: OpenCode's free tier can only be used from within OpenCode`.
   - The validation checks:
     - `Authorization`: Must be `Bearer public` for free-tier models; any user-saved API key from other providers is strictly ignored on free models to prevent `AuthError: Invalid API key`. Paid Zen models use the configured API key.
     - `tools`: Keyless free-tier completions must declare the complete suite of 12 OpenCode CLI tools (`DefaultZenChatTools`) in the request payload. Incomplete or single-tool requests are rejected by the proxy with `FreeTierError`.
     - `x-opencode-client`: `cli`.
     - `x-opencode-project`: Stable SHA-1 workspace hash (`fbfbb8ecca77fbf4f585927700f1baa979cb6a94`).
     - `x-opencode-session`: Must follow OpenCode's internal `XC(true)` encoding format within an active validity timestamp window.
     - `x-opencode-session-id`, `X-Session-Id`, `x-session-id`, `x-session-affinity`: Mirrored session ID across canonical and legacy header keys.
     - `User-Agent`: `opencode/latest/2.0.29/cli`.
     - `traceparent` & `b3`: W3C distributed trace context and B3 trace headers.
     - Zero leakage of OpenRouter attribution headers (`HTTP-Referer`, `X-Title`) to prevent Cloudflare/gateway detection.

2. **Session ID Bitwise Algorithm (`ZenRouter.generateSessionId`)**:
   - Formula reverse-engineered directly from OpenCode binary. The 6 timestamp bytes are emitted most-significant-first:
     ```kotlin
     val n = (System.currentTimeMillis() shl 12) + 1L
     val a = n.inv() // bitwise NOT
     // 6 hex bytes (12 hex characters), big-endian
     val hexPrefix = (0 until 6).joinToString("") { m ->
         String.format(Locale.US, "%02x", ((a shr (40 - 8 * m)) and 0xFF).toInt())
     }
     // 14 random base62 characters
     val randomSuffix = ByteArray(14).map { BASE62[(it.toInt() and 0xFF) % 62] }.joinToString("")
     return "ses_$hexPrefix$randomSuffix"
     ```

3. **Model-Specific Reasoning Architecture & Clamping (`ModelReasoningCatalog.kt`)**:
   - Reasoning capabilities and valid levels vary significantly across models and providers:
     - **Discrete Effort Models**:
       - `space-bunny-free`, `claude-sonnet-5`, `claude-opus-5`, `claude-fable-5-1`: `['low', 'medium', 'high', 'xhigh', 'max']`
       - `gpt-5.6-sol`, `gpt-5.6-terra`, `gpt-5.6-luna`, `gpt-6-sol`: `['none', 'low', 'medium', 'high', 'xhigh', 'max']`
       - `fledge-alpha-free`, `glm-5.3-flash`, `deepseek-v4-flash`: `['low', 'high', 'max']`
       - `gemini-3.8-flash`, `gemini-3.7-flash`, `gemini-flash-latest`, `o1`, `o3-mini`, `o4-mini`: `['low', 'medium', 'high']`
       - `muse-spark-1.3-contributor-free`, `muse-spark-1.3-free`, `muse-spark-1.3`: `['minimal', 'low', 'medium', 'high', 'xhigh']`
       - `gemini-3.6-flash`, `gemini-3.5-flash`, `gpt-5`: `['minimal', 'low', 'medium', 'high']`
       - `qwen3.8-flash`: `['low', 'medium', 'xhigh']`
       - `deepseek-v4-pro`: `['high', 'max']`
       - `mistral-medium-latest`, `mistral-small-latest`: `['none', 'high']`
     - **Toggle Models (Thinking On / Off)**:
       - `ling-3.1-flash-free`, `longcat-2.5-preview-free`, `kimi-k2.5`: discrete toggle (`['default', 'on', 'off']`).
     - **Budget Token Models**:
       - `claude-haiku-4-5`, `claude-haiku-4-5-20251001`: integer token budget (1024..32768 tokens) formatted as `thinking: { type: "enabled", budget_tokens: N }` with temperature set to 1.0.
     - **Fixed / Native Reasoning Models**:
       - `big-pickle`, `mimo-v2.6-flash-free`, `mimo-v2.5-free`, `nemotron-3-ultra-free`, `nemotron-3.5-lightning-free`, `minimax-m3`: model always reasons natively without user-configurable effort parameters.
     - **Unsupported Models**:
       - Standard non-reasoning models (`sonar`, `mistral-large-latest`, `claude-3-5-haiku`, `gemini-pro-latest`).
   - **Adaptive Settings UI**:
     - Tapping "Reasoning Effort" dynamically queries `ModelReasoningCatalog.getReasoningSupport` for the selected model.
     - Only displays valid options supported by the target model.
     - Displays an informational modal explaining built-in fixed reasoning or non-supported status when applicable.
   - **Intelligent Parameter Clamping & Sanitization**:
     - `ModelReasoningCatalog.sanitizeReasoningEffort(userEffort, model, provider)` clamps the user's preference to the closest level valid for the active model (e.g. `max` -> `high` on Gemini 3.8 Flash, `medium` -> `low` on Fledge Alpha Free).
     - Returns `null` for fixed or unsupported models, strictly omitting `reasoning_effort` / `reasoning` payloads to prevent upstream HTTP 400 Bad Request errors.
     - No artificial output token cap is sent on Chat Completions, Responses, or Gemini endpoints; Anthropic Messages still sends wire-required `max_tokens` (thinking budget + headroom, else a high default) since the API rejects requests without it.
     - Robust content parser in `OpenRouterService.parseGeneratedContent`: handles raw JSON strings, unwraps `JsonPrimitive` values, and strips markdown quotes/formatting (`**Opener**`, `> "..."`, `---`, `Why it works:`) so models like Space Bunny never fail with "Failed to parse response".

4. **Zero-Configuration Out-of-the-Box UX**:
   - `PreferencesManager` defaults to the `Zen` provider on `Consts.ZEN_DEFAULT_BASE_URL` (`https://opencode.ai/inference/v1/chat/completions`) / `Consts.ZEN_DEFAULT_MODEL` (`space-bunny-free`), so a fresh install generates openers and answers Ask AI queries without an account, key, or billing configuration.
   - `AiWingmanSettingsPage` groups all free models under `(Free - No Key Required)`, and clearly labels paid Zen models with `(Requires Zen key)`.
   - `AiWingmanHelper.isApiKeyRequired(provider, model)` is the single gate consumed by both the settings page and the in-app sheet (`HostAppAiSheetContent`); the Zen branch of `resolveEffectiveEndpoint` only honours a base URL as an override when it points at a genuine third-party proxy, so a stale URL from another provider can never hijack Zen routing.

5. **Autonomous Self-Maintaining Architecture (Dynamic Roster & User-Agent Auto-Updation)**:
   - **Dynamic Version Discovery & User-Agent Injection**:
     - Automatically queries public distribution channels (`https://registry.npmjs.org/@opencode/cli/latest` with fallback to `https://formulae.brew.sh/api/formula/opencode.json`) to discover the active released CLI version without requiring APK rebuilds.
     - Dynamically formats and injects `opencode/latest/<version>/cli` into all outgoing Zen requests.
     - Persists discovered version in `PreferencesManager.zenOpencodeVersion`.
   - **Live Model Roster Discovery & Dynamic Protocol Resolution**:
     - Dynamically fetches all live server-supported models directly from `https://opencode.ai/inference/v1/models`.
     - Auto-classifies free models (`cost == 0`, `-free` in model identifier, `big-pickle`, `grok-code`).
     - Auto-resolves protocol routing families (`claude-`, `qwen-`, `jev-`, `minimax-` -> Anthropic Messages; `gemini-` -> Google Gemini; `gpt-`, `muse-spark-`, `grok-` -> OpenAI Responses; standard -> OpenAI Chat Completions).
     - Dynamically updates the Settings UI model picker (`ZenRouter.allAvailableModels`), prioritizing free models at the top, and persisting cached rosters (`zenCachedFreeModels`, `zenCachedAllModels`).
   - **Multi-Tiered Trigger Engine**:
     - **Launch-Time Background Sync**: On application startup (`App.onCreate`), runs background synchronization with a 6-hour rate-limiting window.
     - **Manual Settings Sync**: An interactive "Sync OpenCode Gateway" row in AI Wingman Settings lets users refresh the catalog and version on-demand, with instant toast feedback.
     - **Reactive Self-Healing**: On any HTTP 401, 403, or 404 gateway response encountered during generation or streaming, `ZenRouter.onGatewayResponse` automatically launches an asynchronous force-sync guarded by `Mutex.withLock` to immediately adapt to server-side model deprecations or version rotations.

## 6. Native Hinge-Styled Inline Opener UI (`hook/ui/sheet/`)

Instead of rendering an intrusive modal bottom sheet that darkens 70% of the screen and hides candidate profiles, the AI Wingman adopts Hinge's native AI prompt feedback and analysis design language (as seen in Hinge's own profile prompt review):

1. **Inline Non-Modal Architecture (`InlineAiOpenerCard.kt`)**:
   - **Zero Dimming & Full Background Interactivity**: The inline card is embedded directly in `TAG_FAB_CONTAINER` attached to the host activity's decor/content view. It renders without window dimming (`dimAmount = 0f`), allowing users to scroll candidate profiles, view photos, and interact with Hinge while viewing opener suggestions.
   - **Automatic Controls Docking**: Dynamically docks right above the bottom controls (`passRect`, `chatInputRect`, or `bottomNavTop`), taking only ~180-220dp vertical height at the bottom of the screen.
   - **Toggleable Overlay**: Tapping the Sparkle FAB toggles the inline card on and off. Tapping `✕` on the card collapses it immediately.

2. **Native Hinge AI Prompt Feedback Aesthetic**:
   - **Card Container**: Soft rounded corners (`RoundedCornerShape(20.dp)`), hairline borders (`#EBEBEA` in light mode, `#2B2B2B` in dark mode), and high-contrast surfaces (`#FFFFFF` in light mode, `#1E1E1E` in dark mode).
   - **Header**: Features a distinctive sparkle icon (✨ in Hinge's signature burgundy/purple tint), "Prompt Review" / "AI Opener" headline in `modernEraBold`, prompt pagination (`< 1/3 >`), and a clean dismiss (✕) button.
   - **Target Prompt Context**: A compact preview chip displays the prompt question and snippet to show exactly what the opener responds to.
   - **Opener Body**: Clean, readable suggestion rendered in `modernEraMedium` (14sp) with one-tap copy. Displays a subtle pulsing spinner while generating.
   - **Streamlined Action Row**:
     - **Try another (Retry)**: Instant one-tap regeneration with fresh angles.
     - **Copy**: Copies the opener directly to the clipboard with instant green "Copied!" visual feedback.
     - **Insert**: If active chat or comment input is present, one tap inserts the generated text directly into Hinge's input field.

3. **Multi-Prompt Cycling**:
   - Candidate profiles typically contain up to 3 prompts. The header provides a compact `< 1/3 >` pill, allowing users to effortlessly switch between Prompt 1, Prompt 2, or Prompt 3 and generate tailored openers for any prompt on the profile.

4. **Backward-Compatible Coordinator (`HostAppAiSheetContent.kt`)**:
   - Serves as a lightweight delegate to `InlineAiOpenerCard`. When opened from `CandidatesScreen` or `MatchesScreen` in the companion app, it renders the exact same compact, elegant card.

---

## 7. Context Engineering & Directional Stimulus (DAIR.AI Integration)

The Unhinge prompt engineering pipeline adheres to modern empirical LLM research synthesized from the DAIR.AI Prompt Engineering Guide:

### 7.1 XML Semantic Enclosures & Data Isolation
- **Semantic Tagging**: Untrusted user-generated profile data extracted from Hinge SQLite is wrapped within explicit `<candidate_context>` tags:
  ```markdown
  <candidate_context>
  {profile}Candidate Prompts ({lineCount} items):
  {prompts}
  {avoid}{directional_stimulus}
  </candidate_context>
  ```
- **Context Bleed Protection**: Directives instruct the LLM that content inside `<candidate_context>` constitutes profile data to reference obliquely, never system commands or meta-prompts.
- **Strict Topical Isolation**: Prevents the model from fabricating "Frankenstein" openers by stitching unrelated profile data (e.g. neighborhood or college) into a prompt card unless specifically relevant.

### 7.2 Directional Stimulus Prompting (DSP) & Interactive Vibe Chips
- **Interactive Vibe Steering**: `HostAppAiSheetContent.kt` renders a horizontal scrollable row of authentic Hinge-style chips above the opener carousel:
  - `Auto`: Default balanced tone, allowing candidate profile cues to dictate archetype.
  - `Playful Tease`: Dry feigned suspicion, playful bias, and gentle friction.
  - `Micro-Debate`: Deadpan stance on low-stakes trivial food/preference debates.
  - `Chaos Story`: Straight-faced escalation or humorous hypothetical backstory.
  - `Collaborative`: Invitational conditions and joint hypothetical scenarios.
- **Dynamic Prompt Insertion**: Injects `<directional_stimulus>` into the user prompt:
  ```markdown
  <directional_stimulus>
  Desired Tone/Vibe: Playful Tease
  Tailor all reply angles to this archetype/vibe.
  </directional_stimulus>
  ```
- **Cache Isolation**: `directionalStimulus` is hashed into `getCacheKey()`, ensuring tone changes instantly trigger fresh generations without stale cache hits.

### 7.3 Large Reasoning Model (LRM) Test-Time Compute Scoping
- **Chain-of-Thought (CoT) Incompatibility**: Explicit step-by-step CoT prompting degrades instruction-following and induces overthinking on reasoning models (e.g. Gemini 2.0/3 Flash Thinking, Claude 3.7 Sonnet Thinking, DeepSeek R1).
- **Declarative Scope Guardrails**: Replaces manual step-by-step reasoning tokens with declarative test-time compute scope boundaries:
  - Internal deliberation restricted strictly to noun selection and natural mobile phrasing.
  - Zero thought leakage: enforces raw JSON output without explanatory wrappers (`Why this works:`), markdown blocks, or meta-comments.

### 7.5 Data Boundary Isolation & Delimiter Sanitization
- **Untrusted Input Escaping**: All candidate bio strings and prompt text extracted from Hinge SQLite are treated as untrusted user content and sanitized via `PromptSanitizer.sanitizeUntrusted()` to neutralize XML delimiter tag spoofing (`<candidate_context>`, `</candidate_context>`, `<directional_stimulus>`).
- **Segregation of User Controls**: User controls (`{avoid}` and `{directional_stimulus}`) are explicitly kept *outside* the `<candidate_context>` enclosure to maintain a strict barrier between third-party profile data and system instructions.

### 7.6 Deterministic Prompt Count Derivation
- **PromptEntry-Derived Count**: Output line counts are strictly derived from the number of active `PromptEntry` objects (`promptCount`) rather than physical string newlines (`promptsText.lines().size`).
- **Protection Against Multiline Answers**: Candidates with multi-sentence or multiline answers no longer cause newline inflation or corrupted JSON line padding/truncation during parser alignment.

### 7.7 Privacy Minimization & Context Boundaries
- **Opener Generation Context**: Only essential conversational attributes (first name, age, job title/employer, city) are transmitted to external LLM providers during opener generation.
- **Sensitive Trait Suppression**: Habits (smoking, drinking, marijuana), dating intentions, relationship types, hometowns, and zodiac signs are suppressed from opener generation prompts, reserving full dossier exposure exclusively for user-initiated deep "Ask AI" queries.

### 7.8 Template Schema Validation & Output Auditing
- **Schema Guardrails**: `PromptRepository.validateOpenerTemplate()` verifies that remote or custom user templates contain mandatory placeholders (`{prompts}` and `{lineCount}`) before activation, preventing broken generation states.
- **Output Sanitization**: `PromptSanitizer.cleanOpenerReply()` strips markdown wrappers, analytical preambles (`Why this works:`), conversational throat-clearing, and normalizes excessive interrogation questions.

---

## 8. Match Chat Reply Wingman Architecture

The AI Wingman extends seamlessly from candidate profile openers into active match chat conversations, providing contextual reply suggestions calibrated to conversation momentum and human texting dynamics.

### 8.1 In-Process Chat Context Extraction (`HostCandidateReader.kt`)
- **Screen Context Classification**: `HostCandidateReader.detectScreenContext` classifies active chat screens as `ScreenContext.MATCH_CHAT` by identifying Hinge's chat input composable (`messageComposition`, `messageCompositionContainer`, or hint `"Send a message"`).
- **Match Chat Extraction (`readCurrentMatchChat`)**:
  - Traverses Hinge's active activity view tree to extract the visible match name from the action bar or chat header, any active draft message text in `messageComposition`, and on-screen message text.
  - Queries Hinge's internal SQLite database (`/data/data/co.hinge.app/databases/db`) asynchronously in strict `SQLiteDatabase.OPEN_READONLY` mode.
  - Reads matching candidate demographic attributes from `profiles` and full conversation transcripts from `subject_messages` (or `messages`):
    - Identifies message directions (`sentBySubject = true` for match, `false` for user).
    - Unpacks voice note records (`isVoiceNote = true`) and transcribed text (`voiceTranscript`).
    - Sorts chronologically, formatting the most recent 15 messages via `AiWingmanHelper.formatConversationHistory`:
      ```text
      Alex: Hey! How was your weekend?
      You: Pretty good, went hiking in Marin.
      Alex: [Voice note: "Oh nice, which trail did you do?"]
      ```
    - Computes `isMyTurn`: determined by whether the final message in the transcript was sent by the match.

### 8.2 The 4 Empirically Proven Human Chat Reply Archetypes (`chat_reply_template.md`)
Unlike generic chatbots that produce robotic praise or interrogation lists, Unhinge generates exactly 4 distinct replies matching validated dating conversation archetypes:
1. **Playful Tease / Banter**: Lighthearted friction, teasing assumption, or mock disagreement that creates flirty tension without being mean-spirited.
2. **Date Pitch / Move to Meet**: Smooth, low-stakes escalation proposing coffee, drinks, or an activity based on a shared topic in the conversation.
3. **Witty Callback / Story**: Sharp callback to a specific detail mentioned earlier in the chat or on their profile prompts.
4. **Casual / Low-Key**: Natural, low-effort follow-up maintaining momentum without looking overly eager.

### 8.3 In-App Overlay Positioning & Direct Text Injection (`HostAppAiFab.kt`)
- **Dynamic Keyboard-Grounded Docking**:
  - In chat mode, `findChatInputBounds(rootView)` locates Hinge's native chat input container.
  - Docks a compact 48dp sparkle FAB at the bottom-right corner directly above the input box:
    ```kotlin
    val targetX = containerWidth - 16.dp - fabSize
    val targetY = chatInputRect.top - containerScreenY - 8.dp - fabSize
    ```
  - As the Android soft keyboard appears or resizes, layout passes automatically adjust `chatInputRect.top`, keeping the FAB docked right above the input field.
- **Direct Reply Injection (`insertReplyIntoChatInput`)**:
  - Tapping **"Insert into Chat"** in the reply sheet directly populates Hinge's `messageComposition` `EditText`:
    ```kotlin
    val editText = activity.findViewById<EditText>(resId)
    editText.setText(text)
    editText.setSelection(text.length)
    editText.requestFocus()
    ```
  - Closes the AI sheet immediately, leaving the user with the generated reply ready to review and send with a single tap.
- **1-Tap Clipboard Copying**: Secondary "Copy" button places the selected reply on the Android clipboard with temporary visual confirmation.

### 8.4 Companion Application Integration (`MatchesScreen.kt`)
- **Match Card Quick-Action**: Each match item in the companion app's `MatchesScreen` displays an AI sparkle button for instantaneous reply suggestion generation.
- **Transcript Dialog AI Sheet**: Opening the chat transcript dialog provides an **"AI Wingman"** header action and a **"Suggest replies"** prompt in empty conversation states.
- **Shared Compose Sheet**: Invokes `HostAppAiSheetContent(isChatMode = true, initialChatContext = chatContext)`, ensuring unified styling, archetype selection, and regeneration workflows across both the host app overlay and the companion app.

---

## 9. Native In-Prompt-Card AI Opener (`InlineAiOpenerCard.kt`)

Rather than relying on floating bottom sheets or intrusive modal dialogs, Unhinge embeds AI openers directly into Hinge's active prompt cards in the whitespace above the "Add a comment" input box.

### 9.1 Minimal, Surgical & Pill-Free Design
- **Zero Pill Clutter**: All bulky pills (such as "Try another", "Insert", "Copy", and pagination selectors) are omitted.
- **Small Top-Right Corner Retry Button**: A compact 26dp circular button in the top right corner houses the retry trigger (`Icons.Default.Refresh`), rotating into a mini progress spinner (`CircularProgressIndicator`) during generation.
- **Direct Tap-to-Copy Opener Body**: Tapping the opener text immediately copies it to the system clipboard (`ClipData.newPlainText`), displays a confirmation toast, and temporarily renders an inline green "Copied!" indicator.

### 9.2 Precision In-Card Docking & 120Hz Scroll Tracking
- **Target Node Bounds Matching**:
  - `width`: Set to `commentRect.width()`, perfectly matching Hinge's "Add a comment" field and prompt card bounds.
  - `x`: `(commentRect.left - containerScreenX).toFloat()`.
  - `y`: `commentRect.top - containerScreenY - cardHeight - gap`, docking directly inside the white prompt card above the comment box.
- **120Hz PreDraw Refresh**: In `preDrawListener`, screen coordinates of the active virtual comment node are queried on each frame via `node.getBoundsInScreen(info.commentInputRect)`, keeping the card glued to the scrolling prompt card without jitter.
- **Automatic Lifecycle**: The card appears automatically whenever a user taps "Like" on any prompt card or photo, and dismisses immediately (`View.GONE`) when comment mode is exited.





