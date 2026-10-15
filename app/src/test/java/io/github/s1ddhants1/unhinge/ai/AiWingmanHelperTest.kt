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

    @Test
    fun formatConversationHistoryWithEmptyMessagesReturnsPlaceholder() {
        val formatted = AiWingmanHelper.formatConversationHistory("Sarah", emptyList())
        assertEquals("No messages sent yet. (Fresh match!)", formatted)
    }

    @Test
    fun formatConversationHistoryFormatsTextAndVoiceNotesCorrectly() {
        val messages = listOf(
            io.github.s1ddhants1.unhinge.model.ChatMessageRecord(
                body = "Hey! How was your weekend?",
                sentBySubject = true,
            ),
            io.github.s1ddhants1.unhinge.model.ChatMessageRecord(
                body = "Pretty good, went hiking in Marin.",
                sentBySubject = false,
            ),
            io.github.s1ddhants1.unhinge.model.ChatMessageRecord(
                isVoiceNote = true,
                voiceTranscript = "Oh nice, which trail did you do?",
                sentBySubject = true,
            ),
            io.github.s1ddhants1.unhinge.model.ChatMessageRecord(
                isVoiceNote = true,
                sentBySubject = false,
            ),
        )
        val formatted = AiWingmanHelper.formatConversationHistory("Sarah", messages)
        val expected = """
            Sarah: Hey! How was your weekend?
            You: Pretty good, went hiking in Marin.
            Sarah: [Voice note: "Oh nice, which trail did you do?"]
            You: [Sent a voice note]
        """.trimIndent()
        assertEquals(expected, formatted)
    }

    @Test
    fun formatConversationHistoryTruncatesTo15Messages() {
        val messages = (1..20).map { i ->
            io.github.s1ddhants1.unhinge.model.ChatMessageRecord(
                body = "Message $i",
                sentBySubject = i % 2 == 1,
            )
        }
        val formatted = AiWingmanHelper.formatConversationHistory("Sarah", messages)
        val lines = formatted.split("\n")
        assertEquals(15, lines.size)
        assertEquals("You: Message 6", lines.first())
        assertEquals("You: Message 20", lines.last())
    }
}

