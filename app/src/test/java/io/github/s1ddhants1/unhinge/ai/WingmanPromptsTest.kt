package io.github.s1ddhants1.unhinge.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WingmanPromptsTest {
    @Test
    fun openerSystemPromptIsMinimalWithoutCustom() {
        assertEquals(
            "You are a dating wingman AI assistant for Hinge.\n" +
                "Output ONLY a valid JSON object {\"lines\": [...]} with EXACTLY 2 opening replies, one per input prompt.",
            WingmanPrompts.openerSystemPrompt(2, ""),
        )
    }

    @Test
    fun customPromptMergesAsMiddleTier() {
        val system = WingmanPrompts.openerSystemPrompt(3, "Be brief ({lineCount} max).")
        val lines = system.lines()
        assertEquals(WingmanPrompts.ROLE, lines.first())
        assertTrue(system.contains("Be brief (3 max)."))
        assertTrue(lines.last().contains("EXACTLY 3 opening replies"))
    }

    @Test
    fun openerUserPromptIncludesAvoidList() {
        val user = WingmanPrompts.openerUserPrompt("Q: A", listOf("old opener"))
        assertTrue(user.contains("- \"old opener\""))
        assertTrue(user.contains("EXACTLY 1 opening replies"))
    }

    @Test
    fun openerUserPromptIncludesProfileBlockBeforePrompts() {
        val user = WingmanPrompts.openerUserPrompt("Q: A", emptyList(), "Ada, 27\nWork: Designer")
        val profileIdx = user.indexOf("Profile:\nAda, 27")
        val promptsIdx = user.indexOf("Candidate Prompts (1 items):")
        assertTrue(profileIdx >= 0)
        assertTrue(promptsIdx > profileIdx)
    }

    @Test
    fun openerUserPromptOmitsProfileSectionWhenBlank() {
        assertFalse(WingmanPrompts.openerUserPrompt("Q: A", emptyList()).contains("Profile:"))
    }

    @Test
    fun openerUserPromptEncodesReplyBestPractices() {
        val user = WingmanPrompts.openerUserPrompt("Q: A", emptyList())
        assertTrue(user.contains("comment + question", ignoreCase = true))
        assertTrue(user.contains("under 25 words", ignoreCase = true))
        assertTrue(user.contains("hey/hi/hello", ignoreCase = true))
    }

    @Test
    fun askAiPromptAppendsCustomInstructions() {
        val system = WingmanPrompts.askAiSystemPrompt("Name: Ada", "custom rules")
        assertTrue(system.contains("Candidate Profile:\nName: Ada"))
        assertTrue(system.contains("Additional System Instructions:\ncustom rules"))
    }

    @Test
    fun askAiPromptOmitsCustomBlockWhenBlank() {
        assertFalse(WingmanPrompts.askAiSystemPrompt("Name: Ada", "").contains("Additional System"))
    }
}
