package io.github.s1ddhants1.unhinge.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WingmanPromptsTest {
    @Test
    fun openerSystemPromptIsMinimalWithoutCustom() {
        val system = WingmanPrompts.openerSystemPrompt(2, "")
        assertTrue(system.contains("You are a dating wingman AI assistant for Hinge."))
        assertTrue(system.contains("Output ONLY a valid JSON object {\"lines\": [...]} with EXACTLY 2 opening replies, one per input prompt."))
    }

    @Test
    fun customPromptMergesAsMiddleTier() {
        val system = WingmanPrompts.openerSystemPrompt(3, "Be brief ({lineCount} max).")
        assertTrue(system.contains("You are a dating wingman AI assistant for Hinge."))
        assertTrue(system.contains("Be brief (3 max)."))
        assertTrue(system.contains("EXACTLY 3 opening replies"))
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
        assertTrue(user.contains("Energy & Length Calibration", ignoreCase = true))
        assertTrue(user.contains("match the energy", ignoreCase = true))
        assertTrue(user.contains("hey/hi/hello", ignoreCase = true))
        assertTrue(user.contains("bold claim", ignoreCase = true))
        assertTrue(user.contains("challenge accepted", ignoreCase = true))
        assertTrue(user.contains("plot twist", ignoreCase = true))
        assertTrue(user.contains("EXHAUSTIVE HUMAN TEXTING RULES", ignoreCase = true))
        assertTrue(user.contains("FEW-SHOT CALIBRATION EXAMPLES", ignoreCase = true))
        assertTrue(user.contains("STRICT TOPICAL ISOLATION", ignoreCase = true))
        assertTrue(user.contains("FORBIDDEN SYNTHETIC SLANG", ignoreCase = true))
        assertTrue(user.contains("FORBIDDEN PATTERN", ignoreCase = true))
        assertTrue(user.contains("heavy lifting", ignoreCase = true))
        assertTrue(user.contains("bet you", ignoreCase = true))
        assertTrue(user.contains("full time job", ignoreCase = true))
        assertTrue(user.contains("submit my resume", ignoreCase = true))
        assertTrue(user.contains("Mock Skeptic", ignoreCase = true))
        assertTrue(user.contains("Niche Item Callback", ignoreCase = true))
        assertTrue(user.contains("Relatable Micro-Debate", ignoreCase = true))
        assertTrue(user.contains("Backstory Inquirer", ignoreCase = true))
        assertTrue(user.contains("Collaborative Condition", ignoreCase = true))
    }

    @Test
    fun customTemplateOverridesDefaultAndSubstitutesVariables() {
        val customTemplate = "CUSTOM TEMPLATE for {lineCount} prompts:\n{profile}{prompts}{avoid}"
        val formatted = WingmanPrompts.openerUserPrompt(
            text = "Q: What is love?",
            avoidReplies = listOf("Baby don't hurt me"),
            profileBlock = "Name: Bob",
            template = customTemplate,
        )
        assertTrue(formatted.startsWith("CUSTOM TEMPLATE for 1 prompts:"))
        assertTrue(formatted.contains("Profile:\nName: Bob"))
        assertTrue(formatted.contains("Q: What is love?"))
        assertTrue(formatted.contains("- \"Baby don't hurt me\""))
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
