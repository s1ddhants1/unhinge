package io.github.s1ddhants1.unhinge.ai

import io.github.s1ddhants1.unhinge.Consts
import org.junit.Assert.assertEquals
import org.junit.Test

class AiWingmanHelperTest {
    @Test
    fun legacyOpenRouterDefaultMigratesToCurrentDefault() {
        val (_, _, model) = AiWingmanHelper.resolveEffectiveEndpoint(
            provider = "OpenRouter",
            apiKey = "sk-test",
            baseUrl = "",
            model = Consts.LEGACY_OPENROUTER_DEFAULT_MODEL,
        )
        assertEquals(Consts.OPENROUTER_DEFAULT_MODEL, model)
    }

    @Test
    fun tildePrefixedModelIdsAreSanitized() {
        val (_, _, model) = AiWingmanHelper.resolveEffectiveEndpoint(
            provider = "OpenRouter",
            apiKey = "sk-test",
            baseUrl = "",
            model = "~deepseek/deepseek-v4-flash-latest",
        )
        assertEquals("deepseek/deepseek-v4-flash-latest", model)
    }

    @Test
    fun blankModelFallsBackToCurrentDefault() {
        val (_, _, model) = AiWingmanHelper.resolveEffectiveEndpoint(
            provider = "OpenRouter",
            apiKey = "sk-test",
            baseUrl = "",
            model = "",
        )
        assertEquals(Consts.OPENROUTER_DEFAULT_MODEL, model)
    }
}
