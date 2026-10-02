package io.github.s1ddhants1.unhinge.ai

import io.github.s1ddhants1.unhinge.Consts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenRouterTest {
    @Test
    fun freeModelsRecognizedAsFree() {
        assertTrue(ZenRouter.isFreeModel("muse-spark-1.3-contributor-free"))
        assertTrue(ZenRouter.isFreeModel("muse-spark-1.2-contributor-free"))
        assertTrue(ZenRouter.isFreeModel("space-bunny-free"))
        assertTrue(ZenRouter.isFreeModel("big-pickle"))
        assertTrue(ZenRouter.isFreeModel("deepseek-v4-flash-free"))
        assertTrue(ZenRouter.isFreeModel("glm-5-free"))
        assertTrue(ZenRouter.isFreeModel("qwen3.6-plus-free"))
        assertTrue(ZenRouter.isFreeModel(""))
        assertTrue(ZenRouter.isFreeModel("custom-model-free"))
    }

    @Test
    fun paidModelsRecognizedAsPaid() {
        assertFalse(ZenRouter.isFreeModel("muse-spark-1.3"))
        assertFalse(ZenRouter.isFreeModel("gpt-5.6-sol"))
        assertFalse(ZenRouter.isFreeModel("claude-sonnet-5"))
        assertFalse(ZenRouter.isFreeModel("gemini-3.8-flash"))
        assertFalse(ZenRouter.isFreeModel("qwen3.8-max"))
        assertFalse(ZenRouter.isFreeModel("deepseek-v4-pro"))
        assertFalse(ZenRouter.isFreeModel("deepseek-v4-flash"))
        assertFalse(ZenRouter.isFreeModel("glm-5.3-flash"))
    }

    @Test
    fun zenFreeModelsDoNotRequireApiKey() {
        assertFalse(AiWingmanHelper.isApiKeyRequired("Zen", "muse-spark-1.3-contributor-free"))
        assertFalse(AiWingmanHelper.isApiKeyRequired("Zen", "space-bunny-free"))
        assertFalse(AiWingmanHelper.isApiKeyRequired("Zen", "big-pickle"))
        assertFalse(AiWingmanHelper.isApiKeyRequired("Zen", ""))
        assertFalse(AiWingmanHelper.isApiKeyRequired("zen", "deepseek-v4-flash-free"))
    }

    @Test
    fun zenPaidModelsRequireApiKey() {
        assertTrue(AiWingmanHelper.isApiKeyRequired("Zen", "muse-spark-1.3"))
        assertTrue(AiWingmanHelper.isApiKeyRequired("Zen", "gpt-5.6-sol"))
        assertTrue(AiWingmanHelper.isApiKeyRequired("Zen", "claude-sonnet-5"))
    }

    @Test
    fun otherProvidersAlwaysRequireApiKey() {
        assertTrue(AiWingmanHelper.isApiKeyRequired("OpenRouter", "inception/mercury-2.5-preview"))
        assertTrue(AiWingmanHelper.isApiKeyRequired("OpenRouter", "muse-spark-1.3-contributor-free"))
        assertTrue(AiWingmanHelper.isApiKeyRequired("OpenAI", "gpt-5.6-sol"))
        assertTrue(AiWingmanHelper.isApiKeyRequired("Claude", "claude-sonnet-5"))
    }

    @Test
    fun resolvesDefaultWhenBlank() {
        val (proto, url, model) = ZenRouter.resolve("")
        assertEquals(LlmProtocol.OpenAiResponses, proto)
        assertEquals(Consts.ZEN_DEFAULT_BASE_URL, url)
        assertEquals(Consts.ZEN_DEFAULT_MODEL, model)
    }

    @Test
    fun resolvesFreeResponsesModel() {
        val (proto, url, model) = ZenRouter.resolve("muse-spark-1.3-contributor-free")
        assertEquals(LlmProtocol.OpenAiResponses, proto)
        assertEquals(ZenResponsesDefaultBaseUrl, url)
        assertEquals("muse-spark-1.3-contributor-free", model)
    }

    @Test
    fun resolvesFreeChatModels() {
        val (proto1, url1, model1) = ZenRouter.resolve("space-bunny-free")
        assertEquals(LlmProtocol.OpenAiChatCompletions, proto1)
        assertEquals(ZenChatBaseUrl, url1)
        assertEquals("space-bunny-free", model1)

        val (proto2, url2, model2) = ZenRouter.resolve("big-pickle")
        assertEquals(LlmProtocol.OpenAiChatCompletions, proto2)
        assertEquals(ZenChatBaseUrl, url2)
        assertEquals("big-pickle", model2)
    }

    @Test
    fun resolvesMessagesModels() {
        val (proto1, url1, model1) = ZenRouter.resolve("qwen3.8-flash")
        assertEquals(LlmProtocol.AnthropicMessages, proto1)
        assertEquals(ZenMessagesBaseUrl, url1)
        assertEquals("qwen3.8-flash", model1)

        val (proto2, url2, model2) = ZenRouter.resolve("claude-sonnet-5")
        assertEquals(LlmProtocol.AnthropicMessages, proto2)
        assertEquals(ZenMessagesBaseUrl, url2)
        assertEquals("claude-sonnet-5", model2)
    }

    @Test
    fun resolvesGeminiModels() {
        val (proto, url, model) = ZenRouter.resolve("gemini-3.8-flash")
        assertEquals(LlmProtocol.GoogleGemini, proto)
        assertEquals("$ZenGeminiBase/gemini-3.8-flash", url)
        assertEquals("gemini-3.8-flash", model)
    }

    @Test
    fun aiWingmanHelperResolvesZenEndpoints() {
        val (proto, url, model) = AiWingmanHelper.resolveEffectiveEndpoint(
            provider = "Zen",
            apiKey = "",
            baseUrl = "",
            model = "space-bunny-free"
        )
        assertEquals(LlmProtocol.OpenAiChatCompletions, proto)
        assertEquals(ZenChatBaseUrl, url)
        assertEquals("space-bunny-free", model)
    }

    @Test
    fun aiWingmanHelperAllowsCustomBaseUrlOverrideForZen() {
        val (proto, url, model) = AiWingmanHelper.resolveEffectiveEndpoint(
            provider = "Zen",
            apiKey = "",
            baseUrl = "https://custom-proxy.internal/v1/responses",
            model = "muse-spark-1.3"
        )
        assertEquals(LlmProtocol.OpenAiResponses, proto)
        assertEquals("https://custom-proxy.internal/v1/responses", url)
        assertEquals("muse-spark-1.3", model)
    }

    @Test
    fun storedZenBaseUrlDoesNotOverridePerModelRouting() {
        val (proto, url, model) = AiWingmanHelper.resolveEffectiveEndpoint(
            provider = "Zen",
            apiKey = "",
            baseUrl = Consts.ZEN_DEFAULT_BASE_URL,
            model = "claude-sonnet-5"
        )
        assertEquals(LlmProtocol.AnthropicMessages, proto)
        assertEquals(ZenMessagesBaseUrl, url)
        assertEquals("claude-sonnet-5", model)
    }

    @Test
    fun staleOpenRouterBaseUrlCannotHijackZenRouting() {
        val (proto, url, model) = AiWingmanHelper.resolveEffectiveEndpoint(
            provider = "Zen",
            apiKey = "",
            baseUrl = Consts.OPENROUTER_DEFAULT_BASE_URL,
            model = "gemini-3.8-flash"
        )
        assertEquals(LlmProtocol.GoogleGemini, proto)
        assertEquals("$ZenGeminiBase/gemini-3.8-flash", url)
        assertEquals("gemini-3.8-flash", model)
    }

    @Test
    fun responsesPayloadBuildingAndExtraction() {
        val body = buildResponsesRequest(
            instructions = "Be concise",
            input = "Say hello",
            model = "muse-spark-1.3-contributor-free",
            stream = false,
        )
        assertEquals("muse-spark-1.3-contributor-free", body["model"]?.jsonPrimitive?.content)
        assertEquals("Be concise", body["instructions"]?.jsonPrimitive?.content)
        assertEquals("Say hello", body["input"]?.jsonPrimitive?.content)

        val sampleJson = """
            {
                "output": [
                    {
                        "content": [
                            {"type": "text", "text": "Hello there!"}
                        ]
                    }
                ]
            }
        """.trimIndent()
        val text = extractResponsesText(sampleJson)
        assertEquals("Hello there!", text)
    }

    @Test
    fun messagesPayloadBuildingAndExtraction() {
        val body = buildMessagesRequest(
            system = "System prompt",
            user = "User query",
            model = "claude-sonnet-5",
            stream = false,
        )
        assertEquals("claude-sonnet-5", body["model"]?.jsonPrimitive?.content)
        assertEquals("System prompt", body["system"]?.jsonPrimitive?.content)

        val sampleJson = """
            {
                "content": [
                    {"type": "text", "text": "Anthropic response"}
                ]
            }
        """.trimIndent()
        val text = extractMessagesText(sampleJson)
        assertEquals("Anthropic response", text)
    }

    @Test
    fun geminiPayloadBuildingAndExtraction() {
        val body = buildGeminiRequest(
            system = "System rule",
            user = "User question"
        )
        assertTrue(body.containsKey("system_instruction"))
        assertTrue(body.containsKey("contents"))

        val sampleJson = """
            {
                "candidates": [
                    {
                        "content": {
                            "parts": [
                                {"text": "Gemini response"}
                            ]
                        }
                    }
                ]
            }
        """.trimIndent()
        val text = extractGeminiText(sampleJson)
        assertEquals("Gemini response", text)
    }

    @Test
    fun sessionIdFormatMatchesOpenCodeSpecification() {
        val sessionId = ZenRouter.generateSessionId()
        assertTrue("Session ID should start with ses_", sessionId.startsWith("ses_"))
        assertEquals("Session ID total length should be 30 (ses_ + 26)", 30, sessionId.length)
        assertTrue(
            "Session ID should be alphanumeric after prefix",
            sessionId.matches(Regex("^ses_[0-9A-Za-z]{26}$"))
        )
    }

    @Test
    fun traceparentFormatMatchesW3cSpec() {
        val traceparent = ZenRouter.generateTraceparent()
        assertTrue(
            "Traceparent should match W3C format 00-{32hex}-{16hex}-01",
            traceparent.matches(Regex("^00-[0-9a-f]{32}-[0-9a-f]{16}-01$"))
        )
    }

    @Test
    fun isZenUrlIdentifiesZenDomains() {
        assertTrue(ZenRouter.isZenUrl("https://opencode.ai/zen/v1/chat/completions"))
        assertTrue(ZenRouter.isZenUrl("https://opencode.ai/zen/v1/responses"))
        assertTrue(ZenRouter.isZenUrl("https://opencode.ai/zen/v1/messages"))
        assertTrue(ZenRouter.isZenUrl("https://opencode.ai/zen/v1/models/gemini-3.8-flash:generateContent"))
        assertFalse(ZenRouter.isZenUrl("https://openrouter.ai/api/v1/chat/completions"))
        assertFalse(ZenRouter.isZenUrl("https://api.openai.com/v1/chat/completions"))
        assertFalse(ZenRouter.isZenUrl("https://api.anthropic.com/v1/messages"))
    }

    @Test
    fun injectZenHeadersSetsAllRequiredOpenCodeHeaders() {
        val requestWithNoKey = okhttp3.Request.Builder()
            .url("https://opencode.ai/zen/v1/chat/completions")
            .also { ZenRouter.injectZenHeaders(it, "") }
            .build()

        assertEquals("Bearer public", requestWithNoKey.header("Authorization"))
        assertEquals(ZenRouter.OPENCODE_USER_AGENT, requestWithNoKey.header("User-Agent"))
        assertEquals(ZenRouter.OPENCODE_CLIENT, requestWithNoKey.header("x-opencode-client"))
        assertEquals(ZenRouter.OPENCODE_PROJECT_ID, requestWithNoKey.header("x-opencode-project"))
        val sessionHeader = requestWithNoKey.header("x-opencode-session")
        assertTrue(sessionHeader != null && sessionHeader.startsWith("ses_"))
        assertEquals(sessionHeader, requestWithNoKey.header("x-session-affinity"))
        assertEquals(sessionHeader, requestWithNoKey.header("x-session-id"))
        assertTrue(requestWithNoKey.header("traceparent")?.startsWith("00-") == true)

        val customKey = "sk-custom-zen-key-123"
        val requestWithKey = okhttp3.Request.Builder()
            .url("https://opencode.ai/zen/v1/chat/completions")
            .also { ZenRouter.injectZenHeaders(it, customKey) }
            .build()
        assertEquals("Bearer $customKey", requestWithKey.header("Authorization"))
    }
}

