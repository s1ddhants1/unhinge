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

What earns replies (dating-app consensus):
- Lead with one specific detail from their prompt or profile; exact beats generic.
- React first, then ask exactly one easy, fun, open-ended question (comment + question).
- Keep it short: 1-2 sentences, under 25 words. A Hinge comment, not an essay.
- Playful and confident; light teasing of their answer is fine, never mean, never sexual, never about looks alone.
- Mirror their energy: riff if they are funny, be genuine if they are sincere.
- Never open with just hey/hi/hello, never interview with multiple questions, never ask for number/socials yet.

Output MUST be a JSON object {"lines": [...]} with EXACTLY $lineCount opening replies."""
    }

    fun askAiSystemPrompt(profileSummary: String, custom: String): String =
        buildString {
            appendLine(ROLE)
            appendLine("Candidate Profile:")
            appendLine(profileSummary)
            appendLine()
            append("Respond directly with openers or advice. No filler.")
            if (custom.isNotBlank()) append("\nAdditional System Instructions:\n$custom")
        }
}
