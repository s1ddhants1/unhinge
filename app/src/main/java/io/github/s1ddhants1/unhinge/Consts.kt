package io.github.s1ddhants1.unhinge

object Consts {
    const val TAG = "Unhinge"
    const val APP_TAG = "UnhingeApp"
    const val PREFS_SETTINGS = "privacy_settings"
    const val TARGET_PACKAGE = "co.hinge.app"
    const val MODULE_PACKAGE = "io.github.s1ddhants1.unhinge"

    // Telemetry hosts safe to drop (core API prod-api.hingeaws.net, media CDNs, and verification remain untouched)
    val TELEMETRY_HOST_SUBSTRINGS = arrayOf(
        "sdk.split.io",
        "auth.split.io",
        "telemetry.split.io",
        "streaming.split.io",
        "events.split.io",
        "appsflyer.com",
        "appengage.",
        "app-measurement.com",
        "crashlyticsreports-pa.googleapis.com"
    )

    // Privacy Preference Keys
    const val PREF_MASTER_ENABLED = "master_enabled"
    const val PREF_BLOCK_FIREBASE = "block_firebase_analytics"
    const val PREF_BLOCK_CRASH_UPLOAD = "block_crashlytics_upload"
    const val PREF_BLOCK_PERF = "block_perf"
    const val PREF_BLOCK_APPSFLYER = "block_appsflyer"
    const val PREF_BLOCK_INCOGNIA = "block_incognia"
    const val PREF_BLOCK_SPLIT = "block_split_telemetry"
    const val PREF_BLOCK_UBE = "block_ube"
    const val PREF_BLOCK_OKHTTP = "block_okhttp_telemetry"
    const val PREF_FUZZ_LOCATION = "fuzz_location"
    const val PREF_BLOCK_CONTACTS = "block_contacts"
    const val PREF_BLOCK_METRIC_WORKERS = "block_metric_workers"
    const val PREF_BLOCK_GMS_MEASUREMENT = "block_gms_measurement"
    const val PREF_BLOCK_DATATRANSPORT = "block_datatransport"

    // Feed Navigation
    const val PREF_ENABLE_FEED_NAVIGATION = "enable_feed_navigation"

    // AI Wingman & Theme Preference Keys
    const val PREF_AI_PROVIDER = "ai_provider"
    const val PREF_OPENROUTER_API_KEY = "openrouter_api_key"
    const val PREF_OPENROUTER_BASE_URL = "openrouter_base_url"
    const val PREF_OPENROUTER_MODEL = "openrouter_model"
    const val PREF_AI_CUSTOM_SYSTEM_PROMPT = "ai_custom_system_prompt"
    const val PREF_AI_OVERRIDE_SYSTEM_PROMPT = "ai_override_system_prompt"
    const val PREF_SHOW_HOST_APP_FAB = "show_host_app_fab"
    const val PREF_AI_TEMPERATURE = "ai_temperature"
    const val PREF_AI_TOP_P = "ai_top_p"
    const val PREF_AI_MAX_TOKENS = "ai_max_tokens"
    const val PREF_PURE_BLACK = "pure_black"
    const val PREF_THEME_COLOR = "theme_color"
    const val PREF_THEME_MODE = "theme_mode"

    const val OPENROUTER_DEFAULT_BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
    const val OPENROUTER_DEFAULT_MODEL = "inception/mercury-2.5-preview"
    // Pre-Oct-2026 default retained for one-way migration of stored prefs.
    const val LEGACY_OPENROUTER_DEFAULT_MODEL = "google/gemini-2.5-flash-lite"
    const val ZEN_DEFAULT_BASE_URL = "https://opencode.ai/zen/v1/responses"
    const val ZEN_DEFAULT_MODEL = "muse-spark-1.3-contributor-free"
    const val DEFAULT_THEME_COLOR = 0xFFED5564L
    const val DEFAULT_AI_TEMPERATURE = 0.85f
    const val DEFAULT_AI_TOP_P = 0.95f
    const val DEFAULT_AI_MAX_TOKENS = 250

    val DEFAULT_AI_SYSTEM_PROMPT = """
You text on Hinge like a real, effortless human—never like an AI bot, copywriter, or pickup artist.
Data-backed messaging rules:
1. Strict brevity: 8 to 16 words (under 25 words max). Shorter beats longer.
2. Focus on them: minimize self-focused "I" pronouns ("I think", "I am").
3. Anchor to an exact noun: reference their specific place, dish, artist, or hobby detail.
4. Low friction: 1 grounded reaction or 1 effortless question.
5. Strictly avoid AI clichés: NO "bold claim", "challenge accepted", "you look like", "you strike me as", "plot twist", "are we talking", "on a scale of 1-10", or dramatic preambles.
""".trimIndent()
}
