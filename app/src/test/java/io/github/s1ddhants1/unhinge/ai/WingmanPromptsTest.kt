package io.github.s1ddhants1.unhinge.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WingmanPromptsTest {
    @Test
    fun systemPromptIsMinimalWithoutCustom() {
        val system = WingmanPrompts.systemPrompt(2, "")
        assertTrue(system.contains("You are a Hinge wingman"))
        assertTrue(system.contains("Output ONLY a valid JSON object {\"lines\": [...]} with exactly 2 replies"))
        assertFalse(system.contains("{custom_instructions}"))
    }

    @Test
    fun customPromptMergesAsMiddleTier() {
        val system = WingmanPrompts.systemPrompt(3, "Be brief ({lineCount} max).")
        assertTrue(system.contains("You are a Hinge wingman"))
        assertTrue(system.contains("Be brief (3 max)."))
        assertTrue(system.contains("exactly 3 replies"))
    }

    @Test
    fun systemPromptEncodesVoiceRules() {
        val system = WingmanPrompts.systemPrompt(1, "")
        assertTrue(system.contains("lowercase", ignoreCase = true))
        assertTrue(system.contains("no emojis", ignoreCase = true))
        assertTrue(system.contains("no greetings", ignoreCase = true))
        assertTrue(system.contains("at most one question", ignoreCase = true))
        assertTrue(system.contains("bold claim", ignoreCase = true))
        assertTrue(system.contains("plot twist", ignoreCase = true))
        assertTrue(system.contains("hear me out", ignoreCase = true))
    }

    @Test
    fun openerUserPromptIncludesAvoidList() {
        val user = WingmanPrompts.openerUserPrompt("Q: A", listOf("old opener"))
        assertTrue(user.contains("- \"old opener\""))
        assertTrue(user.contains("Prompts (1):"))
    }

    @Test
    fun openerUserPromptIncludesProfileBlockBeforePrompts() {
        val user = WingmanPrompts.openerUserPrompt("Q: A", emptyList(), "Ada, 27\nWork: Designer")
        val profileIdx = user.indexOf("<profile>\nAda, 27")
        val promptsIdx = user.indexOf("Prompts (1):")
        assertTrue(profileIdx >= 0)
        assertTrue(promptsIdx > profileIdx)
    }

    @Test
    fun openerUserPromptOmitsProfileSectionWhenBlank() {
        assertFalse(WingmanPrompts.openerUserPrompt("Q: A", emptyList()).contains("<profile>"))
    }

    @Test
    fun openerUserPromptIncludesDirectionalStimulus() {
        val user = WingmanPrompts.openerUserPrompt(
            text = "Q: A",
            directionalStimulus = "Micro-Debate"
        )
        assertTrue(user.contains("<directional_stimulus>"))
        assertTrue(user.contains("Micro-Debate"))
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

    @Test
    fun chatReplySystemPromptContainsRequiredConstraints() {
        val prompt = WingmanPrompts.chatReplySystemPrompt(replyCount = 4, custom = "Be brief")
        assertTrue(prompt.contains("You are a Hinge wingman"))
        assertTrue(prompt.contains("Be brief"))
        assertTrue(prompt.contains("exactly 4 replies"))
    }

    @Test
    fun chatReplyUserPromptFormatsChatContextAndAvoidList() {
        val userPrompt = WingmanPrompts.chatReplyUserPrompt(
            matchName = "Taylor",
            conversationHistoryText = "Taylor: Coffee tomorrow?\nYou: Which spot?",
            profileBlock = "Taylor, 26",
            avoidReplies = listOf("Sounds like a plan"),
            replyCount = 4,
        )
        assertTrue(userPrompt.contains("<match_context>"))
        assertTrue(userPrompt.contains("Match: Taylor"))
        assertTrue(userPrompt.contains("Taylor, 26"))
        assertTrue(userPrompt.contains("<conversation_history>"))
        assertTrue(userPrompt.contains("Taylor: Coffee tomorrow?"))
        assertTrue(userPrompt.contains("- \"Sounds like a plan\""))
        assertTrue(userPrompt.contains("1. Playful Tease / Banter"))
        assertTrue(userPrompt.contains("2. Date Pitch / Move to Meet"))
        assertTrue(userPrompt.contains("3. Witty Callback / Story"))
        assertTrue(userPrompt.contains("4. Casual / Low-Key"))
    }
}
