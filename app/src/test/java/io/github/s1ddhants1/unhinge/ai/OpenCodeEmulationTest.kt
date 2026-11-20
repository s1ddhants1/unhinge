package io.github.s1ddhants1.unhinge.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeEmulationTest {

    @Test
    fun testIsOpenCodeRecognition() {
        assertTrue(OpenCodeEmulation.isOpenCode("OpenCodeZen"))
        assertTrue(OpenCodeEmulation.isOpenCode("OpenCodeGo"))
        assertTrue(OpenCodeEmulation.isOpenCode("Zen"))
        assertTrue(OpenCodeEmulation.isOpenCode("custom", "https://opencode.ai/zen/v1/chat/completions"))
        assertFalse(OpenCodeEmulation.isOpenCode("OpenRouter", "https://openrouter.ai/api/v1/chat/completions"))
    }

    @Test
    fun testGenerateSessionId() {
        val sessionId = OpenCodeEmulation.generateSessionId()
        assertTrue(sessionId.startsWith("ses_"))
        // 4 chars ("ses_") + 12 hex chars + 14 base62 chars = 30 chars
        assertEquals(30, sessionId.length)
    }

    @Test
    fun testInjectHeaders() {
        val builder = Request.Builder().url("https://opencode.ai/zen/v1/chat/completions")
        OpenCodeEmulation.injectHeaders(builder, apiKey = "")
        val req = builder.build()

        assertEquals("Bearer public", req.header("Authorization"))
        assertEquals("opencode/latest/2.3.2/cli", req.header("User-Agent"))
        assertEquals("cli", req.header("x-opencode-client"))
        assertEquals("fbfbb8ecca77fbf4f585927700f1baa979cb6a94", req.header("x-opencode-project"))
        assertNotNull(req.header("x-opencode-session"))
        assertTrue(req.header("x-opencode-session")!!.startsWith("ses_"))
        assertNotNull(req.header("traceparent"))
        assertNotNull(req.header("b3"))
    }

    @Test
    fun testFreeModelRecognition() {
        assertTrue(AiProviderPresets.isFreeModel("big-pickle"))
        assertTrue(AiProviderPresets.isFreeModel("deepseek-v4.1-flash-free"))
        assertTrue(AiProviderPresets.isFreeModel("mimo-v2.6-flash-free"))
        assertTrue(AiProviderPresets.isFreeModel("google/gemma-4-26b-a4b-it:free"))
        assertFalse(AiProviderPresets.isFreeModel("gpt-5.5"))
        assertFalse(AiProviderPresets.isFreeModel("claude-opus-5"))
    }

    @Test
    fun testParseOpenCodeModels() {
        val jsonString = """
            {
              "data": [
                {
                  "id": "big-pickle",
                  "name": "Big Pickle",
                  "modalities": {
                    "input": ["text"],
                    "output": ["text"]
                  },
                  "limit": {
                    "context": 200000
                  },
                  "cost": {
                    "text": {
                      "input": { "price": 0 },
                      "output": { "price": 0 }
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val root = Json.parseToJsonElement(jsonString).jsonObject
        val models = AiModelCatalog.parseOpenAiModels(root, textOnly = false)

        assertEquals(1, models.size)
        val m = models.first()
        assertEquals("big-pickle", m.id)
        assertEquals(200000, m.contextLength)
        assertEquals(0.0, m.inputUsdPerMTok!!, 0.001)
        assertEquals(0.0, m.outputUsdPerMTok!!, 0.001)
        assertTrue(m.free)
    }
}
