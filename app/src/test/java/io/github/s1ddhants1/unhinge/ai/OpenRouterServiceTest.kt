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
    fun translationParsingHandlesFencedAndShortResponses() {
        assertEquals(
            listOf("uno", ""),
            parseTranslationContent("```json\n[\"uno\"]\n```", 2).getOrThrow(),
        )
    }

    @Test
    fun translationParsingHandlesTheStructuredOutputObjectShape() {
        assertEquals(
            listOf("uno", "dos"),
            parseTranslationContent("""{"lines": ["uno", "dos"]}""", 2).getOrThrow(),
        )
    }

    @Test
    fun requestUsesStructuredOutputsWithALinesSchemaAndStrictParameterRouting() {
        val request =
            buildTranslationRequest(
                text = "one\ntwo",
                targetLanguage = "Spanish",
                model = "model",
                mode = "Translated",
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
            buildTranslationRequest(
                text = "one",
                targetLanguage = "Spanish",
                model = "mercury-2",
                mode = "Translated",
                customSystemPrompt = "",
                baseUrl = "https://api.inceptionlabs.ai/v1/chat/completions",
            )

        assertTrue("provider" !in request)
        assertTrue(request.containsKey("response_format"))
    }

    @Test
    fun streamingRomanizationUsesTheRomanizationPrompt() {
        val request =
            buildTranslationRequest(
                text = "東京",
                targetLanguage = "English",
                model = "model",
                mode = "Romanized",
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
                .startsWith("Romanize"),
        )
    }
}
