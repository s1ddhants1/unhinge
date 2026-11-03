---v2.0.0
# Unhinge v2.0.0

### AI Prompt Wingman & Translation Pipeline
- Replaced custom AI pipeline verbatim with the production-grade streaming pipeline (`DeepLService`, `OpenRouterService`, `OpenRouterStreamingService`, `AiWingmanHelper`, `PromptEntry`).
- Integrated DeepL Free (`:fx` endpoint routing) and DeepL Pro translation engines with XML line-tag preservation and exponential backoff retry.
- Integrated OpenRouter & OpenAI-compatible engine with Server-Sent Events (SSE) streaming, JSON-schema line validation, and fallback parsing.
- Added Metrolist AI configuration: Translation Mode (Literal, Transcribed, Romanized), DeepL Formality, API domain selection, and custom system prompt editor.
- Symmetrical in-app AI action button styled identically to Hinge's circular pass "X" button (60dp, 16dp elevation, 20dp margin), positioned horizontally mirrored to the X button, exclusive to the Discover page, and limited to the active candidate.
- Direct read-only extraction of active candidate dossier, prompts, and photos from Hinge SQLite database.
- Dynamic in-memory caching and real-time StateFlow UI updates with 1-tap clipboard copying.
- Recreated AI Wingman bottom sheet from scratch to match native Hinge UI: editorial serif typography for prompt answers and candidate titles, authentic prompt cards with integrated draft comment bubbles, capsule pill selectors, and clean monochrome styling.

### Material 3 Design System & Settings
- Implemented modular, dedicated Settings subsystem modeled after InstaEclipse with smooth subpage navigation (`SettingsScreen`).
- Added categorized pages: Appearance (`ThemeMode.SYSTEM`/`LIGHT`/`DARK`, AMOLED black), AI Prompt Wingman (exact Metrolist provider & API credentials layout with EnumDialog/TextFieldDialog, DeepL/OpenRouter/OpenAI/Claude/Gemini/Perplexity/XAi/Mistral/Inception/Custom support, tone persona, and system prompt editor), and Privacy & Telemetry (13 modular hooks).
- Consolidated AI API token and provider configuration into `AiWingmanSettingsPage` as the single source of truth, eliminating redundant dialogs and routing all configuration actions to Settings.
- Replaced repository code icon with official GitHub brand icon.
- Moved Settings action into the 3-dot overflow menu for a cleaner top bar.
- Eliminated excessive badge clutter across candidate cards and removed top bar pulsing indicator.
- Removed all haptic feedback invocations across companion app interactions, cards, tabs, and host overlay FAB.

### CI/CD Workflows
- GitHub Actions CI/CD workflows:
  - `build.yml`: Automated release & debug APK compilation with nightly prerelease deployment.
  - `build_pr.yml`: PR testing and ephemeral cached debug keystores.
  - `build_quick.yml`: Manual fast release builds bypassing lint.
  - `release.yml`: Automatic semver change detection, changelog parsing, and GitHub release creation.
