package io.github.s1ddhants1.unhinge.ai

import android.content.Context
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import io.github.s1ddhants1.unhinge.util.attempt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object PromptRepository {
    const val ASSET_OPENER_TEMPLATE_PATH = "prompts/opener_template.md"
    const val ASSET_SYSTEM_PROMPT_TEMPLATE_PATH = "prompts/system_prompt_template.md"
    const val ASSET_ASK_AI_TEMPLATE_PATH = "prompts/ask_ai_template.md"

    private val templateCache = ConcurrentHashMap<String, String>()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun loadTemplate(path: String, context: Context? = null): String {
        templateCache[path]?.let { return it }

        if (context != null) {
            val fromAssets = attempt("load $path from Android assets", silent = true) {
                context.assets.open(path).bufferedReader().use { it.readText() }
            }
            if (!fromAssets.isNullOrBlank()) {
                templateCache[path] = fromAssets
                return fromAssets
            }
        }

        val fromClassLoader = attempt("load $path from ClassLoader", silent = true) {
            PromptRepository::class.java.classLoader?.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
        }
        if (!fromClassLoader.isNullOrBlank()) {
            templateCache[path] = fromClassLoader
            return fromClassLoader
        }

        val candidatePaths = listOf(
            path,
            "../$path",
            "app/src/main/assets/$path",
            "src/main/assets/$path",
            "../../$path"
        )
        for (candidate in candidatePaths) {
            val file = File(candidate)
            if (file.exists() && file.isFile) {
                val content = attempt("load $candidate from file", silent = true) {
                    file.readText()
                }
                if (!content.isNullOrBlank()) {
                    templateCache[path] = content
                    return content
                }
            }
        }

        val fallback = getBuiltinFallback(path)
        templateCache[path] = fallback
        return fallback
    }

    private fun getBuiltinFallback(path: String): String = when (path) {
        ASSET_SYSTEM_PROMPT_TEMPLATE_PATH ->
            "You are a dating wingman AI assistant for Hinge. Every message must sound like a real, effortless human texting from their couch—never like an AI bot, copywriter, or pickup artist.\n{custom_instructions}\nOutput ONLY a valid JSON object {\"lines\": [...]} with EXACTLY {lineCount} opening replies, one per input prompt."
        ASSET_ASK_AI_TEMPLATE_PATH ->
            "You are an authentic dating wingman AI assistant for Hinge.\n\nCandidate Profile:\n{profile}\n\n=== EXHAUSTIVE DATING WINGMAN INSTRUCTIONS ===\nYou are an authentic, perceptive dating wingman texting on Hinge. All suggested lines and advice must sound 100% human—effortless, grounded, low-stakes, and completely free of AI copywriter or pickup-artist tropes.\n{custom_instructions}"
        else ->
            "Generate opening replies for the following candidate prompts.{avoid}\n\n{profile}Candidate Prompts ({lineCount} items):\n{prompts}\n\nOutput MUST be a JSON object {\"lines\": [...]} with EXACTLY {lineCount} opening replies, one per input prompt."
    }

    fun getEffectiveOpenerTemplate(
        prefs: PreferencesManager? = null,
        context: Context? = null,
    ): String {
        val userCustom = prefs?.aiOpenerPromptTemplate?.takeIf { it.isNotBlank() }
        if (userCustom != null) return userCustom
        return loadTemplate(ASSET_OPENER_TEMPLATE_PATH, context)
    }

    fun getEffectiveSystemPromptTemplate(
        prefs: PreferencesManager? = null,
        context: Context? = null,
    ): String {
        return loadTemplate(ASSET_SYSTEM_PROMPT_TEMPLATE_PATH, context)
    }

    fun getEffectiveAskAiTemplate(
        prefs: PreferencesManager? = null,
        context: Context? = null,
    ): String {
        return loadTemplate(ASSET_ASK_AI_TEMPLATE_PATH, context)
    }

    fun getRole(context: Context? = null): String {
        val systemTemplate = loadTemplate(ASSET_SYSTEM_PROMPT_TEMPLATE_PATH, context)
        return systemTemplate.lines().firstOrNull { it.isNotBlank() } ?: "You are a dating wingman AI assistant for Hinge."
    }

    fun formatOpenerUserPrompt(
        template: String,
        promptsText: String,
        avoidReplies: List<String> = emptyList(),
        profileBlock: String = "",
    ): String {
        val lineCount = promptsText.lines().size
        val avoid = if (avoidReplies.isNotEmpty()) {
            "\n\nPreviously generated replies (generate a completely fresh and DIFFERENT opener; do NOT repeat or reuse similar angles):\n" +
                avoidReplies.joinToString("\n") { "- \"$it\"" }
        } else ""
        val profile = profileBlock.takeIf { it.isNotBlank() }?.let { "Profile:\n$it\n\n" } ?: ""

        return template
            .replace("{avoid}", avoid)
            .replace("{profile}", profile)
            .replace("{lineCount}", lineCount.toString())
            .replace("{prompts}", promptsText)
    }

    fun formatOpenerSystemPrompt(
        template: String,
        lineCount: Int,
        customInstructions: String = "",
    ): String {
        val custom = if (customInstructions.isNotBlank()) {
            "\n" + customInstructions.replace("{lineCount}", lineCount.toString()) + "\n"
        } else "\n"
        return template
            .replace("{lineCount}", lineCount.toString())
            .replace("{custom_instructions}", custom)
            .trim()
    }

    fun formatAskAiSystemPrompt(
        template: String,
        profileSummary: String,
        customInstructions: String = "",
    ): String {
        val custom = if (customInstructions.isNotBlank()) {
            "\nAdditional System Instructions:\n$customInstructions\n"
        } else ""
        return template
            .replace("{profile}", profileSummary)
            .replace("{custom_instructions}", custom)
            .trim()
    }

    suspend fun fetchRemoteTemplate(
        url: String = Consts.DEFAULT_AI_PROMPT_REMOTE_URL,
        client: OkHttpClient = httpClient,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Unhinge-Android")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${resp.code}: ${resp.message}"))
                }
                val body = resp.body.string()
                if (body.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("Remote prompt template is empty"))
                }
                Result.success(body)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
