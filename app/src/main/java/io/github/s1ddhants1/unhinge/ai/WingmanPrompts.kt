package io.github.s1ddhants1.unhinge.ai

/**
 * Two-tier wingman prompts (cf. Zafiro `PromptComposer` stable/volatile tiers).
 *
 * Stable tier (role + JSON contract) is always present, so a custom prompt can
 * never drop the machine-readable envelope — it lands as a middle tier instead
 * of replacing the whole system message. Volatile tier carries per-request
 * candidate data. All hardcoded prompts live here; nothing in `strings.xml`
 * (LLM prompts are logic, never localized).
 */
object WingmanPrompts {
    const val ROLE = "You are a dating wingman AI assistant for Hinge."

    fun openerSystemPrompt(lineCount: Int, custom: String): String {
        val customTier = custom.takeIf { it.isNotBlank() }?.replace("{lineCount}", lineCount.toString())
        return listOfNotNull(
            ROLE,
            customTier,
            """Output ONLY a valid JSON object {"lines": [...]} with EXACTLY $lineCount opening replies, one per input prompt.""",
        ).joinToString("\n")
    }

    fun openerUserPrompt(text: String, avoidReplies: List<String>, profileBlock: String = ""): String {
        val lineCount = text.lines().size
        val avoid = if (avoidReplies.isNotEmpty()) {
            "\n\nPreviously generated replies (generate a completely fresh and DIFFERENT opener; do NOT repeat or reuse similar angles):\n" +
                avoidReplies.joinToString("\n") { "- \"$it\"" }
        } else ""
        val profile = profileBlock.takeIf { it.isNotBlank() }?.let { "Profile:\n$it\n\n" } ?: ""
        return """Generate opening replies for the following candidate prompts.$avoid

${profile}Candidate Prompts ($lineCount items):
$text

Human texting rules (data-backed consensus for maximum reply rate):
- Strict length: under 25 words, sweet spot 8-16 words (40-90 characters). Shorter beats longer.
- Focus on them: minimize self-focused "I" pronouns ("I think", "I love", "I usually").
- Anchor to an exact noun: cite a specific place name, brand, dish, band, or detail from their prompt rather than generic terms.
- Low-friction hook: react first, then ask at most one effortless, easy-to-answer question (comment + question). Never interview.
- STRICTLY BAN AI clichés and dramatic preambles: NEVER use "bold claim", "challenge accepted", "you look like", "you strike me as", "plot twist", "hear me out", "are we talking", "on a scale of 1-10", "tell me I'm wrong".
- Zero cringey flattery (banned: "cute", "hot", "sexy", "gorgeous").
- Natural casual texting: mostly lowercase or relaxed capitalization, understated dry humor, zero throat-clearing.
- Never open with just hey/hi/hello, never interview with multiple questions, never ask for number/socials yet.

Output MUST be a JSON object {"lines": [...]} with EXACTLY $lineCount opening replies."""
    }

    fun askAiSystemPrompt(profileSummary: String, custom: String): String =
        buildString {
            appendLine(ROLE)
            appendLine("Candidate Profile:")
            appendLine(profileSummary)
            appendLine()
            append("Respond directly with natural, human-sounding openers or advice. Text casually like a real person, never like an AI bot. No filler, no theater-kid preambles ('bold claim', 'you look like', 'challenge accepted').")
            if (custom.isNotBlank()) append("\nAdditional System Instructions:\n$custom")
        }
}
