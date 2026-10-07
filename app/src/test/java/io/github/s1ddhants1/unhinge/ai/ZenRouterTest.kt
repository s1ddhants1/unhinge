package io.github.s1ddhants1.unhinge.ai

import io.github.s1ddhants1.unhinge.Consts
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenRouterTest {
    @Test
    fun freeModelsRecognizedAsFree() {
        assertTrue(ZenRouter.isFreeModel("space-bunny-free"))
        assertTrue(ZenRouter.isFreeModel("muse-spark-1.3-contributor-free"))
        assertTrue(ZenRouter.isFreeModel("muse-spark-1.3-free"))
        assertTrue(ZenRouter.isFreeModel("big-pickle"))
        assertTrue(ZenRouter.isFreeModel("fledge-alpha-free"))
        assertTrue(ZenRouter.isFreeModel("ling-3.1-flash-free"))
        assertTrue(ZenRouter.isFreeModel("longcat-2.5-preview-free"))
        assertTrue(ZenRouter.isFreeModel("mimo-v2.6-flash-free"))
        assertTrue(ZenRouter.isFreeModel("mimo-v2.5-free"))
        assertTrue(ZenRouter.isFreeModel("nemotron-3-ultra-free"))
        assertTrue(ZenRouter.isFreeModel("nemotron-3.5-lightning-free"))
        assertTrue(ZenRouter.isFreeModel(""))
        assertTrue(ZenRouter.isFreeModel("custom-free-model"))
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
    fun defaultZenChatToolsHasAllOpenCodeTools() {
        assertFalse(ZenRouter.DefaultZenChatTools.isEmpty())
        assertEquals(12, ZenRouter.DefaultZenChatTools.size)
    }

    @Test
    fun zenFreeModelsDoNotRequireApiKey() {
        assertTrue(!AiWingmanHelper.isApiKeyRequired("Zen", "space-bunny-free"))
        assertTrue(!AiWingmanHelper.isApiKeyRequired("Zen", "big-pickle"))
        assertTrue(!AiWingmanHelper.isApiKeyRequired("Zen", "fledge-alpha-free"))
        assertTrue(!AiWingmanHelper.isApiKeyRequired("Zen", "ling-3.1-flash-free"))
        assertTrue(!AiWingmanHelper.isApiKeyRequired("Zen", "longcat-2.5-preview-free"))
        assertTrue(!AiWingmanHelper.isApiKeyRequired("Zen", "mimo-v2.6-flash-free"))
        assertTrue(!AiWingmanHelper.isApiKeyRequired("Zen", "mimo-v2.5-free"))
        assertTrue(!AiWingmanHelper.isApiKeyRequired("Zen", "nemotron-3-ultra-free"))
        assertTrue(!AiWingmanHelper.isApiKeyRequired("Zen", "nemotron-3.5-lightning-free"))
        assertFalse(AiWingmanHelper.isApiKeyRequired("Zen", ""))
        assertFalse(AiWingmanHelper.isApiKeyRequired("zen", "space-bunny-free"))
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
        assertEquals(LlmProtocol.OpenAiChatCompletions, proto)
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
    fun resolvesMuseSparkFreeAliases() {
        val (proto1, url1, model1) = ZenRouter.resolve("muse-spark-1.3-free")
        assertEquals(LlmProtocol.OpenAiResponses, proto1)
        assertEquals(ZenResponsesDefaultBaseUrl, url1)
        assertEquals("muse-spark-1.3-contributor-free", model1)

        val (proto2, url2, model2) = ZenRouter.resolve("muse-spark-1.2-free")
        assertEquals(LlmProtocol.OpenAiResponses, proto2)
        assertEquals(ZenResponsesDefaultBaseUrl, url2)
        assertEquals("muse-spark-1.2-contributor-free", model2)
    }

    @Test
    fun defaultZenResponsesToolsFormattedCorrectly() {
        assertFalse(ZenRouter.DefaultZenResponsesTools.isEmpty())
        assertEquals(12, ZenRouter.DefaultZenResponsesTools.size)
        val firstTool = ZenRouter.DefaultZenResponsesTools[0] as kotlinx.serialization.json.JsonObject
        assertEquals("function", firstTool["type"]?.jsonPrimitive?.content)
        assertTrue(firstTool.containsKey("name"))
        assertTrue(firstTool.containsKey("parameters"))
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
        assertEquals("true", body["stream"]?.jsonPrimitive?.content)
        assertFalse(body.containsKey("max_output_tokens"))
        assertTrue(body.containsKey("tools"))
        assertTrue(body.containsKey("prompt_cache_key"))
        assertTrue(body.containsKey("include"))

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

        val incompleteJson = """
            {
                "type": "response.incomplete",
                "response": {
                    "status": "incomplete",
                    "incomplete_details": {
                        "reason": "max_output_tokens"
                    }
                }
            }
        """.trimIndent()
        val incompleteReason = extractIncompleteReason(kotlinx.serialization.json.Json.parseToJsonElement(incompleteJson))
        assertEquals("max_output_tokens", incompleteReason)
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
        assertTrue(requestWithNoKey.header("b3")?.contains("-1-") == true)

        val customKey = "sk-custom-zen-key-123"
        val requestWithKey = okhttp3.Request.Builder()
            .url("https://opencode.ai/zen/v1/chat/completions")
            .also { ZenRouter.injectZenHeaders(it, customKey) }
            .build()
        assertEquals("Bearer $customKey", requestWithKey.header("Authorization"))
    }

    @Test
    fun injectZenHeadersEnforcesPublicBearerForFreeModelsEvenWhenUserConfiguresKey() {
        val leftoverKey = "dummy-leftover-api-key-from-other-provider"

        for (freeModel in ZenRouter.FreeModels) {
            val request = okhttp3.Request.Builder()
                .url("https://opencode.ai/zen/v1/chat/completions")
                .also { ZenRouter.injectZenHeaders(it, apiKey = leftoverKey, model = freeModel) }
                .build()
            assertEquals("Free model $freeModel must strictly use Bearer public", "Bearer public", request.header("Authorization"))
        }
    }

    @Test
    fun injectZenHeadersUsesUserKeyForPaidZenModels() {
        val paidKey = "sk-opencode-paid-key"

        val requestPaid = okhttp3.Request.Builder()
            .url("https://opencode.ai/zen/v1/responses")
            .also { ZenRouter.injectZenHeaders(it, apiKey = paidKey, model = "gpt-5.6-sol") }
            .build()
        assertEquals("Bearer $paidKey", requestPaid.header("Authorization"))

        val requestClaude = okhttp3.Request.Builder()
            .url("https://opencode.ai/zen/v1/messages")
            .also { ZenRouter.injectZenHeaders(it, apiKey = paidKey, model = "claude-sonnet-5") }
            .build()
        assertEquals("Bearer $paidKey", requestClaude.header("Authorization"))

        val requestPaidNoKey = okhttp3.Request.Builder()
            .url("https://opencode.ai/zen/v1/responses")
            .also { ZenRouter.injectZenHeaders(it, apiKey = "", model = "gpt-5.6-sol") }
            .build()
        assertEquals("Bearer public", requestPaidNoKey.header("Authorization"))
    }

    @Test
    fun buildGenerationRequestInjectsToolsForZenFreeModels() {
        for (model in ZenRouter.FreeModels) {
            val body = buildGenerationRequest(
                text = "Prompt 1",
                model = model,
                customSystemPrompt = "",
                baseUrl = Consts.ZEN_DEFAULT_BASE_URL,
                reasoningEffort = "low",
            )
            assertTrue("Zen free model $model must include tools", body.containsKey("tools"))
            val expectedEffort = ModelReasoningCatalog.sanitizeReasoningEffort("low", model, "Zen")
            if (expectedEffort != null && expectedEffort != "on" && expectedEffort != "off") {
                assertEquals(expectedEffort, body["reasoning_effort"]?.jsonPrimitive?.content)
            } else {
                assertFalse("Model $model should not have reasoning_effort parameter", body.containsKey("reasoning_effort"))
            }
        }
    }

    @Test
    fun modelReasoningCatalogResolvesCorrectSupportTypes() {
        assertEquals(
            ModelReasoningCatalog.ReasoningSupport.Effort(listOf("low", "medium", "high", "xhigh", "max")),
            ModelReasoningCatalog.getReasoningSupport("space-bunny-free")
        )
        assertEquals(
            ModelReasoningCatalog.ReasoningSupport.Fixed,
            ModelReasoningCatalog.getReasoningSupport("big-pickle")
        )
        assertEquals(
            ModelReasoningCatalog.ReasoningSupport.Effort(listOf("minimal", "low", "medium", "high", "xhigh")),
            ModelReasoningCatalog.getReasoningSupport("muse-spark-1.3-contributor-free")
        )
        assertEquals(
            ModelReasoningCatalog.ReasoningSupport.Effort(listOf("minimal", "low", "medium", "high", "xhigh")),
            ModelReasoningCatalog.getReasoningSupport("muse-spark-1.3-free")
        )
        assertEquals(
            ModelReasoningCatalog.ReasoningSupport.Effort(listOf("low", "high", "max")),
            ModelReasoningCatalog.getReasoningSupport("fledge-alpha-free")
        )
        assertEquals(
            ModelReasoningCatalog.ReasoningSupport.Toggle,
            ModelReasoningCatalog.getReasoningSupport("ling-3.1-flash-free")
        )
        assertEquals(
            ModelReasoningCatalog.ReasoningSupport.Effort(listOf("low", "medium", "high")),
            ModelReasoningCatalog.getReasoningSupport("gemini-3.8-flash")
        )
        assertEquals(
            ModelReasoningCatalog.ReasoningSupport.Effort(listOf("none", "low", "medium", "high", "xhigh", "max")),
            ModelReasoningCatalog.getReasoningSupport("gpt-5.6-sol")
        )
        assertTrue(ModelReasoningCatalog.getReasoningSupport("claude-haiku-4-5") is ModelReasoningCatalog.ReasoningSupport.BudgetTokens)
        assertEquals(
            ModelReasoningCatalog.ReasoningSupport.Unsupported,
            ModelReasoningCatalog.getReasoningSupport("sonar")
        )
    }

    @Test
    fun modelReasoningCatalogClampingAndSanitization() {

        assertEquals("high", ModelReasoningCatalog.sanitizeReasoningEffort("max", "gemini-3.8-flash"))
        assertEquals("high", ModelReasoningCatalog.sanitizeReasoningEffort("xhigh", "gemini-3.8-flash"))

        assertEquals("low", ModelReasoningCatalog.sanitizeReasoningEffort("medium", "fledge-alpha-free"))

        assertEquals("high", ModelReasoningCatalog.sanitizeReasoningEffort("high", "fledge-alpha-free"))
        assertEquals("max", ModelReasoningCatalog.sanitizeReasoningEffort("max", "fledge-alpha-free"))
        assertEquals("low", ModelReasoningCatalog.sanitizeReasoningEffort("low", "fledge-alpha-free"))

        assertEquals("on", ModelReasoningCatalog.sanitizeReasoningEffort("on", "ling-3.1-flash-free"))
        assertEquals("on", ModelReasoningCatalog.sanitizeReasoningEffort("low", "ling-3.1-flash-free"))
        assertEquals("off", ModelReasoningCatalog.sanitizeReasoningEffort("off", "ling-3.1-flash-free"))
        assertEquals("off", ModelReasoningCatalog.sanitizeReasoningEffort("none", "ling-3.1-flash-free"))

        assertEquals(null, ModelReasoningCatalog.sanitizeReasoningEffort("default", "space-bunny-free"))
        assertEquals(null, ModelReasoningCatalog.sanitizeReasoningEffort("low", "big-pickle"))
        assertEquals(null, ModelReasoningCatalog.sanitizeReasoningEffort("low", "sonar"))
    }

    @Test
    fun modelReasoningCatalogUiOptions() {
        val fledgeOptions = ModelReasoningCatalog.getAvailableOptions("fledge-alpha-free").map { it.first }
        assertTrue("fledge options must have default", fledgeOptions.contains("default"))
        assertTrue("fledge options must have low", fledgeOptions.contains("low"))
        assertTrue("fledge options must have high", fledgeOptions.contains("high"))
        assertTrue("fledge options must have max", fledgeOptions.contains("max"))
        assertFalse("fledge options must NOT have medium", fledgeOptions.contains("medium"))

        val lingOptions = ModelReasoningCatalog.getAvailableOptions("ling-3.1-flash-free").map { it.first }
        assertEquals(listOf("default", "on", "off"), lingOptions)

        val pickleOptions = ModelReasoningCatalog.getAvailableOptions("big-pickle")
        assertTrue(pickleOptions.isEmpty())

        val sonarOptions = ModelReasoningCatalog.getAvailableOptions("sonar")
        assertTrue(sonarOptions.isEmpty())
    }

    @Test
    fun buildGenerationRequestOmitsReasoningEffortWhenDefault() {
        val body = buildGenerationRequest(
            text = "Prompt 1",
            model = "gpt-5.6-sol",
            customSystemPrompt = "",
            baseUrl = Consts.ZEN_DEFAULT_BASE_URL,
            reasoningEffort = "default",
        )
        assertFalse(body.containsKey("reasoning_effort"))
    }

    @Test
    fun parseGeneratedContentHandlesMarkdownAndQuotes() {
        val markdownResponse = """
            **Opener**

            > "Okay, important question: tonkotsu or shoyu? Because your answer tells me everything."

            ---

            **Why it works:** It's a real question.
        """.trimIndent()

        val parsed = parseGeneratedContent(markdownResponse, 1).getOrThrow()
        assertEquals(1, parsed.size)
        assertEquals("Okay, important question: tonkotsu or shoyu? Because your answer tells me everything.", parsed[0])
    }

    @Test
    fun parseGeneratedContentHandlesSingleJsonPrimitive() {
        val jsonPrimitiveResponse = "\"your yuzu shio slaps in a way I'm still thinking about\""
        val parsed = parseGeneratedContent(jsonPrimitiveResponse, 1).getOrThrow()
        assertEquals(1, parsed.size)
        assertEquals("your yuzu shio slaps in a way I'm still thinking about", parsed[0])
    }

    @Test
    fun parseGeneratedContentHandlesSchemaObject() {
        val schemaJson = """{"lines":["Line 1","Line 2"]}"""
        val parsed = parseGeneratedContent(schemaJson, 2).getOrThrow()
        assertEquals(2, parsed.size)
        assertEquals("Line 1", parsed[0])
        assertEquals("Line 2", parsed[1])
    }
}
