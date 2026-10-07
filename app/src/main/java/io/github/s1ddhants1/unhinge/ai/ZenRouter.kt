package io.github.s1ddhants1.unhinge.ai

import io.github.s1ddhants1.unhinge.Consts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Request
import java.security.SecureRandom
import java.util.Locale

const val ZenChatBaseUrl = "https://opencode.ai/zen/v1/chat/completions"
const val ZenMessagesBaseUrl = "https://opencode.ai/zen/v1/messages"
const val ZenResponsesDefaultBaseUrl = "https://opencode.ai/zen/v1/responses"
const val ZenGeminiBase = "https://opencode.ai/zen/v1/models"

object ZenRouter {
    const val OPENCODE_PROJECT_ID = "fbfbb8ecca77fbf4f585927700f1baa979cb6a94"
    const val OPENCODE_USER_AGENT = "opencode/latest/2.0.18/cli"
    const val OPENCODE_CLIENT = "cli"

    val FreeModels: List<String> =
        listOf(
            "space-bunny-free",
            "muse-spark-1.3-contributor-free",
            "muse-spark-1.3-free",
            "big-pickle",
            "fledge-alpha-free",
            "ling-3.1-flash-free",
            "longcat-2.5-preview-free",
            "mimo-v2.6-flash-free",
            "mimo-v2.5-free",
            "nemotron-3-ultra-free",
            "nemotron-3.5-lightning-free",
        )

    private val ResponsesModels =
        setOf(
            "gpt-6-astra", "gpt-6-sol", "gpt-6.1-sol", "gpt-6-luna",
            "gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna",
            "gpt-5.5", "gpt-5.5-pro", "gpt-5.4", "gpt-5.4-pro", "gpt-5.4-mini", "gpt-5.4-nano",
            "gpt-5.3-codex", "gpt-5.3-codex-spark",
            "gpt-5.2", "gpt-5.2-codex",
            "gpt-5.1", "gpt-5.1-codex", "gpt-5.1-codex-max", "gpt-5.1-codex-mini",
            "gpt-5", "gpt-5-codex", "gpt-5-nano",
            "grok-4.7", "grok-4.6", "grok-4.5", "grok-build-0.1",
            "muse-spark-1.3", "muse-spark-1.2",
            "muse-spark-1.3-contributor-free", "muse-spark-1.3-free",
            "muse-spark-1.2-contributor-free", "muse-spark-1.2-free",
        )

    private val MessagesModels =
        setOf(
            "claude-fable-5-1", "claude-fable-5",
            "claude-opus-5-5", "claude-opus-5", "claude-opus-4-8", "claude-opus-4-7",
            "claude-opus-4-6", "claude-opus-4-5",
            "claude-sonnet-5-5", "claude-sonnet-5", "claude-sonnet-4-6", "claude-sonnet-4-5",
            "claude-sonnet-4", "claude-haiku-4-5",
            "qwen3.8-flash", "qwen3.7-max", "qwen3.7-plus", "qwen3.6-plus", "qwen3.5-plus", "qwen3.6-plus-free",
            "minimax-m3-free", "minimax-m2.5-free",
        )

    private val GeminiModels =
        setOf(
            "gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.6-flash",
            "gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-3.1-pro", "gemini-3-flash",
        )

    fun normalizeModelId(model: String): String {
        val sanitized = sanitizeModelId(model)
        val id = sanitized.lowercase()
        return when (id) {
            "muse-spark-1.3-free" -> "muse-spark-1.3-contributor-free"
            "muse-spark-1.2-free" -> "muse-spark-1.2-contributor-free"
            else -> sanitized
        }
    }

    fun isFreeModel(model: String): Boolean {
        val id = normalizeModelId(model).lowercase()
        return id.isBlank() || id in FreeModels || id.contains("free") || id == "big-pickle"
    }

    fun isZenUrl(url: String): Boolean = url.contains("opencode.ai", ignoreCase = true)

    fun resolve(model: String): Triple<LlmProtocol, String, String> {
        val normalized = normalizeModelId(model)
        val id = normalized.lowercase()
        return when {
            id.isBlank() -> Triple(LlmProtocol.OpenAiChatCompletions, Consts.ZEN_DEFAULT_BASE_URL, Consts.ZEN_DEFAULT_MODEL)
            ResponsesModels.contains(id) -> Triple(LlmProtocol.OpenAiResponses, ZenResponsesDefaultBaseUrl, normalized)
            MessagesModels.contains(id) -> Triple(LlmProtocol.AnthropicMessages, ZenMessagesBaseUrl, normalized)
            GeminiModels.contains(id) -> Triple(LlmProtocol.GoogleGemini, "$ZenGeminiBase/$normalized", normalized)
            else -> Triple(LlmProtocol.OpenAiChatCompletions, ZenChatBaseUrl, normalized)
        }
    }

    fun generateSessionId(): String {
        val now = System.currentTimeMillis()
        val p = 1L
        val n = (now shl 12) + p
        val a = n.inv()
        val sb = StringBuilder("ses_")
        for (m in 0 until 6) {
            val shift = 40 - 8 * m
            val byteVal = ((a shr shift) and 0xffL).toInt()
            sb.append(String.format(Locale.US, "%02x", byteVal))
        }
        val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        val random = SecureRandom()
        val randomBytes = ByteArray(14)
        random.nextBytes(randomBytes)
        for (b in randomBytes) {
            val unsigned = b.toInt() and 0xff
            sb.append(alphabet[unsigned % 62])
        }
        return sb.toString()
    }

    fun generateTraceHeaders(): Pair<String, String> {
        val random = SecureRandom()
        val traceBytes = ByteArray(16)
        val spanBytes = ByteArray(8)
        val parentSpanBytes = ByteArray(8)
        random.nextBytes(traceBytes)
        random.nextBytes(spanBytes)
        random.nextBytes(parentSpanBytes)
        val traceHex = traceBytes.joinToString("") { String.format(Locale.US, "%02x", it) }
        val spanHex = spanBytes.joinToString("") { String.format(Locale.US, "%02x", it) }
        val parentHex = parentSpanBytes.joinToString("") { String.format(Locale.US, "%02x", it) }
        return "00-$traceHex-$spanHex-01" to "$traceHex-$spanHex-1-$parentHex"
    }

    fun generateTraceparent(): String = generateTraceHeaders().first

    fun injectZenHeaders(
        builder: Request.Builder,
        apiKey: String,
        model: String = "",
        sessionId: String = generateSessionId(),
    ) {
        val effectiveKey = if (isFreeModel(model)) {
            if (model.isBlank() && apiKey.isNotBlank()) apiKey.trim() else "public"
        } else {
            apiKey.trim().ifBlank { "public" }
        }
        val (traceparent, b3) = generateTraceHeaders()
        builder.header("Authorization", "Bearer $effectiveKey")
        builder.header("User-Agent", OPENCODE_USER_AGENT)
        builder.header("x-opencode-client", OPENCODE_CLIENT)
        builder.header("x-opencode-project", OPENCODE_PROJECT_ID)
        builder.header("x-opencode-session", sessionId)
        builder.header("x-session-affinity", sessionId)
        builder.header("x-session-id", sessionId)
        builder.header("traceparent", traceparent)
        builder.header("b3", b3)
    }

    private val zenJson = Json { ignoreUnknownKeys = true }

    private const val OPENCODE_TOOLS_JSON =
        """[{"type":"function","function":{"name":"edit","description":"Edit the contents of a file by finding and replacing exact text. When editing text from Read output, preserve the exact indentation (tabs or spaces) and omit the line-number prefix, such as `1: `. Never include the prefix in oldString or newString. The edit fails if oldString is not found. By default, oldString must identify a UNIQUE location. Multiple matches FAIL unless replaceAll is true. Add more surrounding context to disambiguate, or set replaceAll to true to replace every occurrence. Use replaceAll when the change should apply to every occurrence, such as renaming a variable.","parameters":{"type":"object","properties":{"path":{"type":"string","description":"File to edit"},"oldString":{"type":"string","description":"Exact text to find and replace"},"newString":{"type":"string","description":"Text to replace oldString with (must differ from oldString)"},"replaceAll":{"type":"boolean","description":"Whether to replace every occurrence of oldString. When false, oldString must match exactly once. Defaults to false."}},"required":["path","oldString","newString"],"additionalProperties":false},"strict":false}},{"type":"function","function":{"name":"glob","description":"Search file paths using a glob pattern (examples: \"**/*.ts\", \"src/**/*.tsx\").","parameters":{"type":"object","properties":{"pattern":{"type":"string","description":"Glob pattern to match files against"},"path":{"type":"string","description":"Directory to search. Defaults to the current working directory."},"hidden":{"type":"boolean","description":"Include hidden files and directories (default: false)."},"limit":{"type":"integer","exclusiveMinimum":0,"description":"Maximum number of matching files to return (default: 100)"}},"required":["pattern"],"additionalProperties":false},"strict":false}},{"type":"function","function":{"name":"grep","description":"Search file contents using ripgrep's regular expression syntax or literal text matching. Use it to locate specific code, symbols, or text patterns, and narrow searches with `path` or `include`. Returns matching file paths, line numbers, and line previews.","parameters":{"type":"object","properties":{"pattern":{"type":"string","minLength":1,"description":"Regular expression or literal text to match in file contents."},"path":{"type":"string","description":"File or directory to search. Defaults to the current working directory."},"include":{"type":"string","description":"Glob pattern to filter files (for example, \"*.js\" or \"*.{ts,tsx}\")"},"literal":{"type":"boolean","description":"Treat `pattern` as exact text instead of a regular expression (default: false)."},"caseSensitive":{"type":"boolean","description":"Use case-sensitive matching (default: true)."},"limit":{"type":"integer","exclusiveMinimum":0,"description":"Maximum number of matching lines to return (default: 100)"}},"required":["pattern"],"additionalProperties":false},"strict":false}},{"type":"function","function":{"name":"question","description":"Use this tool when you need to ask the user questions during execution. This allows you to:\n1. Gather user preferences or requirements\n2. Clarify ambiguous instructions\n3. Get decisions on implementation choices as you work\n4. Offer choices to the user about what direction to take.\n\nUsage notes:\n- A \"Type your own answer\" option is added automatically; don't include a separate option for free form answers\n- Set `multiple: true` to allow selecting more than one option\n- If you recommend a specific option, make that the first option in the list and add \"(Recommended)\" at the end of the label","parameters":{"type":"object","properties":{"questions":{"type":"array","items":{"type":"object","properties":{"question":{"type":"string","description":"Complete question"},"header":{"type":"string","description":"Very short label (max 30 chars)"},"options":{"type":"array","items":{"type":"object","properties":{"label":{"type":"string","description":"Display text (1-5 words, concise)"},"description":{"type":"string","description":"Explanation of choice"}},"required":["label","description"],"additionalProperties":false},"description":"Available choices"},"multiple":{"type":"boolean"}},"required":["question","header","options"],"additionalProperties":false},"minItems":1,"description":"Questions to ask"}},"required":["questions"],"additionalProperties":false},"strict":false}},{"type":"function","function":{"name":"read","description":"Read the contents of a file or directory. Supports text files, images, and PDFs. Images and PDFs are presented directly to the model. Each text line is prefixed by its 1-based line number as <line>: <content>. The prefix is for reference and is not part of the file content. Directory entries are returned one per line. Use offset and limit to read large files or directories in sections. Prefer one larger read over many small slices, and use grep to find specific content in large files.","parameters":{"type":"object","properties":{"path":{"type":"string","description":"File or directory to read"},"offset":{"type":"integer","minimum":0,"description":"The line or directory entry to start reading from (1-based)"},"limit":{"type":"integer","minimum":0,"description":"The maximum number of lines or directory entries to read (defaults to and capped at 2000)"}},"required":["path"],"additionalProperties":false},"strict":false}},{"type":"function","function":{"name":"shell","description":"Execute a shell command and return its output. Commands run on android using bash. Quote file paths containing spaces or special characters. Prefer dedicated tools over shell commands when possible. When output is large, the full result is saved to a file and a truncated preview is returned. Rely on automatic truncation unless filtering the output is more useful. Commands accept an optional timeout, background commands have no timeout by default. Background commands return immediately, and you will be notified when they complete.","parameters":{"type":"object","properties":{"command":{"type":"string","description":"Shell command string to execute"},"workdir":{"type":"string","description":"Working directory to execute the command in. Defaults to the current working directory. When possible, avoid changing directories in the command and set the working directory here instead."},"timeout":{"type":"integer","minimum":0,"description":"Timeout in milliseconds. Set to 0 to disable the timeout. Defaults to 120000 for foreground commands. Background commands have no timeout by default."},"background":{"type":"boolean","description":"Run the command in the background and return immediately (useful for dev servers and long-running builds). You do not need to use '&' at the end of the command when using this parameter. You will be notified when it completes. DO NOT poll for completion."}},"required":["command"],"additionalProperties":false},"strict":false}},{"type":"function","function":{"name":"skill","description":"Load a specialized skill's instructions and resources into the current conversation when the task at hand matches its description.\n\nThe skill ID must match an available skill or a skill explicitly referenced by the user.","parameters":{"type":"object","properties":{"id":{"type":"string","description":"The ID of an available skill or a skill explicitly referenced by the user"}},"required":["id"],"additionalProperties":false},"strict":false}},{"type":"function","function":{"name":"subagent","description":"Spawns an agent in a child session to work on the specified task.\nThe output includes a sessionID you can pass back later to continue that specific conversation with the subagent.\nNew child sessions start with fresh context, so include all relevant context and instructions when you don't pass a sessionID.\nForeground (default) runs the subagent to completion and returns its final response.\nBackground mode (background=true) launches it asynchronously and returns immediately; you are notified when it finishes.\nUse background only for independent work that can run while you continue elsewhere.\n\nAvailable subagents:\n- explore: Fast agent specialized for exploring codebases. Use this when you need to quickly find files by patterns (eg. \"src/components/**/*.tsx\"), search code for keywords (eg. \"API endpoints\"), or answer questions about the codebase (eg. \"how do API endpoints work?\"). When calling this agent, specify the desired thoroughness level: \"quick\" for basic searches, \"medium\" for moderate exploration, or \"very thorough\" for comprehensive analysis across multiple locations and naming conventions.\n- general: General-purpose agent for researching complex questions and executing multi-step tasks. Use this agent to execute multiple units of work in parallel.","parameters":{"type":"object","properties":{"agent":{"type":"string","description":"The type of specialized agent to use for this task. If the user asks for a subagent by a name that is not one of the available subagents, they most likely mean a model: pick a suitable agent and pass the name through the model parameter instead."},"description":{"type":"string","description":"A short 3-5 word label for the task, displayed to the user"},"prompt":{"type":"string","description":"The task for the subagent to perform"},"model":{"type":"string","description":"NEVER set this unless the user explicitly asks for a particular model or variant. The value is written as \"providerID/modelID\", or \"providerID/modelID#variant\" to include a variant. Do not guess the ID: look the model up with the models tool, filtering to your own provider first."},"sessionID":{"type":"string","pattern":"^ses","description":"Continue a specific previous subagent conversation by passing its sessionID. Calls without a sessionID start a new conversation."},"background":{"type":"boolean","description":"Run the subagent in the background and return immediately. You will be notified when it completes. DO NOT sleep, poll, or proactively check on its progress."}},"required":["agent","description","prompt"],"additionalProperties":false},"strict":false}},{"type":"function","function":{"name":"webfetch","description":"Fetch content from an HTTP or HTTPS URL and return it as text, markdown, or HTML. Markdown is the default.\n\nUse a more targeted tool when one is available. This tool is read-only. Large text results may be replaced with a preview while the complete output is retained in managed storage.","parameters":{"type":"object","properties":{"url":{"type":"string","description":"The HTTP or HTTPS URL to fetch content from"},"format":{"type":"string","enum":["text","markdown","html"],"description":"The format to return the content in. Defaults to markdown."},"timeout":{"type":"number","exclusiveMinimum":0,"maximum":120,"description":"Optional timeout in seconds (maximum: 120)"}},"required":["url"],"additionalProperties":false},"strict":false}},{"type":"function","function":{"name":"websearch","description":"Search the web using the user's selected search integration. Use this for current information beyond knowledge cutoff.\n\nThe current year is 2026. Use this year when searching for recent information or current events.","parameters":{"type":"object","properties":{"query":{"type":"string","description":"Websearch query"}},"required":["query"],"additionalProperties":false},"strict":false}},{"type":"function","function":{"name":"write","description":"Writes a file to the local filesystem, overwriting if one exists.\n\nMissing parent directories are created automatically.\n\nUse this tool to create new files or overwrite existing files. For partial changes, use the edit tool instead.","parameters":{"type":"object","properties":{"path":{"type":"string","description":"Path to the file to write to"},"content":{"type":"string","description":"Content to write to the file"}},"required":["path","content"],"additionalProperties":false},"strict":false}},{"type":"function","function":{"name":"execute","description":"Run JavaScript in a confined Code Mode runtime to script tool calls and HTTP requests and compose their results.\n`fetch` is available for HTTP requests. Imports, direct filesystem access, and timers are unavailable; all other external access goes through `tools`.\nWithin `{ code }`, the only callable tools are those explicitly listed in the Code Mode catalog instructions or returned by the `search` function. Inside `{ code }`, ignore tools shown outside the Code Mode catalog. They are not available in the Code Mode runtime.\nCall tools through `tools` using only exact paths and signatures from the catalog. Do not infer or normalize tool names; preserve bracket notation such as `tools.<namespace>[\"tool-name\"](input)`.\nPrefer an explicit `return`; if omitted, the final top-level expression becomes the result.\nAwait every call whose completion matters; pending calls are interrupted when execution ends. Run independent calls concurrently with `Promise.all`.","parameters":{"type":"object","properties":{"code":{"type":"string"}},"required":["code"],"additionalProperties":false},"strict":false}}]"""

    val DefaultZenChatTools: JsonArray by lazy {
        zenJson.parseToJsonElement(OPENCODE_TOOLS_JSON).jsonArray
    }

    val DefaultZenResponsesTools: JsonArray by lazy {
        buildJsonArray {
            DefaultZenChatTools.forEach { toolElement ->
                val toolObj = toolElement as? JsonObject ?: return@forEach
                val fnObj = toolObj["function"] as? JsonObject ?: return@forEach
                add(
                    buildJsonObject {
                        put("type", "function")
                        put("name", fnObj["name"]?.jsonPrimitive?.contentOrNull ?: "")
                        put("description", fnObj["description"]?.jsonPrimitive?.contentOrNull ?: "")
                        fnObj["parameters"]?.let { put("parameters", it) }
                        val strictVal = fnObj["strict"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                        put("strict", strictVal)
                    },
                )
            }
        }
    }
}
