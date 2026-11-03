<div align="center">
<img src="assets/icon.png" width="100" alt="Unhinge Logo"/>
<h1>Unhinge</h1>

<p>An open-source enhancement module for Hinge on Android.</p>

<a href="https://github.com/s1ddhants1/unhinge/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/s1ddhants1/unhinge/build.yml?style=for-the-badge&logo=githubactions&logoColor=white&label=CI" alt="CI Status" /></a>
<a href="https://github.com/s1ddhants1/unhinge/releases"><img src="https://img.shields.io/github/v/release/s1ddhants1/unhinge?style=for-the-badge&color=ED5564&logo=android&label=Release" alt="Release Version" /></a>
<a href="https://github.com/s1ddhants1/unhinge/releases/tag/nightly"><img src="https://img.shields.io/badge/Nightly-Pre--release-f43f5e?style=for-the-badge&logo=github" alt="Nightly Build" /></a>
<a href="https://github.com/s1ddhants1/unhinge/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-10b981?style=for-the-badge" alt="License" /></a>
<img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026--37)-f59e0b?style=for-the-badge&logo=android" alt="Android API Support" />
<img src="https://img.shields.io/badge/Framework-LSPosed%20%7C%20LibXposed%20%7C%20LSPatch-8b5cf6?style=for-the-badge" alt="LSPosed / LibXposed / LSPatch" />
<img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-06b6d4?style=for-the-badge&logo=jetpackcompose" alt="Jetpack Compose Material 3" />

</div>
## Compatibility

| Requirement           | Supported Details                                                                                                                    |
| :-------------------- | :----------------------------------------------------------------------------------------------------------------------------------- |
| **Target App**        | [Hinge: Dating & Relationships](https://play.google.com/store/apps/details?id=co.hinge.app) (`co.hinge.app`) v10.4.0+                |
| **Android Version**   | Android 8.0 (API 26) through Android 16+ _(Target app Hinge requires Android 12L+)_                                                  |
| **Rooted Frameworks** | [LSPosed](https://github.com/LSPosed/LSPosed) (v2.0.0+), [Vector](https://github.com/JingMatrix/Vector), or LibXposed (API 101/102+) |
| **Rootless Setup**    | [LSPatch](https://github.com/JingMatrix/LSPatch) (Integrated Mode and Manager Mode)                                                  |
| **Architecture**      | `arm64-v8a` (compatible with modern 16 KB page-size kernels)                                                                         |

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

## Credits & License

- **Author**: [s1ddhants1](https://github.com/s1ddhants1)
- **Frameworks**: [LibXposed](https://github.com/libxposed), [LSPosed](https://github.com/LSPosed/LSPosed), [Vector](https://github.com/JingMatrix/Vector), [LSPatch](https://github.com/JingMatrix/LSPatch)
- **License**: [MIT License](LICENSE)

**Disclaimer**: Unhinge is an independent, open-source project intended strictly for personal privacy enhancement, educational study, and security research. "Hinge" is a registered trademark of Match Group, LLC. Unhinge is not affiliated with, endorsed by, or sponsored by Hinge or Match Group.
