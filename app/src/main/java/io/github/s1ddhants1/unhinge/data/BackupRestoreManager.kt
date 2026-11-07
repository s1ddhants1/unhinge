package io.github.s1ddhants1.unhinge.data

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.model.CachedCandidateProfile
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class UnhingeSettingsBackup(
    val masterEnabled: Boolean = true,
    val blockFirebase: Boolean = true,
    val blockCrashUpload: Boolean = true,
    val blockPerf: Boolean = true,
    val blockAppsflyer: Boolean = true,
    val blockIncognia: Boolean = false,
    val blockSplitTelemetry: Boolean = true,
    val blockUbe: Boolean = false,
    val blockOkHttpTelemetry: Boolean = true,
    val fuzzLocation: Boolean = false,
    val blockContacts: Boolean = true,
    val blockMetricWorkers: Boolean = false,
    val blockGmsMeasurement: Boolean = true,
    val blockDataTransport: Boolean = true,
    val enableFeedNavigation: Boolean = true,
    val showAvailableLikes: Boolean = true,
    val aiProvider: String = "Zen",
    val openRouterApiKey: String = "",
    val openRouterBaseUrl: String = Consts.ZEN_DEFAULT_BASE_URL,
    val openRouterModel: String = Consts.ZEN_DEFAULT_MODEL,
    val aiCustomSystemPrompt: String = "",
    val aiOverrideSystemPrompt: Boolean = false,
    val showHostAppFab: Boolean = true,
    val aiTemperature: Float = Consts.DEFAULT_AI_TEMPERATURE,
    val aiTopP: Float = Consts.DEFAULT_AI_TOP_P,
    val aiReasoningEffort: String = Consts.DEFAULT_AI_REASONING_EFFORT,
    val pureBlack: Boolean = false,
    val themeMode: String = "SYSTEM",
    val themeColor: Long = Consts.DEFAULT_THEME_COLOR,
    val aiOpenerPromptTemplate: String = "",
    val aiPromptRemoteUrl: String = Consts.DEFAULT_AI_PROMPT_REMOTE_URL
)

@Serializable
data class UnhingeBackupBundle(
    val version: Int = 1,
    val app: String = "Unhinge",
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: UnhingeSettingsBackup? = null,
    val candidates: List<CachedCandidateProfile>? = null
)

data class ImportResult(
    val success: Boolean,
    val settingsRestored: Boolean,
    val candidatesImported: Int,
    val errorMessage: String? = null
)

object BackupRestoreManager {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportToJson(
        prefs: PreferencesManager?,
        candidateArchiveDb: CandidateArchiveDb?,
        includeSettings: Boolean = true,
        includeCandidates: Boolean = true
    ): String {
        val settingsBackup = if (includeSettings && prefs != null) prefs.exportSettings() else null
        val candidatesList = if (includeCandidates && candidateArchiveDb != null) candidateArchiveDb.getAllArchived() else null
        val bundle = UnhingeBackupBundle(
            version = 1,
            app = "Unhinge",
            exportedAt = System.currentTimeMillis(),
            settings = settingsBackup,
            candidates = candidatesList
        )
        return json.encodeToString(bundle)
    }

    fun importFromJson(
        context: Context?,
        jsonString: String,
        prefs: PreferencesManager?,
        candidateArchiveDb: CandidateArchiveDb?
    ): ImportResult {
        return try {
            val bundle = json.decodeFromString<UnhingeBackupBundle>(jsonString)
            var settingsRestored = false
            if (bundle.settings != null && prefs != null && context != null) {
                prefs.importSettings(context, bundle.settings)
                settingsRestored = true
            }

            var candidatesCount = 0
            if (!bundle.candidates.isNullOrEmpty() && candidateArchiveDb != null) {
                candidatesCount = candidateArchiveDb.upsertCandidates(bundle.candidates)
            }

            ImportResult(
                success = settingsRestored || candidatesCount > 0,
                settingsRestored = settingsRestored,
                candidatesImported = candidatesCount
            )
        } catch (e: Exception) {
            Log.e(Consts.TAG, "BackupRestoreManager: failed to import backup", e)
            ImportResult(
                success = false,
                settingsRestored = false,
                candidatesImported = 0,
                errorMessage = e.localizedMessage ?: "Invalid or incompatible backup format"
            )
        }
    }

    fun writeToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
                output.flush()
            }
            true
        } catch (e: Exception) {
            Log.e(Consts.TAG, "BackupRestoreManager: failed to write to URI: $uri", e)
            false
        }
    }

    fun readFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            Log.e(Consts.TAG, "BackupRestoreManager: failed to read from URI: $uri", e)
            null
        }
    }
}
