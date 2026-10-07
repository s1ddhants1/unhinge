package io.github.s1ddhants1.unhinge.ai

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterServiceTest {
    @Test
    fun generationParsingHandlesFencedAndShortResponses() {
        assertEquals(
            listOf("uno", ""),
            parseGeneratedContent("```json\n[\"uno\"]\n```", 2).getOrThrow(),
        )
    }

    @Test
    fun generationParsingHandlesTheStructuredOutputObjectShape() {
        assertEquals(
            listOf("uno", "dos"),
            parseGeneratedContent("""{"lines": ["uno", "dos"]}""", 2).getOrThrow(),
        )
    }

    @Test
    fun requestUsesLenientStructuredOutputsWithoutStrictParameterRouting() {
        val request =
            buildGenerationRequest(
                text = "one\ntwo",
                model = "model",
                customSystemPrompt = "",
            )

        val responseFormat = request.getValue("response_format").jsonObject
        assertEquals("json_schema", responseFormat.getValue("type").jsonPrimitive.content)
        val schema = responseFormat.getValue("json_schema").jsonObject.getValue("schema").jsonObject
        assertEquals("object", schema.getValue("type").jsonPrimitive.content)
        val lines = schema.getValue("properties").jsonObject.getValue("lines").jsonObject
        assertEquals("array", lines.getValue("type").jsonPrimitive.content)
        assertEquals("string", lines.getValue("items").jsonObject.getValue("type").jsonPrimitive.content)
        assertTrue("provider" !in request)
    }

    @Test
    fun lenientRequestOmitsStructuredOutputsWhenDisabled() {
        val request =
            buildGenerationRequest(
                text = "one",
                model = "model",
                customSystemPrompt = "",
                structured = false,
            )

        assertTrue("response_format" !in request)
        assertTrue("provider" !in request)
    }

    @Test
    fun sanitizeModelIdStripsTildeArtifacts() {
        assertEquals("deepseek/deepseek-v4-flash-latest", sanitizeModelId("~deepseek/deepseek-v4-flash-latest"))
        assertEquals("model", sanitizeModelId("  model  "))
        assertEquals("", sanitizeModelId("   "))
    }

    @Test
    fun transientCodesCoverUpstreamOverload() {
        assertTrue(isTransientHttpCode(500))
        assertTrue(isTransientHttpCode(502))
        assertTrue(isTransientHttpCode(503))
        assertTrue(isTransientHttpCode(529))
        assertTrue(!isTransientHttpCode(400))
        assertTrue(!isTransientHttpCode(401))
        assertTrue(!isTransientHttpCode(429))
    }

    @Test
    fun structuredOutputRejectionDetectsEmptyProviderPool() {
        assertTrue(isStructuredOutputError("""{"error":{"message":"No available model provider"}}""", 503))
        assertTrue(isStructuredOutputError("""{"error":{"message":"No endpoints found with response_format support"}}""", 400))
        assertTrue(!isStructuredOutputError("""{"error":{"message":"Invalid API key"}}""", 401))
        assertTrue(!isStructuredOutputError("HTTP 503: Service Unavailable", 503))
    }

    @Test
    fun friendlyErrorHintsAtRetryOrModelSwitchFor503() {
        val message = friendlyGenerationError("""{"error":{"message":"No available model provider"}}""", 503, "Service Unavailable")
        assertTrue(message.contains("503"))
        assertTrue(message.contains("Retry or switch model"))
        assertTrue(friendlyGenerationError(null, 401, "Unauthorized").contains("API key"))
    }

    @Test
    fun friendlyErrorProviderAwareness() {

        val zenModelError = friendlyGenerationError(
            """{"type":"error","error":{"type":"ModelError","message":"Model glm-5-free is not supported"}}""",
            401,
            "Unauthorized",
            provider = "Zen",
            model = "glm-5-free",
        )
        assertTrue(zenModelError.contains("Zen model error"))
        assertTrue(zenModelError.contains("space-bunny-free"))
        assertFalse(zenModelError.contains("openrouter"))

        val zenFree401 = friendlyGenerationError(
            null,
            401,
            "Unauthorized",
            baseUrl = "https://opencode.ai/zen/v1/chat/completions",
            model = "space-bunny-free",
        )
        assertTrue(zenFree401.contains("Zen free-tier authorization failed"))
        assertTrue(zenFree401.contains("No API key is needed"))
        assertFalse(zenFree401.contains("sk-or-"))

        val zenPaid401 = friendlyGenerationError(
            null,
            401,
            "Unauthorized",
            provider = "Zen",
            model = "claude-sonnet-5",
        )
        assertTrue(zenPaid401.contains("Invalid Zen API key"))
        assertTrue(zenPaid401.contains("opencode.ai/auth"))
        assertFalse(zenPaid401.contains("openrouter.ai"))

        val zen403 = friendlyGenerationError(
            """{"type":"error","error":{"type":"FreeTierError","message":"Error from provider (Console): OpenCode's free tier can only be used from within OpenCode"}}""",
            403,
            "Forbidden",
            provider = "Zen",
            model = "big-pickle",
        )
        assertTrue(zen403.contains("Zen free-tier restriction"))
        assertTrue(zen403.contains("space-bunny-free"))
        assertFalse(zen403.contains("guardrail"))

        val claude401 = friendlyGenerationError(null, 401, "Unauthorized", provider = "Claude")
        assertTrue(claude401.contains("Anthropic"))
        assertTrue(claude401.contains("console.anthropic.com"))

        val openAi401 = friendlyGenerationError(null, 401, "Unauthorized", provider = "OpenAI")
        assertTrue(openAi401.contains("OpenAI"))
        assertTrue(openAi401.contains("platform.openai.com"))

        val gemini401 = friendlyGenerationError(null, 401, "Unauthorized", provider = "Gemini")
        assertTrue(gemini401.contains("Gemini"))
        assertTrue(gemini401.contains("aistudio.google.com"))

        val openRouter401 = friendlyGenerationError(null, 401, "Unauthorized", provider = "OpenRouter")
        assertTrue(openRouter401.contains("openrouter.ai/keys"))
        assertTrue(openRouter401.contains("sk-or-"))
    }

    @Test
    fun openrouterOnlyProviderRoutingIsOmittedForDirectProviders() {
        val request =
            buildGenerationRequest(
                text = "one",
                model = "mercury-2",
                customSystemPrompt = "",
                baseUrl = "https://api.inceptionlabs.ai/v1/chat/completions",
            )

        assertTrue("provider" !in request)
        assertTrue(request.containsKey("response_format"))
    }

    @Test
    fun streamingRequestUsesTheWingmanPrompt() {
        val request =
            buildGenerationRequest(
                text = "I love hiking: I climb every weekend",
                model = "model",
                customSystemPrompt = "",
                stream = true,
            )

        assertTrue(request.getValue("stream").jsonPrimitive.boolean)
        assertTrue(
            request
                .getValue("messages")
                .jsonArray[1]
                .jsonObject
                .getValue("content")
                .jsonPrimitive
                .content
                .startsWith("Generate opening replies"),
        )
    }

    @Test
    fun customSystemPromptMergesAsMiddleTierKeepingJsonContract() {
        val request =
            buildGenerationRequest(
                text = "one\ntwo",
                model = "model",
                customSystemPrompt = "Custom instructions for {lineCount} prompts",
            )

        val system = request.getValue("messages").jsonArray[0].jsonObject.getValue("content").jsonPrimitive.content
        assertTrue(system.contains("Custom instructions for 2 prompts"))
        assertTrue(system.contains("EXACTLY 2 opening replies"))
    }
}
