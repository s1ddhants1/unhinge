package io.github.s1ddhants1.unhinge.ai

object PromptSanitizer {
    private val DELIMITER_TAG_REGEX = Regex(
        "</?(?:candidate_context|directional_stimulus|system|user|assistant|instruction)[^>]*>",
        RegexOption.IGNORE_CASE
    )

    private val REASONING_PREAMBLE_REGEX = Regex(
        "^(?:Why this works|Why it works|Opener \\d+|Reply \\d+|Suggestion \\d+|Option \\d+|Line \\d+|Thought|Analysis):\\s*",
        RegexOption.IGNORE_CASE
    )

    private val CONVERSATIONAL_THROAT_CLEARING_REGEX = Regex(
        "^(?:(?:hey|hi|hello)\\s*[,!]?\\s*|(?:fair enough|okay noted|love that|solid plan|valid fear|great choice)\\s*[,!]?\\s*|respect\\s*[,!.]\\s*)",
        RegexOption.IGNORE_CASE
    )

    private val MULTIPLE_QUESTIONS_REGEX = Regex("\\?.*\\?")

    val BANNED_AI_CLICHES = listOf(
        "doing some heavy lifting",
        "doing the heavy lifting",
        "doing heavy lifting",
        "heavy lifting here",
        "carrying this whole",
        "bet you",
        "i bet",
        "bet that",
        "willing to bet",
        "full time job",
        "full-time job",
        "submit my resume",
        "sending my resume",
        "are you hiring",
        "starting salary",
        "sounds exhausting",
        "sounds tiring",
        "bold claim",
        "challenge accepted",
        "you look like",
        "you strike me as",
        "plot twist",
        "hear me out",
        "tell me i'm wrong",
        "unpopular opinion",
        "delve",
        "tapestry",
        "realm",
        "ecosystem",
        "testament",
        "masterclass",
        "main character energy",
        "giving energy",
        "has entered the chat",
    )

    fun sanitizeUntrusted(text: String): String {
        if (text.isBlank()) return ""
        val stripped = text.replace(DELIMITER_TAG_REGEX) { match ->
            match.value.replace("<", "‹").replace(">", "›")
        }
        return stripped.filter { ch -> ch == '\n' || ch == '\r' || ch == '\t' || !ch.isISOControl() }
    }

    fun cleanOpenerReply(raw: String): String {
        var line = raw.trim()
        if (line.isBlank()) return ""

        line = line.removePrefix(">").trim()
            .removePrefix("-").trim()
            .removePrefix("*").trim()
            .replace(Regex("^\\d+[.):]\\s*"), "")
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .removeSurrounding("`")
            .trim()

        line = line.replace(REASONING_PREAMBLE_REGEX, "").trim()
            .removeSurrounding("\"")
            .trim()

        line = line.replace(CONVERSATIONAL_THROAT_CLEARING_REGEX, "").trim()

        if (MULTIPLE_QUESTIONS_REGEX.containsMatchIn(line)) {
            val firstQ = line.indexOf('?')
            if (firstQ >= 0 && firstQ < line.length - 1) {
                line = line.substring(0, firstQ + 1).trim()
            }
        }

        line = line.removeSuffix(";").trim()

        return line
    }

    fun auditLine(line: String): QualityAuditResult {
        val violations = mutableListOf<String>()
        val lower = line.lowercase()

        for (cliche in BANNED_AI_CLICHES) {
            if (lower.contains(cliche)) {
                violations.add("Contains banned AI cliché: \"$cliche\"")
            }
        }

        val wordCount = line.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        if (wordCount > 28) {
            violations.add("Exceeds conversational length ($wordCount words > 28)")
        }

        val questionCount = line.count { it == '?' }
        if (questionCount > 1) {
            violations.add("Multiple questions ($questionCount questions)")
        }

        return QualityAuditResult(
            line = line,
            isValid = violations.isEmpty(),
            violations = violations,
        )
    }

    data class QualityAuditResult(
        val line: String,
        val isValid: Boolean,
        val violations: List<String>,
    )
}
