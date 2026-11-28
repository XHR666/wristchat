package io.github.xhr666.wristchat.data

import org.json.JSONObject

/** 一行 SSE。工具调用片段留在 raw,由仓库合并。 */
object SseEvents {
    data class Delta(
        val content: String = "",
        val reasoning: String = "",
        val usage: TokenUsage? = null,
        val done: Boolean = false,
        val raw: JSONObject? = null,
    )

    fun parseLine(line: String): Delta? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("data:")) return null
        val payload = trimmed.removePrefix("data:").trim()
        if (payload == "[DONE]") return Delta(done = true)
        val content = SseChunk.deltaField(payload, "content")
        val reasoning = SseChunk.deltaField(payload, "reasoning_content")
        val root = try { JSONObject(payload) } catch (_: Exception) { null }
        if (root == null && content.isEmpty() && reasoning.isEmpty()) return null
        val choice = root?.optJSONArray("choices")?.optJSONObject(0)
        val delta = choice?.optJSONObject("delta") ?: choice?.optJSONObject("message")
        val usage = root?.optJSONObject("usage")?.let { UsageJson.parse(it) }
        return Delta(
            content = content.ifEmpty { JsonText.of(delta, "content") },
            reasoning = reasoning.ifEmpty { JsonText.of(delta, "reasoning_content") },
            usage = usage,
            raw = root,
        )
    }
}
