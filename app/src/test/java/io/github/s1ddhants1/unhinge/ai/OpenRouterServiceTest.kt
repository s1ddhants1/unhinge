package io.github.s1ddhants1.unhinge.ai

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
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
