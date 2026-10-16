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

    @Test
    fun promptEntryTracksFollowUpMessagesAndAddsNewReplies() {
        val entry = PromptEntry(text = "My simple pleasures: Morning espresso")
        assertEquals(0, entry.followUpMessagesFlow.value.size)

        entry.addFollowUpUserMessage("Make it more playful and mention biscotti")
        assertEquals(1, entry.followUpMessagesFlow.value.size)
        val userMsg = entry.followUpMessagesFlow.value.first()
        org.junit.Assert.assertTrue(userMsg.isUser)
        assertEquals("Make it more playful and mention biscotti", userMsg.text)

        entry.addFollowUpAssistantMessage("Only if you dip the biscotti first.")
        assertEquals(2, entry.followUpMessagesFlow.value.size)

        entry.updateLastAssistantMessage("Only if you dip the biscotti first, otherwise we can't talk.")
        assertEquals(2, entry.followUpMessagesFlow.value.size)
        assertEquals(
            "Only if you dip the biscotti first, otherwise we can't talk.",
            entry.followUpMessagesFlow.value.last().text
        )

        entry.addReply("Only if you dip the biscotti first, otherwise we can't talk.", selectNew = true)
        assertEquals(1, entry.repliesFlow.value.size)
        assertEquals(0, entry.activeReplyIndexFlow.value)
        assertEquals(
            "Only if you dip the biscotti first, otherwise we can't talk.",
            entry.suggestedReplyFlow.value
        )
    }

    @Test
    fun steerOpenerPromptFormatsCorrectly() {
        val prompt = WingmanPrompts.steerOpenerPrompt(
            profileBlock = "Work: Architect\nLocation: Brooklyn",
            promptText = "A boundary of mine: Don't touch my playlist",
            currentOpener = "What's the top artist on it?",
            steerInstruction = "Make it teasing and ask about guilty pleasures",
        )
        org.junit.Assert.assertTrue(prompt.contains("Work: Architect"))
        org.junit.Assert.assertTrue(prompt.contains("A boundary of mine: Don't touch my playlist"))
        org.junit.Assert.assertTrue(prompt.contains("What's the top artist on it?"))
        org.junit.Assert.assertTrue(prompt.contains("Make it teasing and ask about guilty pleasures"))
        org.junit.Assert.assertTrue(prompt.contains("Task: Rewrite and refine the opener"))
    }
}


