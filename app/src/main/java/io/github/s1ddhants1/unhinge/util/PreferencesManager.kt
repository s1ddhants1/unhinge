package io.github.s1ddhants1.unhinge.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import io.github.s1ddhants1.unhinge.Consts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.reflect.KProperty

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Stable
class PreferencesManager(
    val prefs: SharedPreferences? = null,
    private val isDynamic: Boolean = false,
    var backupPrefs: SharedPreferences? = null
) {
    private val preferenceSyncers = mutableMapOf<String, () -> Unit>()
    private var isInternalUpdate = false
    private var fallbackSyncJob: Job? = null
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
        if (isInternalUpdate) return@OnSharedPreferenceChangeListener
        if (changedKey != null) {
            preferenceSyncers[changedKey]?.invoke()
        } else {
            preferenceSyncers.values.forEach { it.invoke() }
        }
        rawUpdatedAt = prefs?.getLong("updated_at", rawUpdatedAt) ?: rawUpdatedAt
        onPreferenceChanged?.invoke()
    }

    init {
        attempt("register OnSharedPreferenceChangeListener", silent = true) {
            prefs?.registerOnSharedPreferenceChangeListener(changeListener)
        }
    }

    fun unregister() {
        attempt("unregister OnSharedPreferenceChangeListener", silent = true) {
            prefs?.unregisterOnSharedPreferenceChangeListener(changeListener)
        }
    }

    private class Preference<T>(
        private val isDynamic: Boolean,
        val key: String,
        private val defaultValue: T,
        private val getter: (key: String, defaultValue: T) -> T,
        private val setter: (key: String, newValue: T) -> Unit,
        private val hasRemoteKey: (key: String) -> Boolean = { false }
    ) {
        var value by mutableStateOf(getter(key, defaultValue))
            private set

        fun syncFromStorage() {
            value = getter(key, defaultValue)
        }

        operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
            if (isDynamic) {
                val current = getter(key, defaultValue)
                if (current is String && current.isBlank() && value is String && (value as String).isNotBlank()) {
                    return value
                }
                return current
            }
            return value
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: T) {
            value = newValue
            setter(key, newValue)
        }
    }

    private fun getString(key: String, defaultValue: String): String {
        val remoteVal = prefs?.getString(key, null)
        if (!remoteVal.isNullOrBlank()) return remoteVal
        val backupVal = backupPrefs?.getString(key, null)
        if (!backupVal.isNullOrBlank()) return backupVal
        return remoteVal ?: backupVal ?: defaultValue
    }

    private fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        if (prefs?.contains(key) == true) {
            return prefs.getBoolean(key, defaultValue)
        }
        val bp = backupPrefs
        if (bp?.contains(key) == true) {
            return bp.getBoolean(key, defaultValue)
        }
        return defaultValue
    }

    private fun getLong(key: String, defaultValue: Long): Long {
        if (prefs?.contains(key) == true) {
            return prefs.getLong(key, defaultValue)
        }
        val bp = backupPrefs
        if (bp?.contains(key) == true) {
            return bp.getLong(key, defaultValue)
        }
        return defaultValue
    }

    private fun getFloat(key: String, defaultValue: Float): Float {
        if (prefs?.contains(key) == true) {
            return prefs.getFloat(key, defaultValue)
        }
        val bp = backupPrefs
        if (bp?.contains(key) == true) {
            return bp.getFloat(key, defaultValue)
        }
        return defaultValue
    }

    private fun getInt(key: String, defaultValue: Int): Int {
        if (prefs?.contains(key) == true) {
            return prefs.getInt(key, defaultValue)
        }
        val bp = backupPrefs
        if (bp?.contains(key) == true) {
            return bp.getInt(key, defaultValue)
        }
        return defaultValue
    }

    var onPreferenceChanged: (() -> Unit)? = null

    private var rawUpdatedAt: Long = prefs?.getLong("updated_at", 0L) ?: 0L

    var updatedAt: Long
        get() = prefs?.getLong("updated_at", rawUpdatedAt) ?: rawUpdatedAt
        set(value) {
            rawUpdatedAt = value
            attempt("save preference long updated_at to remote", silent = true) {
                prefs?.edit()?.putLong("updated_at", value)?.apply()
            }
            attempt("save preference long updated_at to backup", silent = true) {
                backupPrefs?.edit()?.putLong("updated_at", value)?.apply()
            }
        }

    private fun putBoolean(key: String, value: Boolean) {
        val now = System.currentTimeMillis()
        isInternalUpdate = true
        try {
            attempt("save preference boolean $key to remote", silent = true) {
                prefs?.edit()?.putBoolean(key, value)?.putLong("updated_at", now)?.apply()
            }
            attempt("save preference boolean $key to backup", silent = true) {
                backupPrefs?.edit()?.putBoolean(key, value)?.putLong("updated_at", now)?.apply()
            }
            rawUpdatedAt = now
            preferenceSyncers[key]?.invoke()
        } finally {
            isInternalUpdate = false
        }
        onPreferenceChanged?.invoke()
    }

    private fun putString(key: String, value: String) {
        val now = System.currentTimeMillis()
        isInternalUpdate = true
        try {
            attempt("save preference string $key to remote", silent = true) {
                prefs?.edit()?.putString(key, value)?.putLong("updated_at", now)?.apply()
            }
            attempt("save preference string $key to backup", silent = true) {
                backupPrefs?.edit()?.putString(key, value)?.putLong("updated_at", now)?.apply()
            }
            rawUpdatedAt = now
            preferenceSyncers[key]?.invoke()
        } finally {
            isInternalUpdate = false
        }
        onPreferenceChanged?.invoke()
    }

    private fun putLong(key: String, value: Long) {
        val now = System.currentTimeMillis()
        isInternalUpdate = true
        try {
            attempt("save preference long $key to remote", silent = true) {
                prefs?.edit()?.putLong(key, value)?.putLong("updated_at", now)?.apply()
            }
            attempt("save preference long $key to backup", silent = true) {
                backupPrefs?.edit()?.putLong(key, value)?.putLong("updated_at", now)?.apply()
            }
            rawUpdatedAt = now
            preferenceSyncers[key]?.invoke()
        } finally {
            isInternalUpdate = false
        }
        onPreferenceChanged?.invoke()
    }

    private fun putFloat(key: String, value: Float) {
        val now = System.currentTimeMillis()
        isInternalUpdate = true
        try {
            attempt("save preference float $key to remote", silent = true) {
                prefs?.edit()?.putFloat(key, value)?.putLong("updated_at", now)?.apply()
            }
            attempt("save preference float $key to backup", silent = true) {
                backupPrefs?.edit()?.putFloat(key, value)?.putLong("updated_at", now)?.apply()
            }
            rawUpdatedAt = now
            preferenceSyncers[key]?.invoke()
        } finally {
            isInternalUpdate = false
        }
        onPreferenceChanged?.invoke()
    }

    private fun putInt(key: String, value: Int) {
        val now = System.currentTimeMillis()
        isInternalUpdate = true
        try {
            attempt("save preference int $key to remote", silent = true) {
                prefs?.edit()?.putInt(key, value)?.putLong("updated_at", now)?.apply()
            }
            attempt("save preference int $key to backup", silent = true) {
                backupPrefs?.edit()?.putInt(key, value)?.putLong("updated_at", now)?.apply()
            }
            rawUpdatedAt = now
            preferenceSyncers[key]?.invoke()
        } finally {
            isInternalUpdate = false
        }
        onPreferenceChanged?.invoke()
    }

    private fun <T> registerPreference(pref: Preference<T>): Preference<T> {
        preferenceSyncers[pref.key] = { pref.syncFromStorage() }
        return pref
    }

    private fun booleanPreference(key: String, defaultValue: Boolean = false) =
        registerPreference(Preference(isDynamic, key, defaultValue, ::getBoolean, ::putBoolean) { prefs?.contains(it) == true })

    private fun stringPreference(key: String, defaultValue: String = "") =
        registerPreference(Preference(isDynamic, key, defaultValue, ::getString, ::putString) { prefs?.contains(it) == true })

    private fun longPreference(key: String, defaultValue: Long = 0L) =
        registerPreference(Preference(isDynamic, key, defaultValue, ::getLong, ::putLong) { prefs?.contains(it) == true })

    private fun floatPreference(key: String, defaultValue: Float = 0f) =
        registerPreference(Preference(isDynamic, key, defaultValue, ::getFloat, ::putFloat) { prefs?.contains(it) == true })

    private fun intPreference(key: String, defaultValue: Int = 0) =
        registerPreference(Preference(isDynamic, key, defaultValue, ::getInt, ::putInt) { prefs?.contains(it) == true })

    // Privacy Toggles
    var masterEnabled by booleanPreference(Consts.PREF_MASTER_ENABLED, true)
    var blockFirebase by booleanPreference(Consts.PREF_BLOCK_FIREBASE, true)
    var blockCrashUpload by booleanPreference(Consts.PREF_BLOCK_CRASH_UPLOAD, true)
    var blockPerf by booleanPreference(Consts.PREF_BLOCK_PERF, true)
    var blockAppsflyer by booleanPreference(Consts.PREF_BLOCK_APPSFLYER, true)
    var blockIncognia by booleanPreference(Consts.PREF_BLOCK_INCOGNIA, false)
    var blockSplitTelemetry by booleanPreference(Consts.PREF_BLOCK_SPLIT, true)
    var blockUbe by booleanPreference(Consts.PREF_BLOCK_UBE, false)
    var blockOkHttpTelemetry by booleanPreference(Consts.PREF_BLOCK_OKHTTP, true)
    var fuzzLocation by booleanPreference(Consts.PREF_FUZZ_LOCATION, false)
    var blockContacts by booleanPreference(Consts.PREF_BLOCK_CONTACTS, true)
    var blockMetricWorkers by booleanPreference(Consts.PREF_BLOCK_METRIC_WORKERS, false)
    var blockGmsMeasurement by booleanPreference(Consts.PREF_BLOCK_GMS_MEASUREMENT, true)
    var blockDataTransport by booleanPreference(Consts.PREF_BLOCK_DATATRANSPORT, true)

    // Feed Navigation
    var enableFeedNavigation by booleanPreference(Consts.PREF_ENABLE_FEED_NAVIGATION, true)

    // AI Wingman & Theme Preferences
    // Zen free tier needs no credentials, so a fresh install is AI-ready without onboarding.
    var aiProvider by stringPreference(Consts.PREF_AI_PROVIDER, "Zen")
    var openRouterApiKey by stringPreference(Consts.PREF_OPENROUTER_API_KEY, "")
    var openRouterBaseUrl by stringPreference(Consts.PREF_OPENROUTER_BASE_URL, Consts.ZEN_DEFAULT_BASE_URL)
    var openRouterModel by stringPreference(Consts.PREF_OPENROUTER_MODEL, Consts.ZEN_DEFAULT_MODEL)
    var aiCustomSystemPrompt by stringPreference(Consts.PREF_AI_CUSTOM_SYSTEM_PROMPT, "")
    var aiOverrideSystemPrompt by booleanPreference(Consts.PREF_AI_OVERRIDE_SYSTEM_PROMPT, false)
    var showHostAppFab by booleanPreference(Consts.PREF_SHOW_HOST_APP_FAB, true)
    var aiTemperature by floatPreference(Consts.PREF_AI_TEMPERATURE, Consts.DEFAULT_AI_TEMPERATURE)
    var aiTopP by floatPreference(Consts.PREF_AI_TOP_P, Consts.DEFAULT_AI_TOP_P)
    var aiMaxTokens by intPreference(Consts.PREF_AI_MAX_TOKENS, Consts.DEFAULT_AI_MAX_TOKENS)
    var pureBlack by booleanPreference(Consts.PREF_PURE_BLACK, false)
    var themeColor by longPreference(Consts.PREF_THEME_COLOR, Consts.DEFAULT_THEME_COLOR)
    var themeModeString by stringPreference(Consts.PREF_THEME_MODE, ThemeMode.SYSTEM.name)

    var themeMode: ThemeMode
        get() = try {
            ThemeMode.valueOf(themeModeString)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
        set(value) {
            themeModeString = value.name
        }

    fun isEffective(flag: Boolean): Boolean = masterEnabled && flag

    fun ensureBackupPrefs(ctx: Context) {
        if (backupPrefs == null) {
            backupPrefs = ctx.getSharedPreferences(Consts.PREFS_SETTINGS, Context.MODE_PRIVATE)
        }
        if (prefs == null || prefs.all.isEmpty()) {
            loadFromFallbackStorage(ctx)
        }
    }

    fun loadFromFallbackStorage(ctx: Context) {
        attempt("load preferences from fallback storage", silent = true) {
            val localPrefs = ctx.getSharedPreferences(Consts.PREFS_SETTINGS, Context.MODE_PRIVATE)
            if (backupPrefs == null) {
                backupPrefs = localPrefs
            }
            if (localPrefs.all.isNotEmpty()) {
                val apiKeyInStorage = localPrefs.getString(Consts.PREF_OPENROUTER_API_KEY, "") ?: ""
                if (apiKeyInStorage.isNotBlank()) {
                    openRouterApiKey = apiKeyInStorage
                }
                val baseUrlInStorage = localPrefs.getString(Consts.PREF_OPENROUTER_BASE_URL, "") ?: ""
                if (baseUrlInStorage.isNotBlank()) {
                    openRouterBaseUrl = baseUrlInStorage
                }
                val modelInStorage = localPrefs.getString(Consts.PREF_OPENROUTER_MODEL, "") ?: ""
                if (modelInStorage.isNotBlank()) {
                    openRouterModel = modelInStorage
                }
                val customPromptInStorage = localPrefs.getString(Consts.PREF_AI_CUSTOM_SYSTEM_PROMPT, "") ?: ""
                if (customPromptInStorage.isNotBlank()) {
                    aiCustomSystemPrompt = customPromptInStorage
                }
                aiOverrideSystemPrompt = localPrefs.getBoolean(Consts.PREF_AI_OVERRIDE_SYSTEM_PROMPT, aiOverrideSystemPrompt)
                showHostAppFab = localPrefs.getBoolean(Consts.PREF_SHOW_HOST_APP_FAB, showHostAppFab)
                aiTemperature = localPrefs.getFloat(Consts.PREF_AI_TEMPERATURE, aiTemperature)
                aiTopP = localPrefs.getFloat(Consts.PREF_AI_TOP_P, aiTopP)
                aiMaxTokens = localPrefs.getInt(Consts.PREF_AI_MAX_TOKENS, aiMaxTokens)
                pureBlack = localPrefs.getBoolean(Consts.PREF_PURE_BLACK, pureBlack)
                themeColor = localPrefs.getLong(Consts.PREF_THEME_COLOR, themeColor)
                themeModeString = localPrefs.getString(Consts.PREF_THEME_MODE, themeModeString) ?: themeModeString

                // Privacy toggles
                masterEnabled = localPrefs.getBoolean(Consts.PREF_MASTER_ENABLED, masterEnabled)
                blockFirebase = localPrefs.getBoolean(Consts.PREF_BLOCK_FIREBASE, blockFirebase)
                blockCrashUpload = localPrefs.getBoolean(Consts.PREF_BLOCK_CRASH_UPLOAD, blockCrashUpload)
                blockPerf = localPrefs.getBoolean(Consts.PREF_BLOCK_PERF, blockPerf)
                blockAppsflyer = localPrefs.getBoolean(Consts.PREF_BLOCK_APPSFLYER, blockAppsflyer)
                blockIncognia = localPrefs.getBoolean(Consts.PREF_BLOCK_INCOGNIA, blockIncognia)
                blockSplitTelemetry = localPrefs.getBoolean(Consts.PREF_BLOCK_SPLIT, blockSplitTelemetry)
                blockUbe = localPrefs.getBoolean(Consts.PREF_BLOCK_UBE, blockUbe)
                blockOkHttpTelemetry = localPrefs.getBoolean(Consts.PREF_BLOCK_OKHTTP, blockOkHttpTelemetry)
                fuzzLocation = localPrefs.getBoolean(Consts.PREF_FUZZ_LOCATION, fuzzLocation)
                blockContacts = localPrefs.getBoolean(Consts.PREF_BLOCK_CONTACTS, blockContacts)
                blockMetricWorkers = localPrefs.getBoolean(Consts.PREF_BLOCK_METRIC_WORKERS, blockMetricWorkers)
                blockGmsMeasurement = localPrefs.getBoolean(Consts.PREF_BLOCK_GMS_MEASUREMENT, blockGmsMeasurement)
                blockDataTransport = localPrefs.getBoolean(Consts.PREF_BLOCK_DATATRANSPORT, blockDataTransport)
                enableFeedNavigation = localPrefs.getBoolean(Consts.PREF_ENABLE_FEED_NAVIGATION, enableFeedNavigation)
                val providerInStorage = localPrefs.getString(Consts.PREF_AI_PROVIDER, "") ?: ""
                if (providerInStorage.isNotBlank()) {
                    aiProvider = providerInStorage
                }
            }
        }
    }

    fun saveToFallbackStorageAsync(ctx: Context) {
        fallbackSyncJob?.cancel()
        val appContext = ctx.applicationContext
        fallbackSyncJob = syncScope.launch {
            delay(500)
            attempt("save preferences to fallback storage", silent = true) {
                val localPrefs = appContext.getSharedPreferences(Consts.PREFS_SETTINGS, Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                isInternalUpdate = true
                try {
                    localPrefs.edit()
                        .putBoolean(Consts.PREF_MASTER_ENABLED, masterEnabled)
                        .putBoolean(Consts.PREF_BLOCK_FIREBASE, blockFirebase)
                        .putBoolean(Consts.PREF_BLOCK_CRASH_UPLOAD, blockCrashUpload)
                        .putBoolean(Consts.PREF_BLOCK_PERF, blockPerf)
                        .putBoolean(Consts.PREF_BLOCK_APPSFLYER, blockAppsflyer)
                        .putBoolean(Consts.PREF_BLOCK_INCOGNIA, blockIncognia)
                        .putBoolean(Consts.PREF_BLOCK_SPLIT, blockSplitTelemetry)
                        .putBoolean(Consts.PREF_BLOCK_UBE, blockUbe)
                        .putBoolean(Consts.PREF_BLOCK_OKHTTP, blockOkHttpTelemetry)
                        .putBoolean(Consts.PREF_FUZZ_LOCATION, fuzzLocation)
                        .putBoolean(Consts.PREF_BLOCK_CONTACTS, blockContacts)
                        .putBoolean(Consts.PREF_BLOCK_METRIC_WORKERS, blockMetricWorkers)
                        .putBoolean(Consts.PREF_BLOCK_GMS_MEASUREMENT, blockGmsMeasurement)
                        .putBoolean(Consts.PREF_BLOCK_DATATRANSPORT, blockDataTransport)
                        .putBoolean(Consts.PREF_ENABLE_FEED_NAVIGATION, enableFeedNavigation)
                        .putString(Consts.PREF_AI_PROVIDER, aiProvider)
                        .putString(Consts.PREF_OPENROUTER_API_KEY, openRouterApiKey)
                        .putString(Consts.PREF_OPENROUTER_BASE_URL, openRouterBaseUrl)
                        .putString(Consts.PREF_OPENROUTER_MODEL, openRouterModel)
                        .putString(Consts.PREF_AI_CUSTOM_SYSTEM_PROMPT, aiCustomSystemPrompt)
                        .putBoolean(Consts.PREF_AI_OVERRIDE_SYSTEM_PROMPT, aiOverrideSystemPrompt)
                        .putBoolean(Consts.PREF_SHOW_HOST_APP_FAB, showHostAppFab)
                        .putFloat(Consts.PREF_AI_TEMPERATURE, aiTemperature)
                        .putFloat(Consts.PREF_AI_TOP_P, aiTopP)
                        .putInt(Consts.PREF_AI_MAX_TOKENS, aiMaxTokens)
                        .putBoolean(Consts.PREF_PURE_BLACK, pureBlack)
                        .putLong(Consts.PREF_THEME_COLOR, themeColor)
                        .putString(Consts.PREF_THEME_MODE, themeModeString)
                        .putLong("updated_at", now)
                        .apply()

                    // Ensure remote preferences are flushed if present
                    prefs?.let { remote ->
                        attempt("commit all preferences to remote", silent = true) {
                            remote.edit()
                                .putBoolean(Consts.PREF_MASTER_ENABLED, masterEnabled)
                                .putBoolean(Consts.PREF_BLOCK_FIREBASE, blockFirebase)
                                .putBoolean(Consts.PREF_BLOCK_CRASH_UPLOAD, blockCrashUpload)
                                .putBoolean(Consts.PREF_BLOCK_PERF, blockPerf)
                                .putBoolean(Consts.PREF_BLOCK_APPSFLYER, blockAppsflyer)
                                .putBoolean(Consts.PREF_BLOCK_INCOGNIA, blockIncognia)
                                .putBoolean(Consts.PREF_BLOCK_SPLIT, blockSplitTelemetry)
                                .putBoolean(Consts.PREF_BLOCK_UBE, blockUbe)
                                .putBoolean(Consts.PREF_BLOCK_OKHTTP, blockOkHttpTelemetry)
                                .putBoolean(Consts.PREF_FUZZ_LOCATION, fuzzLocation)
                                .putBoolean(Consts.PREF_BLOCK_CONTACTS, blockContacts)
                                .putBoolean(Consts.PREF_BLOCK_METRIC_WORKERS, blockMetricWorkers)
                                .putBoolean(Consts.PREF_BLOCK_GMS_MEASUREMENT, blockGmsMeasurement)
                                .putBoolean(Consts.PREF_BLOCK_DATATRANSPORT, blockDataTransport)
                                .putBoolean(Consts.PREF_ENABLE_FEED_NAVIGATION, enableFeedNavigation)
                                .putString(Consts.PREF_AI_PROVIDER, aiProvider)
                                .putString(Consts.PREF_OPENROUTER_API_KEY, openRouterApiKey)
                                .putString(Consts.PREF_OPENROUTER_BASE_URL, openRouterBaseUrl)
                                .putString(Consts.PREF_OPENROUTER_MODEL, openRouterModel)
                                .putString(Consts.PREF_AI_CUSTOM_SYSTEM_PROMPT, aiCustomSystemPrompt)
                                .putBoolean(Consts.PREF_AI_OVERRIDE_SYSTEM_PROMPT, aiOverrideSystemPrompt)
                                .putBoolean(Consts.PREF_SHOW_HOST_APP_FAB, showHostAppFab)
                                .putFloat(Consts.PREF_AI_TEMPERATURE, aiTemperature)
                                .putFloat(Consts.PREF_AI_TOP_P, aiTopP)
                                .putInt(Consts.PREF_AI_MAX_TOKENS, aiMaxTokens)
                                .putBoolean(Consts.PREF_PURE_BLACK, pureBlack)
                                .putLong(Consts.PREF_THEME_COLOR, themeColor)
                                .putString(Consts.PREF_THEME_MODE, themeModeString)
                                .putLong("updated_at", now)
                                .apply()
                        }
                    }
                } finally {
                    isInternalUpdate = false
                }

                // Fallback sync to Hinge target shared_prefs directory via root if available (strictly on background syncScope)
                attempt("sync privacy_settings.xml to Hinge app via su", silent = true) {
                    val src = "/data/data/io.github.s1ddhants1/unhinge/shared_prefs/privacy_settings.xml"
                    val dst = "/data/data/co.hinge.app/shared_prefs/privacy_settings.xml"
                    io.github.s1ddhants1.unhinge.data.SuStorageReader.executeSu(
                        "if [ -d /data/data/co.hinge.app ]; then mkdir -p /data/data/co.hinge.app/shared_prefs && cp $src $dst && chmod 660 $dst && chown \$(stat -c '%u:%g' /data/data/co.hinge.app) $dst; fi"
                    )
                }
            }
        }
    }
}
