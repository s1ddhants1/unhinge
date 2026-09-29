# Hinge Android App — Decompilation Report (on-device)

Target: `co.hinge.app` v10.4.0 (versionCode 168201253), minSdk 32, targetSdk 36, compileSdk 37. Base APK ~41.6MB (2314 files) + `split_config.arm64_v8a` (~6.6MB native libs). Analyzed in `/data/data/com.termux/files/home/hinge_decompile/` via `aapt`, `apktool 3.0.3`, `jadx 1.5.6` (15663 Java files, ~30770 smali across classes.dex + classes3/4.dex).

Companion on device: `com.hingedark.module` v1.4.1 (789KB, LibXposed, scope `co.hinge.app`) — separate force-dark theme mod, not part of Hinge.

## 1. Architecture

- Language: Kotlin 2.x, Jetpack Compose (UI dump shows `ComposeView` + `nav_host` + `p5k` Compose nodes, no WebView). Single-activity: `co.hinge.app.ui.AppActivity` (singleTop, portrait, adjustResize) + `NavHost`.
- DI/startup: `co.hinge.app.App extends Application` (obfuscated fields, Hilt-style `Hilt_AppActivity`, `androidx.startup.InitializationProvider`, `ProcessLifecycleInitializer`, `ProfileInstallerInitializer`, `OkHttp PlatformInitializer`).
- Obfuscation: R8 (`r8-map-id-*`, top-level `a*.smali`, `defpackage.*` in jadx). `co/hinge/*` package names retained (~1829 smali, 60+ feature dirs); core logic in obfuscated `defpackage`.
- UI model (from `hinge_ui.xml` live dump): bottom tabs Discover / Standouts / Likes You (selected) / Matches / Profile Hub; Likes-You empty state with “Boost your profile” + “Upgrade to HingeX” CTAs; `sdui_notification_host` + `blur_view` overlays.
- Resources: `resources.arsc` 3.8MB, Lottie `res/raw/*.json` (heart, roses, loaders, standouts/discover/matches), `login_video.webm`, FaceTec strings, `locales_config`, `share_shortcuts.xml`, `file_paths.xml` (`HingeDataExport/` for GDPR export), `network_security_config.xml` (system trust anchors only).

## 2. Manifest entry points

- Deep links on `AppActivity`: `hinge://`, `https://hinge.co/app`, `https://hinge.onelink.me`, `https://ablinks.mail.hinge.co`, `SEND text/plain`, App Shortcuts.
- `InvalidApkActivity`, `ChatBubbleActivity` (bubble, `HingeTheme`), Places Autocomplete x4, Firebase `GenericIdpActivity`/`RecaptchaActivity`, Billing `ProxyBillingActivity(V2)`, Play Core dialog, `FaceTecSessionActivity`.
- Services: `AppFcmMessagingService` (priority 1000 `MESSAGING_EVENT`), `FirebaseMessagingService` (-500), `ReplyMessageReceiver` (`co.hinge.chat.ReplyMessage`), `BoostNotificationReceiver`, `ChooserTargetServiceCompat`, WorkManager `SystemJobService`/`SystemForegroundService`, Room invalidation, `TransportBackendDiscovery`.
- Providers: `androidx-startup`, `FileProvider (co.hinge.app.fileprovider)`, `FirebaseInitProvider (co.hinge.app.firebaseinitprovider)`.
- Permissions: INTERNET, FCM, POST_NOTIFICATIONS, FINE/COARSE_LOCATION, CAMERA, RECORD_AUDIO (optional), READ_MEDIA_IMAGES, READ_CONTACTS, BILLING/CHECK_LICENSE, AD_ID, WAKE_LOCK, FOREGROUND_SERVICE, RECEIVE_BOOT_COMPLETED.

## 3. Feature modules (`sources/co/hinge/`)

`domain` (largest), `inappnotifications`, `billing`, `features`, `subsystem`, `foundation`, `chat`, `user`, `auth`, `paywall`, `likesyou`, `standouts`, `matches`, `boost`, `onboarding`, `selfie`, `edit_profile/media/voice`, `we_met`, `blocklist`, `hiddenwords`, `telemetry/metrics`, `geocoding`, `cdn`, `data_download`, `delete_account`, `video_prompts/videotrimmer`, `promptfeedback`, `dateIdeas`, `friends_take`, `passing_thoughts`, `pause`, `relocation`, `offers/upsell`, `subscription_edu`, `storeaccount`, `enforcement_fairness/abuse/content_takedown/banned/countryblocked/age*` — matches dating lifecycle: onboarding → profile → Discover/Standouts/LikesYou → chat → We Met → moderation.

Key flows:
- Auth: `auth/{data,logic,errors}`, `auth_verification/{data,email}`, `sms`, `login`, Firebase Auth + Recaptcha/GenericIdp + Credential Manager (`HiddenActivity`).
- Integrity: `attestation/data` (`ChallengeResponse`, `VerifyRequest/Response` + JsonAdapters, `PlayIntegrityPrepareFailedException`) — Play Integrity verify path.
- Chat: `chat/{data,domain,errors}`, `chatstreaming/ChatStreamingServiceImpl`, `ChatBubbleActivity`, FCM → `Chat/Like/MatchNotificationWork` (WorkManager), `ReplyMessageReceiver`.
- Billing: `billing/{caches,errors,models,providers/google}`, `api/models/products` (Moshi adapters), tiers `tier1/tier2/tier3.1month/tier3.3month`; Play Billing Client 9.1.0. Monetization strings: Hinge+, HingeX, Hinge One, Boost, Roses, Priority Likes, Standouts.
- Media: CameraX, `videotrimmer*`, Cloudinary webhook, `hinenexus` CDN, Places/Maps (`google_maps_key`, geocoding).
- Safety: FaceTec SDK (`FaceTecSessionActivity`, 100+ FaceTec strings, `assets/com/facetec/sdk/`), selfie/ID/NFC scan, abuse/ban/appeal help links.

## 4. Network (from smali string table; Java URLs obfuscated/remote-configured)

Hardcoded hosts observed:
- `https://prod-api.hingeaws.net` — primary API
- `https://prod-ue1-metrics.hingeprod.net`, `https://client-telemetry.hingeprod.net`, `https://client-telemetry-preauth.hingeprod.net`, `https://prod-ue1-cloudinary-webhook.hingeprod.net`
- `https://media.hingenexus.com`, `https://media-dev.hingenexus.com`, `https://api.prod.cdn.gcp.hingenexus.com`
- `https://hinge-ue1-prod-cli-public-downloads.s3.amazonaws.com`
- `https://hinge.onelink.me/`, `https://hingedev.onelink.me/`, `https://ablinks.mail.hinge.co/`, `https://hinge.co`, `https://help.hinge.co`, `https://hingeapp.zendesk.com`
- Google: `maps.googleapis.com`, `firebaseremoteconfig`, `firebaseinstallations`, `fcmregistrations`, `recaptcha.net`; Split.io: `sdk/auth/telemetry/streaming/events.split.io`
- No custom CA in network config; OkHttp + gRPC + OpenTelemetry (`META-INF/services/io.grpc.*`, `wire-*`, `okhttp.kotlin_module`).

## 5. Third-party SDKs

Firebase (Messaging/Crashlytics NDK/Crashlytics-Ktx/RemoteConfig/Installations/Perf-Ktx/Analytics-connector/Sessions, `firebase_*_collection_enabled=false`), Play Billing/GMS/Places/Maps/Credentials, AppsFlyer (`INSTALL_PROVIDER`, backup/extraction rules), Split.io feature flags, FaceTec (Eigen/Boost attributions), Incognia (`smali/com/incognia`), Airbnb (Lottie/paris?), Square (OkHttp/Retrofit/Moshi/wire), LiveData/CameraX/Media3/DataStore/WorkManager/Room/Emoji2/ProfileInstaller.

Native split (`arm64-v8a`): `libPhoenixAndroid.so` (3.6MB, largest — media/integrity), `libd29c.so` (1.4MB), `libcrashlytics*.so`, `libgraphics-core.so`, `libandroidx.graphics.path.so`, `libimage_processing_util_jni.so`, `libsurface_util_jni.so`, `libdatastore_shared_counter.so`. Base APK contains no `.so` (`extractNativeLibs=false`).

## 6. Anti-tamper / root checks

`<queries>` lists ~100 packages: Magisk (`topjohnwu`, `huskydg`, `vvb2060`), KernelSU (`me.weishu.kernelsu`, `rifsxd.ksunext`), APatch (`bmax`, `garfieldhan`), Xposed/EdXposed/LSPosed (`de.robv...`, `meowcat`, `solohsu`), SuperSU/Kingroot/Framaroot, LuckyPatcher/Freedom, emulators (Bluestacks/MuMu/Genymotion), TeamViewer/AirDroid/AnyDesk/Vysor, Substrate/RootCloak. Indicates client-side root/emulator/cheat-tool detection.

## 7. HingeDark companion (`com.hingedark.module`, NOT Hinge)

LibXposed mod (`minApi 101`, `java_init: ModuleMain`, `scope: co.hinge.app`, `SettingsActivity` launcher). `ModuleMain.kt` + `HingeThemeHook`, `HingePalette`, `HingeColorRemap`: hooks `PhoneWindow.setBackgroundDrawable`, `DecorView.setWindowBackground`, `View.setBackground(Color/Drawable)`, `ColorDrawable`, `Color.parseColor`, Paint/Canvas/RecordingCanvas/Shaders, InsetsController, `ViewRootImpl`/`RenderNode`, theme `resolveAttr`, Activity/ViewGroup addView, `uiMode` config; remaps light BGs (`hinge_white`, `mist`, `gray_*`, `aubergine_light`, `lilac_*`, `mauve_*`, `pebble`, Material card/chip colors) → `BACKGROUND #121212-ish (-15592942)`, `SURFACE`, text → `LIGHT_TEXT`, skips photos/media via luminance/photo check.

## 8. Repro

```bash
su -c 'cp /data/app/~~*/co.hinge.app-*/base.apk ~/hinge_decompile/hinge_base.apk'
aapt dump badging hinge_base.apk
apktool d -f hinge_base.apk -o hinge_apktool
jadx -d hinge_jadx --no-res -j 4 hinge_base.apk
```

Full trees: `hinge_apktool/{AndroidManifest.xml,res,smali*}`, `hinge_jadx/sources/{co/hinge,defpackage,com/facetec,io/split}`.
