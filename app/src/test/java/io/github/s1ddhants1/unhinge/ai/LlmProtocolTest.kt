package io.github.s1ddhants1.unhinge.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmProtocolTest {
    @Test
    fun geminiBrandResolvesToGoogleProtocol() {
        assertEquals(LlmProtocol.GoogleOpenAi, LlmProtocol.infer("Gemini", "sk-anything"))
    }

    @Test
    fun geminiKeyRoutesToGoogleEvenWhenBrandLags() {
        assertEquals(LlmProtocol.GoogleOpenAi, LlmProtocol.infer("OpenRouter", "  AIzaSyABC123"))
    }

    @Test
    fun otherBrandsDefaultToOpenAiCompat() {
        assertEquals(LlmProtocol.OpenAiChatCompletions, LlmProtocol.infer("OpenRouter", "sk-abc"))
        assertEquals(LlmProtocol.OpenAiChatCompletions, LlmProtocol.infer("Custom", ""))
    }

    @Test
    fun zenBrandResolvesToChatCompletions() {
        assertEquals(LlmProtocol.OpenAiChatCompletions, LlmProtocol.infer("Zen", ""))
        assertEquals(LlmProtocol.OpenAiChatCompletions, LlmProtocol.infer("zen", "sk-abc"))
    }

    @Test
    fun zenEndpointResolutionFollowsModelFamily() {
        assertEquals(ZenResponsesDefaultBaseUrl, LlmProtocol.OpenAiResponses.defaultEndpoint())
        assertEquals(ZenMessagesBaseUrl, LlmProtocol.AnthropicMessages.defaultEndpoint())
        assertEquals(ZenGeminiBase, LlmProtocol.GoogleGemini.defaultEndpoint())
    }

    @Test
    fun fromWireFallsBackToDefault() {
        assertEquals(LlmProtocol.GoogleOpenAi, LlmProtocol.fromWire("google-openai"))
        assertEquals(LlmProtocol.Default, LlmProtocol.fromWire("nope"))
        assertEquals(LlmProtocol.Default, LlmProtocol.fromWire(null))
    }

    @Test
    fun googleDefaultEndpointIsOpenAiCompatBridge() {
        assertTrue(LlmProtocol.GoogleOpenAi.defaultEndpoint().contains("generativelanguage.googleapis.com"))
        assertTrue(LlmProtocol.GoogleOpenAi.defaultEndpoint().endsWith("/v1beta/openai/chat/completions"))
    }
}
