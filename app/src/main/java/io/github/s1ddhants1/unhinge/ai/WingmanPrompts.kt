package io.github.s1ddhants1.unhinge.ai

import android.content.Context
import io.github.s1ddhants1.unhinge.util.PreferencesManager

object WingmanPrompts {
    val ROLE: String get() = PromptRepository.getRole()

    fun openerSystemPrompt(
        lineCount: Int,
        custom: String = "",
        template: String? = null,
        prefs: PreferencesManager? = null,
        context: Context? = null,
    ): String {
        val effectiveTemplate = template?.takeIf { it.isNotBlank() }
            ?: PromptRepository.getEffectiveSystemPromptTemplate(prefs, context)
        return PromptRepository.formatOpenerSystemPrompt(
            template = effectiveTemplate,
            lineCount = lineCount,
            customInstructions = custom,
        )
    }

    fun openerUserPrompt(
        text: String,
        avoidReplies: List<String> = emptyList(),
        profileBlock: String = "",
        template: String? = null,
        prefs: PreferencesManager? = null,
        context: Context? = null,
        directionalStimulus: String = "",
        promptCount: Int? = null,
    ): String {
        val effectiveTemplate = template?.takeIf { it.isNotBlank() }
            ?: PromptRepository.getEffectiveOpenerTemplate(prefs, context)
        return PromptRepository.formatOpenerUserPrompt(
            template = effectiveTemplate,
            promptsText = text,
            avoidReplies = avoidReplies,
            profileBlock = profileBlock,
            directionalStimulus = directionalStimulus,
            promptCount = promptCount,
        )
    }

    fun askAiSystemPrompt(
        profileSummary: String,
        custom: String = "",
        template: String? = null,
        prefs: PreferencesManager? = null,
        context: Context? = null,
    ): String {
        val effectiveTemplate = template?.takeIf { it.isNotBlank() }
            ?: PromptRepository.getEffectiveAskAiTemplate(prefs, context)
        return PromptRepository.formatAskAiSystemPrompt(
            template = effectiveTemplate,
            profileSummary = profileSummary,
            customInstructions = custom,
        )
    }
}
