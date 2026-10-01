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
    fun requestUsesStructuredOutputsWithALinesSchemaAndStrictParameterRouting() {
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
        assertTrue(request.getValue("provider").jsonObject.getValue("require_parameters").jsonPrimitive.boolean)
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
