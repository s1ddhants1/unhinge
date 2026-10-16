package io.github.s1ddhants1.unhinge.ai

import io.github.s1ddhants1.unhinge.Consts

/**
 * Endpoint, base URL, and protocol resolver for LLM providers.
 * Decoupled from coordinator logic to ensure centralized, easily testable routing rules.
 */
object LlmProtocolRouter {

    fun isApiKeyRequired(provider: String, model: String): Boolean {
        if (provider.equals("Zen", ignoreCase = true)) {
            return !ZenRouter.isFreeModel(model)
        }
        return true
    }

    fun resolveEffectiveEndpoint(
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
    ): Triple<LlmProtocol, String, String> {
        if (provider.equals("Zen", ignoreCase = true)) {
            val trimmed = baseUrl.trim()
            val isCustomProxy =
                trimmed.isNotBlank() &&
                    !ZenRouter.isZenUrl(trimmed) &&
                    !trimmed.contains("openrouter.ai", ignoreCase = true)
            if (isCustomProxy) {
                val protocol = when {
                    trimmed.endsWith("/responses") -> LlmProtocol.OpenAiResponses
                    trimmed.endsWith("/messages") -> LlmProtocol.AnthropicMessages
                    trimmed.contains("/models/") -> LlmProtocol.GoogleGemini
                    else -> LlmProtocol.OpenAiChatCompletions
                }
                val resolvedModel = sanitizeModelId(model).ifBlank { Consts.ZEN_DEFAULT_MODEL }
                return Triple(protocol, trimmed, resolvedModel)
            }
            return ZenRouter.resolve(model)
        }
        val protocol = LlmProtocol.infer(provider, apiKey)
        val effectiveProvider = if (protocol == LlmProtocol.GoogleOpenAi) "Gemini" else provider

        val effectiveBaseUrl = when {
            effectiveProvider.equals("Gemini", ignoreCase = true) && (baseUrl.isBlank() || baseUrl.contains("openrouter.ai")) ->
                LlmProtocol.GoogleOpenAi.defaultEndpoint()
            effectiveProvider.equals("OpenAI", ignoreCase = true) && (baseUrl.isBlank() || baseUrl.contains("openrouter.ai")) ->
                "https://api.openai.com/v1/chat/completions"
            effectiveProvider.equals("Claude", ignoreCase = true) && (baseUrl.isBlank() || baseUrl.contains("openrouter.ai")) ->
                "https://api.anthropic.com/v1/messages"
            effectiveProvider.equals("Perplexity", ignoreCase = true) && (baseUrl.isBlank() || baseUrl.contains("openrouter.ai")) ->
                "https://api.perplexity.ai/chat/completions"
            effectiveProvider.equals("XAi", ignoreCase = true) && (baseUrl.isBlank() || baseUrl.contains("openrouter.ai")) ->
                "https://api.x.ai/v1/chat/completions"
            effectiveProvider.equals("Mistral", ignoreCase = true) && (baseUrl.isBlank() || baseUrl.contains("openrouter.ai")) ->
                "https://api.mistral.ai/v1/chat/completions"
            effectiveProvider.equals("Inception", ignoreCase = true) && (baseUrl.isBlank() || baseUrl.contains("openrouter.ai")) ->
                "https://api.inceptionlabs.ai/v1/chat/completions"
            else -> baseUrl.ifBlank { OpenRouterDefaultBaseUrl }
        }

        val sanitizedModel = sanitizeModelId(model)
        val effectiveModel = if (effectiveProvider.equals("Gemini", ignoreCase = true) && (sanitizedModel.isBlank() || sanitizedModel.contains("gemini-2.5-flash-lite"))) {
            "gemini-flash-lite-latest"
        } else if (sanitizedModel.isBlank() || sanitizedModel == Consts.LEGACY_OPENROUTER_DEFAULT_MODEL) {
            Consts.OPENROUTER_DEFAULT_MODEL
        } else {
            sanitizedModel
        }

        return Triple(protocol, effectiveBaseUrl, effectiveModel)
    }
}
