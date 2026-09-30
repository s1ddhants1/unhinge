package io.github.s1ddhants1.unhinge.ai

import kotlinx.coroutines.flow.MutableStateFlow

data class PromptEntry(
    val text: String,
    val suggestedReplyFlow: MutableStateFlow<String?> = MutableStateFlow(null),
    val repliesFlow: MutableStateFlow<List<String>> = MutableStateFlow(emptyList()),
    val activeReplyIndexFlow: MutableStateFlow<Int> = MutableStateFlow(0),
    val isGeneratingFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
) {
    val translatedTextFlow: MutableStateFlow<String?> get() = suggestedReplyFlow

    fun addReply(reply: String, selectNew: Boolean = true) {
        val trimmed = reply.trim()
        if (trimmed.isBlank()) return
        val current = repliesFlow.value.toMutableList()
        val existingIndex = current.indexOf(trimmed)
        if (existingIndex >= 0) {
            if (selectNew) {
                activeReplyIndexFlow.value = existingIndex
                suggestedReplyFlow.value = trimmed
            }
            return
        }
        current.add(trimmed)
        repliesFlow.value = current
        if (selectNew) {
            activeReplyIndexFlow.value = current.size - 1
            suggestedReplyFlow.value = trimmed
        }
    }

    fun selectPreviousReply() {
        val current = repliesFlow.value
        if (current.isEmpty()) return
        val nextIdx = (activeReplyIndexFlow.value - 1).coerceAtLeast(0)
        activeReplyIndexFlow.value = nextIdx
        suggestedReplyFlow.value = current[nextIdx]
    }

    fun selectNextReply() {
        val current = repliesFlow.value
        if (current.isEmpty()) return
        val nextIdx = (activeReplyIndexFlow.value + 1).coerceAtMost(current.size - 1)
        activeReplyIndexFlow.value = nextIdx
        suggestedReplyFlow.value = current[nextIdx]
    }
}
