package io.github.s1ddhants1.unhinge.ai

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory cache for generated dating prompt replies and match chat suggestions.
 * Prevents redundant LLM API calls and avoids re-querying models for identical profile contexts.
 */
object AiWingmanCache {
    private val replyCache = ConcurrentHashMap<String, List<String>>()

    fun createKey(
        text: String,
        profileBlock: String,
        systemPrompt: String,
        temperature: Float,
        topP: Float,
        directionalStimulus: String = "",
    ): String =
        "${text.hashCode()}_${profileBlock.hashCode()}_${systemPrompt.hashCode()}_${temperature}_${topP}_${directionalStimulus.hashCode()}"

    fun get(key: String): List<String>? = replyCache[key]

    fun put(key: String, replies: List<String>) {
        replyCache[key] = replies
    }

    fun remove(key: String): List<String>? = replyCache.remove(key)

    fun clear() {
        replyCache.clear()
    }
}
