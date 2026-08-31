package io.github.xhr666.wristchat.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/** 聊天记录导入:OpenAI JSON/JSONL + chatbox backup v2 + 纯文本 */
object ImportParser {

    sealed class ParseResult {
        data class Success(val title: String, val messages: List<ChatMessage>) : ParseResult()
        data class Error(val message: String) : ParseResult()
    }

    fun parse(file: File, fileName: String): ParseResult {
        return try {
            val text = file.readText(Charsets.UTF_8)
            when {
                fileName.endsWith(".zip") -> parseChatboxZip(file)
                text.trimStart().startsWith("{") || text.trimStart().startsWith("[") -> parseJson(text)
                else -> parsePlainText(text)
            }
        } catch (e: Exception) {
            ParseResult.Error("解析失败:${e.message}")
        }
    }

    /** OpenAI 兼容 JSON:{"messages":[{role,content}]} 或 {"messages":[{role,content}]} 数组,或 JSONL 每行一条消息 */
    private fun parseJson(text: String): ParseResult {
        val trimmed = text.trim()
        val messages = mutableListOf<ChatMessage>()
        var title = "导入会话"
        try {
            if (trimmed.startsWith("[")) {
                // JSON 数组,每项可能是 {"role","content"} 或 {"messages":[...]}
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.has("messages")) {
                        addMessages(o.optJSONArray("messages"), messages)
                    } else {
                        addMessage(o, messages)
                    }
                }
            } else {
                val o = JSONObject(trimmed)
                if (o.has("messages")) {
                    addMessages(o.optJSONArray("messages"), messages)
                    title = o.optString("title", title)
                } else {
                    // JSONL:每行一个 JSON
                    trimmed.lineSequence().forEach { line ->
                        val l = line.trim()
                        if (l.isNotEmpty()) addMessage(JSONObject(l), messages)
                    }
                }
            }
        } catch (e: Exception) {
            // 可能 JSONL 但整体不是 JSON:逐行试
            messages.clear()
            trimmed.lineSequence().forEach { line ->
                val l = line.trim()
                if (l.isEmpty()) return@forEach
                try { addMessage(JSONObject(l), messages) } catch (e2: Exception) {}
            }
        }
        if (messages.isEmpty()) return ParseResult.Error("未解析到有效消息")
        return ParseResult.Success(title, messages)
    }

    /** chatbox backup v2:zip 内 manifest.json + sessions/<id>/session.json */
    private fun parseChatboxZip(file: File): ParseResult {
        val messages = mutableListOf<ChatMessage>()
        var title = "Chatbox 导入"
        var found = false
        ZipFile(file).use { zip ->
            val manifest = zip.getEntry("manifest.json")?.let {
                JSONObject(zip.getInputStream(it).readBytes().toString(Charsets.UTF_8))
            }
            if (manifest != null && manifest.optString("format") == "chatbox-backup") {
                // 遍历 sessions/*/session.json
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    val name = e.name
                    if (name.startsWith("sessions/") && name.endsWith("/session.json")) {
                        found = true
                        val o = JSONObject(zip.getInputStream(e).readBytes().toString(Charsets.UTF_8))
                        val msgs = o.optJSONArray("messages") ?: o.optJSONArray("items")
                        if (msgs != null) addMessages(msgs, messages)
                        if (o.has("title")) title = o.optString("title", title)
                    }
                }
            } else {
                // 非标准 zip:可能直接含 messages.json
                val me = zip.getEntry("messages.json") ?: zip.getEntry("session.json")
                if (me != null) {
                    found = true
                    val o = JSONObject(zip.getInputStream(me).readBytes().toString(Charsets.UTF_8))
                    o.optJSONArray("messages")?.let { addMessages(it, messages) }
                }
            }
        }
        if (!found || messages.isEmpty()) return ParseResult.Error("chatbox 备份格式不识别(需 manifest.json 的 backup v2)")
        return ParseResult.Success(title, messages)
    }

    private fun addMessages(arr: JSONArray, out: MutableList<ChatMessage>) {
        for (i in 0 until arr.length()) addMessage(arr.optJSONObject(i), out)
    }

    private fun addMessage(o: JSONObject?, out: MutableList<ChatMessage>) {
        if (o == null) return
        val role = o.optString("role").takeIf { it in setOf("user", "assistant", "system") } ?: return
        // content 可能是字符串或数组
        val content = when (val c = o.opt("content")) {
            is String -> c
            is JSONArray -> {
                val sb = StringBuilder()
                for (i in 0 until c.length()) {
                    val part = c.optJSONObject(i)
                    if (part != null) sb.append(part.optString("text"))
                }
                sb.toString()
            }
            else -> ""
        }
        if (content.isBlank()) return
        out.add(ChatMessage(role = role, content = content, ts = o.optLong("ts", System.currentTimeMillis())))
    }

    /** 纯文本:user:/assistant: 或 我:/AI: 前缀 */
    private fun parsePlainText(text: String): ParseResult {
        val messages = mutableListOf<ChatMessage>()
        text.lineSequence().forEach { line ->
            val l = line.trim()
            if (l.isEmpty()) return@forEach
            val role = when {
                l.startsWith("user:") || l.startsWith("我:") -> "user" to l.substringAfter(':').trim()
                l.startsWith("assistant:") || l.startsWith("AI:") || l.startsWith("ai:") -> "assistant" to l.substringAfter(':').trim()
                else -> null
            }
            if (role != null && role.second.isNotBlank()) {
                messages.add(ChatMessage(role = role.first, content = role.second))
            }
        }
        if (messages.isEmpty()) return ParseResult.Error("纯文本格式无法识别(需 user:/assistant: 或 我:/AI: 前缀)")
        return ParseResult.Success("文本导入", messages)
    }
}
