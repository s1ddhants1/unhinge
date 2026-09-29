package io.github.s1ddhants1.unhinge.ai

import kotlinx.coroutines.flow.MutableStateFlow

data class PromptEntry(
    val text: String,
    val suggestedReplyFlow: MutableStateFlow<String?> = MutableStateFlow(null)
) {
    val translatedTextFlow: MutableStateFlow<String?> get() = suggestedReplyFlow
}
