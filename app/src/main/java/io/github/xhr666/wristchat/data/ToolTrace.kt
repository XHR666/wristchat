package io.github.xhr666.wristchat.data

import org.json.JSONArray
import org.json.JSONObject

/** 把官方 server_tool_event / server_tool_calls 收成能显示的短句。不含密钥，不含用户原文。 */
object ToolTrace {
    fun line(action: String, queries: List<String>, url: String, done: Boolean): String {
        val verb = when (action) {
            "search" -> if (done) "已搜索" else "正在搜索"
            "open_page" -> if (done) "已打开" else "正在打开"
            "find_in_page" -> if (done) "已查找" else "正在查找"
            "read_link" -> if (done) "已阅读" else "正在阅读"
            else -> return ""
        }
        val extra = when (action) {
            "search" -> queries.filter { it.isNotBlank() }.joinToString("、")
            "open_page", "read_link" -> url.trim()
            else -> ""
        }
        return if (extra.isBlank()) verb else "$verb $extra"
    }

    fun fromEvent(root: JSONObject?): String {
        val choice = root?.optJSONArray("choices")?.optJSONObject(0) ?: return ""
        val ev = choice.optJSONObject("server_tool_event")
            ?: root.optJSONObject("server_tool_event")
            ?: return ""
        if (ev.optString("type") == "step_reasoning_done") return ""
        val call = ev.optJSONObject("call") ?: return ""
        val action = call.optJSONObject("action") ?: return ""
        val done = ev.optString("type") == "call_completed" || call.optString("status") == "completed"
        return line(action.optString("type"), strings(action.optJSONArray("queries")), action.optString("url"), done)
    }

    fun fromCalls(raw: String): String {
        if (raw.isBlank()) return ""
        val arr = try { JSONArray(raw) } catch (_: Exception) { return "" }
        val lines = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            val calls = arr.optJSONObject(i)?.optJSONArray("calls") ?: continue
            for (j in 0 until calls.length()) {
                val action = calls.optJSONObject(j)?.optJSONObject("action") ?: continue
                val text = line(action.optString("type"), strings(action.optJSONArray("queries")), action.optString("url"), true)
                if (text.isNotEmpty()) lines.add(text)
            }
        }
        return lines.take(8).joinToString("\n")
    }

    private fun strings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }
    }
}
