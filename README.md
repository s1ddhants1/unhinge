<div align="center">
<img src="assets/icon.png" width="100" alt="Unhinge Logo"/>
<h1>Unhinge</h1>

<p>An open-source enhancement module for Hinge on Android.</p>
<p>Privacy protection, in-app AI wingman openers, feed navigation, and unlimited rewinds.</p>

<a href="https://github.com/s1ddhants1/unhinge/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/s1ddhants1/unhinge/build.yml?style=for-the-badge&logo=githubactions&logoColor=white&label=CI" alt="CI Status" /></a>
<a href="https://github.com/s1ddhants1/unhinge/releases"><img src="https://img.shields.io/github/v/release/s1ddhants1/unhinge?style=for-the-badge&color=ED5564&logo=android&label=Release" alt="Release Version" /></a>
<a href="https://github.com/s1ddhants1/unhinge/releases/tag/nightly"><img src="https://img.shields.io/badge/Nightly-Pre--release-f43f5e?style=for-the-badge&logo=github" alt="Nightly Build" /></a>
<a href="https://github.com/s1ddhants1/unhinge/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-10b981?style=for-the-badge" alt="License" /></a>
<img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026--37)-f59e0b?style=for-the-badge&logo=android" alt="Android API Support" />
<img src="https://img.shields.io/badge/Framework-LSPosed%20%7C%20LibXposed%20%7C%20LSPatch-8b5cf6?style=for-the-badge" alt="LSPosed / LibXposed / LSPatch" />
<img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-06b6d4?style=for-the-badge&logo=jetpackcompose" alt="Jetpack Compose Material 3" />

</div>

---

## Features

- 🤖 **In-App AI Wingman**: Floating assistant directly inside Hinge that generates witty, tailored openers from candidate prompts and photos. Free tier included out of the box with zero API keys or accounts needed, plus optional support for OpenRouter, Claude, GPT, Gemini, and custom local models.
- ⏪ **Unlimited Rewinds**: Rewind and undo as many profiles as you want on the Discover feed without hitting paywalls or needing a Hinge+ subscription.
- 🧭 **Feed Navigation**: Browse forwards and backwards through candidate profiles in your Discover queue to review everyone before deciding who to like or pass.
- 📊 **Likes & Roses Counter**: A real-time pill displaying your remaining daily likes and roses right in the feed, updating instantly as you like profiles.
- 🛡️ **Privacy Shield**: Silently blocks invasive analytics, crash report uploads, behavioral profiling, and background telemetry without affecting normal dating functions (matches, chats, and notifications work as usual).
- 📍 **Location Fuzzing & Contacts Shield**: Conceals your exact street address with city-level GPS rounding and keeps your personal phone contacts private.
- 🗃️ **Candidate Archival**: Automatically saves visited profiles into an offline archive in the companion app with full-text search, filters, and private notes.

---

## Compatibility

| Requirement | Supported Details |
| :--- | :--- |
| **Target App** | [Hinge: Dating & Relationships](https://play.google.com/store/apps/details?id=co.hinge.app) (`co.hinge.app`) v10.4.0+ |
| **Android Version** | Android 8.0 (API 26) through Android 16+ *(Target app Hinge requires Android 12L+)* |
| **Rooted Frameworks** | [LSPosed](https://github.com/LSPosed/LSPosed) (v2.0.0+), [Vector](https://github.com/JingMatrix/Vector), or LibXposed (API 101/102+) |
| **Rootless Setup** | [LSPatch](https://github.com/JingMatrix/LSPatch) (Integrated Mode and Manager Mode) |
| **Architecture** | `arm64-v8a` (compatible with modern 16 KB page-size kernels) |

---

## Installation & Setup

### 1. Download Unhinge

- **Obtainium (Recommended)**: Automatically track and install updates by adding Unhinge to [Obtainium](https://github.com/ImranR98/Obtainium):

<p>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https%3A%2F%2Fgithub.com%2Fs1ddhants1%2Funhinge">
    <img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="80">
  </a>
</p>

Or enter the repository URL manually:
```text
https://github.com/s1ddhants1/unhinge
```

- **Manual Download**: Grab the latest APK from [Releases](https://github.com/s1ddhants1/unhinge/releases) or bleeding-edge [Nightly Builds](https://github.com/s1ddhants1/unhinge/releases/tag/nightly).

### 2. Activation

- **Rooted (LSPosed / Vector)**:
  1. Open LSPosed / Vector Manager.
  2. Enable the **Unhinge** module and add **Hinge** (`co.hinge.app`) to its scope.
  3. Force stop Hinge or reboot your device.
  4. Open Unhinge to configure your preferences.

- **Non-Rooted (LSPatch)**:
  1. Patch the Hinge APK with [LSPatch](https://github.com/JingMatrix/LSPatch) in **Integrated Mode**, selecting the Unhinge APK as the module.
  2. Install the patched Hinge APK.
  3. Features activate automatically inside the patched app without needing root or a background manager.

---

## Building from Source

```bash
git clone https://github.com/s1ddhants1/unhinge.git
cd unhinge

# Run tests
./gradlew testDebugUnitTest

# Build APKs
./gradlew assembleRelease
```

Compiled APKs are output to `app/build/outputs/apk/release/` and `app/build/outputs/apk/debug/`.

---

## Frequently Asked Questions (FAQ)

<details>
<summary><b>Do I need an API key to use the AI Prompt Wingman?</b></summary>
<p><b>No.</b> Unhinge includes a built-in free tier with reasoning models that work out of the box with zero API keys or accounts. You can also optionally connect your own API key for OpenRouter, OpenAI, Claude, Gemini, or a local endpoint in settings.</p>
</details>

<details>
<summary><b>Will using Unhinge get my Hinge account banned?</b></summary>
<p>Unhinge is built with safety as a top priority. It does not automate swipes or bot likes, does not tamper with Google Play Integrity, and does not alter server-side matching logic. It operates purely on local app enhancements and client-side tracking suppression.</p>
</details>

<details>
<summary><b>Does Feed Navigation consume likes or send skips?</b></summary>
<p><b>No.</b> Browsing profiles back and forth happens entirely on your device. No likes are consumed and no skips or passes are sent to Hinge servers until you manually tap Hinge's like or pass button.</p>
</details>

<details>
<summary><b>Does Unhinge read my private chat messages?</b></summary>
<p><b>No.</b> Unhinge only accesses profile cards from Discovery to generate opener suggestions and save dossiers. Your private chats and direct messages are never logged or accessed.</p>
</details>

<details>
<summary><b>Can I use Unhinge without root?</b></summary>
<p><b>Yes.</b> You can use <a href="https://github.com/JingMatrix/LSPatch">LSPatch</a> to embed Unhinge directly into the Hinge APK in Integrated Mode.</p>
</details>

---

## Documentation

For technical specifications, reverse-engineering findings, and architecture documentation, see the [`docs/`](docs/) directory:

- 📐 **[Architecture & System Design](docs/ARCHITECTURE.md)**
- 🪝 **[Hook Handlers & Interception Guide](docs/HOOKS.md)**
- 🕵️ **[Reverse Engineering Field Manual](docs/REVERSE_ENGINEERING.md)**
- 🔬 **[Target Decompilation Analysis](docs/HINGE_ANALYSIS.md)**
- 🧠 **[AI Wingman Architecture](docs/AI_WINGMAN.md)**
- 🗄️ **[Database & Candidate Storage](docs/DATABASE_AND_STORAGE.md)**
- 🔒 **[Telemetry & Privacy Audit](docs/TELEMETRY_AND_PRIVACY.md)**

---

## Credits & License

- **Author**: [s1ddhants1](https://github.com/s1ddhants1)
- **Frameworks**: [LibXposed](https://github.com/libxposed), [LSPosed](https://github.com/LSPosed/LSPosed), [Vector](https://github.com/JingMatrix/Vector), [LSPatch](https://github.com/JingMatrix/LSPatch)
- **License**: [MIT License](LICENSE)

**Disclaimer**: Unhinge is an independent, open-source project intended strictly for personal privacy enhancement, educational study, and security research. "Hinge" is a registered trademark of Match Group, LLC. Unhinge is not affiliated with, endorsed by, or sponsored by Hinge or Match Group.
