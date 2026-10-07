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

1. **[AiWingmanHelper.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/AiWingmanHelper.kt)**:
   - Central coordinator orchestrating requests, tunable generation parameters, caching, and state transitions.
   - Exposes `status: StateFlow<WingmanStatus>` (`Idle`, `Generating`, `Success`, `Error`).
   - Exposes `suggestedReplyFlow: MutableStateFlow<String?>` for progressive UI streaming updates per prompt.
   - Exposes `hasActiveSuggestions: StateFlow<Boolean>` for reactive UI indicators.
   - Manages concurrent in-memory caching (`replyCache`) preventing redundant network calls.
   - Accepts caller-supplied `temperature` and `topP` (defaults `0.85` / `0.95`, adjustable via Settings → Model Parameters) instead of fixed tone presets. No artificial token cap is applied; output length is left to the model/service (Anthropic wire API still requires `max_tokens`, sent as thinking budget + headroom or a high default).

2. **[OpenRouterStreamingService.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/OpenRouterStreamingService.kt)**:
   - OkHttp-backed Server-Sent Events (SSE) streaming client with the same lenient structured-output policy as above.
   - Emits structured `StreamChunk` events (`Content(delta)`, `Complete(fullText)`, `Error(throwable)`).
   - Retries transient HTTP failures (500/502/503/529) with exponential backoff and retries structured-output rejections once without `response_format`; `streamChat` retries transient failures identically. `AiWingmanHelper` falls back from streaming to non-streaming on empty-provider-pool 5xx before surfacing an error.
   - Ingests streaming chunks, parses JSON deltas, validates JSON-schema line outputs, and falls back to text parsing if needed.

3. **[OpenRouterService.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/OpenRouterService.kt)**:
   - Synchronous/standard HTTP client for OpenRouter and OpenAI-compatible API providers.
   - Generates tailored opening lines matching the selected candidate prompt and bio answers.
   - Sends `response_format` JSON-schema requests (`lines` array) with caller-supplied temperature, top-P, and system instructions, but omits `provider.require_parameters` (forcing it shrinks OpenRouter's provider pool and surfaces as HTTP 503 "No available model provider"; the parser already handles bare-array / plain-text fallbacks, so availability wins). No `max_tokens` is sent on Chat Completions; output length is unconstrained.
   - Retries transient 5xx (500/502/503/529) with exponential backoff; structured-output rejections get one lenient retry without `response_format`. Model IDs are sanitized (leading `~` artifacts stripped) and the pre-Oct-2026 stored default migrates to the current default.
   - Extracts detailed error diagnostics (`apiErrorMessage()` + `friendlyGenerationError()`) mapping 401/402/403/404/429/5xx to provider-aware and model-aware Settings hints (Zen free models never prompt for an API key or reference OpenRouter's `sk-or-`; guides on Zen free tier restrictions, unsupported model fallbacks to `space-bunny-free`, and specific key formats for OpenRouter, Claude, OpenAI, and Gemini).

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
   - Canonical routing hub and OpenCode client emulator for Zen endpoints (`/v1/responses`, `/v1/chat/completions`, `/v1/messages`, `/v1/models/<id>`).
   - Recognizes zero-cost OpenCode Zen free models requiring zero API keys and automatically defaulting `Authorization: Bearer public`:
     - `space-bunny-free` (default high-capability reasoning model)
     - `muse-spark-1.3-contributor-free` (Muse Spark 1.3 Free; aliases `muse-spark-1.3-free`)
     - `big-pickle`
     - `fledge-alpha-free`
     - `ling-3.1-flash-free`
     - `longcat-2.5-preview-free`
     - `mimo-v2.6-flash-free`
     - `mimo-v2.5-free`
     - `nemotron-3-ultra-free`
     - `nemotron-3.5-lightning-free`
   - Generates reverse-engineered OpenCode timestamp-encoded session IDs (`ses_` + 12 hex chars `~((now * 4096) + p)` + 14 random base62 chars), W3C traceparents, client header (`x-opencode-client: cli`), project identifier (`x-opencode-project: fbfbb8ecca77fbf4f585927700f1baa979cb6a94`), session affinity headers, and user-agent (`opencode/latest/2.0.18/cli`).
   - Emulates the full 12 OpenCode CLI tools (`edit`, `glob`, `grep`, `question`, `read`, `shell`, `skill`, `subagent`, `webfetch`, `websearch`, `write`, `execute`) in `DefaultZenChatTools` (Chat Completions format) and `DefaultZenResponsesTools` (Responses format with top-level `name`/`parameters`) to satisfy OpenCode's gateway agent validation on keyless free-tier requests.

10. **[OpenAiResponsesService.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/OpenAiResponsesService.kt)**:
   - Client for OpenAI Responses API (`POST /v1/responses`) utilized by Muse Spark, GPT, and Grok on Zen.
   - For free-tier models (`muse-spark-1.3-contributor-free`), automatically injects `DefaultZenResponsesTools`, `prompt_cache_key = sessionId`, `include = ["reasoning.encrypted_content"]`, and consumes streaming SSE deltas (`stream = true`) to comply with OpenCode gateway free tier constraints.
   - Handles `instructions` + `input` structure, parses `output[].content[].text` (or streaming `response.output_text.delta`), and propagates reasoning effort configuration (`reasoning: { effort: reasoningEffort }`).

11. **[AnthropicMessagesService.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/AnthropicMessagesService.kt)**:
   - Client for Anthropic Messages API (`POST /v1/messages`) used by Claude and Qwen on Zen.
   - Handles `system` + `messages` format, `anthropic-version: 2023-06-01`, and `x-api-key` alongside Bearer authorization.

11. **[GoogleGeminiService.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/ai/GoogleGeminiService.kt)**:
   - Client for Google Gemini native API (`POST /v1/models/<id>:generateContent` and `:streamGenerateContent`) used for Gemini models on Zen.
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
     - `x-session-affinity` & `x-session-id`: Mirrored session ID.
     - `User-Agent`: `opencode/latest/2.0.18/cli`.
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
   - `PreferencesManager` defaults to the `Zen` provider on `Consts.ZEN_DEFAULT_BASE_URL` (`https://opencode.ai/zen/v1/chat/completions`) / `Consts.ZEN_DEFAULT_MODEL` (`space-bunny-free`), so a fresh install generates openers and answers Ask AI queries without an account, key, or billing configuration.
   - `AiWingmanSettingsPage` groups all 9 free models under `(Free - No Key Required)`, and clearly labels paid Zen models with `(Requires Zen key)`.
   - `AiWingmanHelper.isApiKeyRequired(provider, model)` is the single gate consumed by both the settings page and the in-app sheet (`HostAppAiSheetContent`); the Zen branch of `resolveEffectiveEndpoint` only honours a base URL as an override when it points at a genuine third-party proxy, so a stale URL from another provider can never hijack Zen routing.

---

## 6. Native Hinge Bottom Sheet UI (`HostAppAiSheetContent.kt`)

[HostAppAiSheetContent.kt](../app/src/main/java/io/github/s1ddhants1/unhinge/hook/ui/HostAppAiSheetContent.kt) delivers a seamless in-app bottom sheet modeled strictly after Hinge's editorial design system and purchase carousel layouts:

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

2. **Horizontal Card Carousel Architecture (Hinge Paywall / Standouts Style)**:
   - Modeled directly on Hinge's decompiled Jetpack Compose purchase UI (`sv1.java`, `cb2.java`, `tu7.java`, `p86.java`, `b5h.java`):
     - Uses `HorizontalPager` with `contentPadding = PaddingValues(horizontal = 28.dp)` and `pageSpacing = 14.dp`, allowing adjacent cards to peek from the edges.
     - Card containers styled with `RoundedCornerShape(22.dp)`, subtle borders, and authentic card background fills (`#FFFFFF` in light mode, `#1E1E1E` in dark mode).
     - Each card features an independent `Modifier.verticalScroll(rememberScrollState())` to handle lengthy prompts and multiline LLM suggestions smoothly without truncating.
   - **Hinge-Style Indicator Dots**:
     - Positioned directly below the card carousel.
     - All pages render as authentic 6dp circular dots matching native Hinge bottom sheets (`DotsPagerIndicator` / `DotsTabLayout`).
     - The active page is highlighted with Hinge primary tint (`#161616` / `#FFFFFF`) without expanding into a pill, while inactive dots render in muted subtle gray (`#D6D6D4` / `#383838`).

3. **Authentic Prompt Cards & Multi-Opener Draft Bubbles**:
   - Individual candidate prompts styled as large rounded cards with question title and Tiempos answer.
   - Tailored conversation openers embedded directly within each card as a drafted comment bubble (`#F4F4F2` in light mode, `#262626` in dark mode).
   - **Multi-Opener History & Stepper Navigation**:
     - Tapping the Refresh/Regenerate button triggers fresh generation with `forceRefresh = true` bypassing session caching.
     - Feeds `avoidReplies` into LLM prompts at elevated creativity temperature (0.85) to guarantee distinct, fresh alternatives on every regeneration.
     - Each prompt accumulates generated openers into `PromptEntry.repliesFlow`, displaying an interactive stepper (`< 2 of 3 >`) with previous/next navigation arrows.
     - Single-tap "Copy" button automatically copies the currently active opener in the stepper.
     - Per-entry loading state (`isGeneratingFlow`) displays a spinning indicator on the prompt's refresh button without disrupting other cards or blanking existing text.
   - **Contextual Card Quick-Action**: Includes an inline `"Ask AI"` action button on each prompt card that automatically pre-populates the input bar with focus on that specific prompt.

4. **Smooth Ask AI Experience & Dynamic Card Sequencing**:
   - **Elimination of Gaze Jump**: When a user submits an Ask AI query or quick action, the generated analysis is appended cleanly as a new card in `customInteractions` rather than prepending at the top of the screen.
   - A `LaunchedEffect` smoothly scrolls the pager (`pagerState.animateScrollToPage`) to the newly created interaction card.
   - **Quick Action Suggestion Chips**: Horizontal scroll row above the input bar offering high-converting one-tap dating analyses:
     - `Vibe check`
     - `Playful roast`
     - `First date pitch`
     - `Hidden hook`
     - `Green & red flags`
   - **Interactive Refinement Pills**: Generated custom AI cards feature quick follow-up refinement action pills (`[Shorter]`, `[Bolder]`, `[Teasing]`, `[Date pitch]`), allowing immediate tone tuning without typing.
   - Streaming responses render live via `OpenRouterStreamingService.streamChat` / `AiWingmanHelper.streamCustomChat` directly inside the active card.

5. **Interactive "Ask AI" Input Bar**:
   - Floating rounded capsule with soft-keyboard elevation (`imePadding` + `SOFT_INPUT_ADJUST_RESIZE`).
   - Clean placeholder `"Ask AI about this profile..."`.
   - Injects rich candidate profile context (demographics, verified status, prompt Q&As, bio) and the user's Model Parameters settings (temperature, top-P, max tokens) into custom queries.
   - Renders interactive response cards supporting instant 1-tap clipboard copying, regeneration, follow-up refinement, and dismissal.

6. **Interactive Swipe-to-Dismiss Architecture**:
   - Native bottom sheet physics matching Android's `BottomSheetBehavior`:
     - Real-time touch tracking via `Animatable(0f)` offset and `Modifier.draggable` attached to the drag handle, candidate identity header, and empty state containers.
     - Release physics: drags beyond threshold (>25% sheet height) or downward flings (>1000px/s) smoothly animate the sheet off-screen before invoking `onDismiss()`; sub-threshold drags spring back up to `0dp`.
     - Close "X" button triggers the identical downward exit animation prior to dismissal.
     - Window-level integration: `dialog.setCanceledOnTouchOutside(true)` and `android.R.style.Animation_InputMethod` for native slide-up entrance and outside-touch dismissal.

7. **Clean Header & Visual Discipline**:
   - Candidate identity header displays candidate first name and age in Modern Era Bold, with photo thumbnail and subtitle (work, location) without selfie verification badges for an uncluttered layout.
   - Opener action button cleanly labeled "Copy" with temporary confirmation state.
   - Zero emojis across all UI copy, chips, pills, badges, toasts, and comments.
   - Zero haptic feedback vibrations for clean, distraction-free interactions.

8. **System Bar & Window Isolation**:
   - `UnhingeTheme` strictly restricts mutating `isAppearanceLightStatusBars` and `isAppearanceLightNavigationBars` to the companion app process (`view.context.packageName == "io.github.s1ddhants1.unhinge"` with `setSystemBars = true`).
   - `HostAppAiFab.showAiSheet` passes `setSystemBars = false` and snapshots the host activity's status bar appearance on display, restoring it on dismiss to guarantee Hinge's status bar icons never turn white on a white background.



