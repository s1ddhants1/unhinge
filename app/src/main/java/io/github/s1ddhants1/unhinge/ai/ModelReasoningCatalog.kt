package io.github.s1ddhants1.unhinge.ai

import java.util.Locale

object ModelReasoningCatalog {

    sealed class ReasoningSupport {

        data class Effort(val levels: List<String>) : ReasoningSupport()

        object Toggle : ReasoningSupport()

        data class BudgetTokens(val minTokens: Int = 1024, val maxTokens: Int = 81920) : ReasoningSupport()

        object Fixed : ReasoningSupport()

        object Unsupported : ReasoningSupport()
    }

    private val MODEL_CAPABILITIES: Map<String, ReasoningSupport> = mapOf(

        "space-bunny-free" to ReasoningSupport.Effort(listOf("low", "medium", "high", "xhigh", "max")),
        "muse-spark-1.3-contributor-free" to ReasoningSupport.Effort(listOf("minimal", "low", "medium", "high", "xhigh")),
        "muse-spark-1.3-free" to ReasoningSupport.Effort(listOf("minimal", "low", "medium", "high", "xhigh")),
        "muse-spark-1.2-contributor-free" to ReasoningSupport.Effort(listOf("minimal", "low", "medium", "high", "xhigh")),
        "muse-spark-1.2-free" to ReasoningSupport.Effort(listOf("minimal", "low", "medium", "high", "xhigh")),
        "big-pickle" to ReasoningSupport.Fixed,
        "fledge-alpha-free" to ReasoningSupport.Effort(listOf("low", "high", "max")),
        "ling-3.1-flash-free" to ReasoningSupport.Toggle,
        "longcat-2.5-preview-free" to ReasoningSupport.Toggle,
        "mimo-v2.6-flash-free" to ReasoningSupport.Fixed,
        "mimo-v2.5-free" to ReasoningSupport.Fixed,
        "nemotron-3-ultra-free" to ReasoningSupport.Fixed,
        "nemotron-3.5-lightning-free" to ReasoningSupport.Fixed,

        "muse-spark-1.3" to ReasoningSupport.Effort(listOf("minimal", "low", "medium", "high", "xhigh")),
        "muse-spark-1.2" to ReasoningSupport.Effort(listOf("minimal", "low", "medium", "high", "xhigh")),
        "claude-sonnet-5" to ReasoningSupport.Effort(listOf("none", "low", "medium", "high", "xhigh", "max")),
        "claude-opus-5" to ReasoningSupport.Effort(listOf("low", "medium", "high", "xhigh", "max")),
        "claude-fable-5-1" to ReasoningSupport.Effort(listOf("low", "medium", "high", "xhigh", "max")),
        "claude-fable-5" to ReasoningSupport.Effort(listOf("low", "medium", "high", "xhigh", "max")),
        "claude-haiku-4-5" to ReasoningSupport.BudgetTokens(minTokens = 1024),
        "gpt-5.6-sol" to ReasoningSupport.Effort(listOf("none", "low", "medium", "high", "xhigh", "max")),
        "gpt-5.6-terra" to ReasoningSupport.Effort(listOf("none", "low", "medium", "high", "xhigh", "max")),
        "gpt-5.6-luna" to ReasoningSupport.Effort(listOf("none", "low", "medium", "high", "xhigh", "max")),
        "gpt-6-sol" to ReasoningSupport.Effort(listOf("none", "low", "medium", "high", "xhigh", "max")),
        "gpt-6-luna" to ReasoningSupport.Effort(listOf("none", "low", "medium", "high", "xhigh", "max")),
        "gpt-6-astra" to ReasoningSupport.Effort(listOf("low", "medium", "high", "xhigh", "max")),
        "gpt-6.1-sol" to ReasoningSupport.Effort(listOf("low", "medium", "high", "xhigh", "max")),
        "gpt-5.5" to ReasoningSupport.Effort(listOf("none", "low", "medium", "high", "xhigh")),
        "gpt-5.4" to ReasoningSupport.Effort(listOf("none", "low", "medium", "high", "xhigh")),
        "gpt-5" to ReasoningSupport.Effort(listOf("minimal", "low", "medium", "high")),
        "gemini-3.8-flash" to ReasoningSupport.Effort(listOf("low", "medium", "high")),
        "gemini-3.7-flash" to ReasoningSupport.Effort(listOf("low", "medium", "high")),
        "gemini-3.6-flash" to ReasoningSupport.Effort(listOf("minimal", "low", "medium", "high")),
        "gemini-3.5-flash" to ReasoningSupport.Effort(listOf("minimal", "low", "medium", "high")),
        "gemini-3.1-pro" to ReasoningSupport.Effort(listOf("low", "medium", "high")),
        "gemini-3-pro" to ReasoningSupport.Effort(listOf("low", "high")),
        "deepseek-v4-flash" to ReasoningSupport.Effort(listOf("none", "low", "high", "max")),
        "deepseek-v4-pro" to ReasoningSupport.Effort(listOf("none", "high", "max")),
        "glm-5.3-flash" to ReasoningSupport.Effort(listOf("low", "high", "max")),
        "glm-5.2" to ReasoningSupport.Effort(listOf("high", "max")),
        "qwen3.8-flash" to ReasoningSupport.Effort(listOf("low", "medium", "xhigh")),
        "kimi-k2.5" to ReasoningSupport.Toggle,
        "kimi-k3" to ReasoningSupport.Effort(listOf("max")),
        "minimax-m3" to ReasoningSupport.Fixed,

        "gpt-5.6-sol-pro" to ReasoningSupport.Effort(listOf("none", "low", "medium", "high", "xhigh", "max")),
        "gpt-5.5-2026-04-23" to ReasoningSupport.Effort(listOf("none", "low", "medium", "high", "xhigh")),
        "gpt-5.4-2026-03-05" to ReasoningSupport.Effort(listOf("none", "low", "medium", "high", "xhigh")),
        "o1" to ReasoningSupport.Effort(listOf("low", "medium", "high")),
        "o1-mini" to ReasoningSupport.Effort(listOf("low", "medium", "high")),
        "o1-pro" to ReasoningSupport.Effort(listOf("low", "medium", "high")),
        "o3" to ReasoningSupport.Effort(listOf("low", "medium", "high")),
        "o3-mini" to ReasoningSupport.Effort(listOf("low", "medium", "high")),
        "o4-mini" to ReasoningSupport.Effort(listOf("low", "medium", "high")),

        "claude-opus-5-5" to ReasoningSupport.Effort(listOf("low", "medium", "high", "xhigh", "max")),
        "claude-sonnet-5-5" to ReasoningSupport.Effort(listOf("low", "medium", "high", "xhigh", "max")),
        "claude-haiku-4-5-20251001" to ReasoningSupport.BudgetTokens(minTokens = 1024),
        "claude-3-7-sonnet" to ReasoningSupport.Effort(listOf("none", "low", "medium", "high", "xhigh", "max")),
        "claude-3-5-haiku" to ReasoningSupport.Unsupported,

        "gemini-flash-lite-latest" to ReasoningSupport.Effort(listOf("minimal", "low", "medium", "high")),
        "gemini-flash-latest" to ReasoningSupport.Effort(listOf("low", "medium", "high")),
        "gemini-pro-latest" to ReasoningSupport.Unsupported,
        "gemini-2.5-flash" to ReasoningSupport.Effort(listOf("none", "low", "medium", "high")),
        "gemini-2.5-pro" to ReasoningSupport.BudgetTokens(minTokens = 128, maxTokens = 32768),

        "sonar" to ReasoningSupport.Unsupported,
        "sonar-pro" to ReasoningSupport.Unsupported,
        "sonar-reasoning-pro" to ReasoningSupport.Effort(listOf("minimal", "low", "medium", "high")),

        "grok-4.3" to ReasoningSupport.Effort(listOf("none", "low", "medium", "high")),
        "grok-4.5" to ReasoningSupport.Effort(listOf("low", "medium", "high")),
        "grok-4.6" to ReasoningSupport.Effort(listOf("low", "medium", "high", "xhigh")),
        "grok-4.7" to ReasoningSupport.Effort(listOf("low", "medium", "high", "xhigh")),
        "grok-4.1-fast" to ReasoningSupport.Unsupported,

        "mistral-large-latest" to ReasoningSupport.Unsupported,
        "mistral-tiny-latest" to ReasoningSupport.Unsupported,
        "mistral-medium-latest" to ReasoningSupport.Effort(listOf("none", "high")),
        "mistral-small-latest" to ReasoningSupport.Effort(listOf("none", "high")),

        "mercury-2" to ReasoningSupport.Effort(listOf("low", "medium", "high")),
        "mercury-2.5-preview" to ReasoningSupport.Effort(listOf("none", "low", "medium", "high")),

        "inception/mercury-2.5-preview" to ReasoningSupport.Effort(listOf("none", "low", "medium", "high")),
        "meta/muse-spark-1.3" to ReasoningSupport.Effort(listOf("minimal", "low", "medium", "high", "xhigh", "max")),
        "z-ai/glm-5.3-flash" to ReasoningSupport.Effort(listOf("low", "high", "max")),
        "qwen/qwen3.8-flash" to ReasoningSupport.Effort(listOf("low", "medium", "xhigh")),
        "deepseek/deepseek-v4-flash-latest" to ReasoningSupport.Effort(listOf("low", "high", "max")),
        "openai/gpt-mini-latest" to ReasoningSupport.Effort(listOf("minimal", "low", "medium", "high")),
        "openai/gpt-oss-120b" to ReasoningSupport.Effort(listOf("low", "medium", "high")),
        "google/gemini-flash-latest" to ReasoningSupport.Effort(listOf("low", "medium", "high"))
    )

    private val EFFORT_ORDER = listOf("none", "off", "minimal", "low", "medium", "high", "xhigh", "max")

    fun getReasoningSupport(model: String, provider: String = ""): ReasoningSupport {
        val normalized = normalizeModelId(model)
        if (normalized.isBlank()) {
            return ReasoningSupport.Effort(listOf("low", "medium", "high", "max"))
        }

        MODEL_CAPABILITIES[normalized]?.let { return it }

        val bareModel = if (normalized.contains('/')) normalized.substringAfterLast('/') else normalized
        MODEL_CAPABILITIES[bareModel]?.let { return it }

        val strippedDate = bareModel.replace(Regex("-\\d{4}-?\\d{2}-?\\d{2}$"), "")
        MODEL_CAPABILITIES[strippedDate]?.let { return it }

        if (provider.equals("Zen", ignoreCase = true) || ZenRouter.isFreeModel(normalized)) {
            if (normalized.contains("free")) {
                return when {
                    normalized.contains("bunny") -> ReasoningSupport.Effort(listOf("low", "medium", "high", "xhigh", "max"))
                    normalized.contains("fledge") -> ReasoningSupport.Effort(listOf("low", "high", "max"))
                    normalized.contains("ling") || normalized.contains("longcat") -> ReasoningSupport.Toggle
                    normalized.contains("pickle") || normalized.contains("mimo") || normalized.contains("nemotron") -> ReasoningSupport.Fixed
                    else -> ReasoningSupport.Effort(listOf("low", "medium", "high"))
                }
            }
        }

        val lower = normalized.lowercase(Locale.US)
        if (lower.contains("thinking") || lower.contains("reasoning") ||
            lower.contains("o1") || lower.contains("o3") || lower.contains("o4") ||
            lower.contains("r1")
        ) {
            return ReasoningSupport.Effort(listOf("low", "medium", "high"))
        }

        if (lower.contains("haiku") || lower.contains("sonar") || lower.contains("tiny") ||
            lower.contains("large-latest") || lower.contains("pro-latest")
        ) {
            return ReasoningSupport.Unsupported
        }

        return ReasoningSupport.Effort(listOf("none", "low", "medium", "high", "max"))
    }

    fun getAvailableOptions(model: String, provider: String = ""): List<Pair<String, String>> {
        return when (val support = getReasoningSupport(model, provider)) {
            is ReasoningSupport.Unsupported -> emptyList()
            is ReasoningSupport.Fixed -> emptyList()
            is ReasoningSupport.Toggle -> listOf(
                "default" to "Default (Provider Auto)",
                "on" to "Enabled (Thinking On)",
                "off" to "Disabled (Thinking Off)"
            )
            is ReasoningSupport.BudgetTokens -> listOf(
                "default" to "Default (Provider Auto)",
                "low" to "Low (~1,024 tokens)",
                "medium" to "Medium (~4,096 tokens)",
                "high" to "High (~8,192 tokens)",
                "max" to "Max (~16,384 tokens)"
            )
            is ReasoningSupport.Effort -> {
                val list = mutableListOf("default" to "Default (Provider Auto)")
                for (lvl in support.levels) {
                    list.add(lvl to levelLabel(lvl))
                }
                list
            }
        }
    }

    fun sanitizeReasoningEffort(userEffort: String, model: String, provider: String = ""): String? {
        val trimmed = userEffort.trim().lowercase(Locale.US)
        if (trimmed.isEmpty() || trimmed == "default") return null

        return when (val support = getReasoningSupport(model, provider)) {
            is ReasoningSupport.Unsupported, ReasoningSupport.Fixed -> null
            is ReasoningSupport.Toggle -> {
                if (trimmed == "off" || trimmed == "none") "off" else "on"
            }
            is ReasoningSupport.BudgetTokens -> {
                if (trimmed in listOf("low", "medium", "high", "xhigh", "max")) trimmed else null
            }
            is ReasoningSupport.Effort -> {
                if (trimmed in support.levels) {
                    trimmed
                } else {
                    clampEffort(trimmed, support.levels)
                }
            }
        }
    }

    fun resolveBudgetTokens(userEffort: String, model: String): Int? {
        val support = getReasoningSupport(model)
        if (support !is ReasoningSupport.BudgetTokens) return null
        val trimmed = userEffort.trim().lowercase(Locale.US)
        return when (trimmed) {
            "low", "minimal" -> maxOf(support.minTokens, 1024)
            "medium" -> 4096
            "high" -> 8192
            "xhigh" -> 16384
            "max" -> minOf(support.maxTokens, 32768)
            else -> null
        }
    }

    fun getEffortSummary(userEffort: String, model: String, provider: String = ""): String {
        return when (val support = getReasoningSupport(model, provider)) {
            is ReasoningSupport.Unsupported -> "Not supported by this model"
            is ReasoningSupport.Fixed -> "Built-in reasoning (fixed by model)"
            is ReasoningSupport.Toggle -> {
                when (userEffort.trim().lowercase(Locale.US)) {
                    "off", "none" -> "Disabled (Thinking Off)"
                    "on" -> "Enabled (Thinking On)"
                    "default" -> "Default (Provider Auto)"
                    else -> "Enabled (${levelLabel(userEffort)})"
                }
            }
            is ReasoningSupport.BudgetTokens -> {
                val eff = userEffort.trim().lowercase(Locale.US)
                if (eff == "default") "Default (Provider Auto)"
                else "${levelLabel(eff)} (Budget)"
            }
            is ReasoningSupport.Effort -> {
                val eff = userEffort.trim().lowercase(Locale.US)
                if (eff == "default") {
                    "Default (Provider Auto)"
                } else if (eff in support.levels) {
                    levelLabel(eff)
                } else {
                    val clamped = clampEffort(eff, support.levels)
                    if (clamped != null) {
                        "${levelLabel(clamped)} (Clamped for this model)"
                    } else {
                        "Default (Provider Auto)"
                    }
                }
            }
        }
    }

    fun levelLabel(level: String): String = when (level.lowercase(Locale.US)) {
        "default" -> "Default (Provider Auto)"
        "none", "off" -> "Disabled (No Reasoning)"
        "on" -> "Enabled (Thinking On)"
        "minimal" -> "Minimal (Fastest)"
        "low" -> "Low (Fastest)"
        "medium" -> "Medium (Balanced)"
        "high" -> "High (Deep Reasoning)"
        "xhigh" -> "Extra High (Thorough)"
        "max" -> "Max (Maximum Depth)"
        else -> level.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    }

    private fun clampEffort(effort: String, supportedLevels: List<String>): String? {
        if (supportedLevels.isEmpty()) return null
        if (effort in supportedLevels) return effort

        val idx = EFFORT_ORDER.indexOf(effort)
        if (idx == -1) return supportedLevels.firstOrNull()

        for (i in (idx - 1) downTo 0) {
            val candidate = EFFORT_ORDER[i]
            if (candidate in supportedLevels) return candidate
        }

        for (i in (idx + 1) until EFFORT_ORDER.size) {
            val candidate = EFFORT_ORDER[i]
            if (candidate in supportedLevels) return candidate
        }

        return supportedLevels.firstOrNull()
    }

    private fun normalizeModelId(model: String): String =
        model.trim().lowercase(Locale.US).removePrefix("~")
}
