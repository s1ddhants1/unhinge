package io.github.s1ddhants1.unhinge

object Consts {
    const val TAG = "Unhinge"
    const val APP_TAG = "UnhingeApp"
    const val PREFS_SETTINGS = "privacy_settings"
    const val TARGET_PACKAGE = "co.hinge.app"
    const val MODULE_PACKAGE = "io.github.s1ddhants1.unhinge"

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

    const val PREF_ENABLE_FEED_NAVIGATION = "enable_feed_navigation"
    const val PREF_SHOW_AVAILABLE_LIKES = "show_available_likes"
    const val PREF_UNLOCK_ACTIVE_FILTERS = "unlock_active_filters"

    const val PREF_AI_PROVIDER = "ai_provider"
    const val PREF_OPENROUTER_API_KEY = "openrouter_api_key"
    const val PREF_OPENROUTER_BASE_URL = "openrouter_base_url"
    const val PREF_OPENROUTER_MODEL = "openrouter_model"
    const val PREF_AI_CUSTOM_SYSTEM_PROMPT = "ai_custom_system_prompt"
    const val PREF_AI_OVERRIDE_SYSTEM_PROMPT = "ai_override_system_prompt"
    const val PREF_SHOW_HOST_APP_FAB = "show_host_app_fab"
    const val PREF_AI_TEMPERATURE = "ai_temperature"
    const val PREF_AI_TOP_P = "ai_top_p"
    @Deprecated("Artificial token limit removed across app")
    const val PREF_AI_MAX_TOKENS = "ai_max_tokens"
    const val PREF_AI_REASONING_EFFORT = "ai_reasoning_effort"
    const val PREF_AI_OPENER_PROMPT_TEMPLATE = "ai_opener_prompt_template"
    const val PREF_AI_PROMPT_REMOTE_URL = "ai_prompt_remote_url"
    const val PREF_PURE_BLACK = "pure_black"
    const val PREF_THEME_COLOR = "theme_color"
    const val PREF_THEME_MODE = "theme_mode"

    const val DEFAULT_AI_PROMPT_REMOTE_URL = "https://raw.githubusercontent.com/s1ddhants1/unhinge/main/prompts/opener_template.md"

    const val OPENROUTER_DEFAULT_BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
    const val OPENROUTER_DEFAULT_MODEL = "inception/mercury-2.5-preview"

    const val LEGACY_OPENROUTER_DEFAULT_MODEL = "google/gemini-2.5-flash-lite"
    const val ZEN_DEFAULT_BASE_URL = "https://opencode.ai/zen/v1/chat/completions"
    const val ZEN_DEFAULT_MODEL = "space-bunny-free"
    const val DEFAULT_THEME_COLOR = 0xFFED5564L
    const val DEFAULT_AI_TEMPERATURE = 0.85f
    const val DEFAULT_AI_TOP_P = 0.95f
    @Deprecated("Artificial token limit removed across app")
    const val DEFAULT_AI_MAX_TOKENS = 250
    const val DEFAULT_AI_REASONING_EFFORT = "low"

    val DEFAULT_AI_SYSTEM_PROMPT: String
        get() = io.github.s1ddhants1.unhinge.ai.PromptRepository.getEffectiveSystemPromptTemplate()
}
