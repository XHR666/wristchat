package io.github.xhr666.wristchat.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** 会话持久化:filesDir/sessions/<id>.json,每条消息含 usage/费用/思维链 */
data class ChatMessage(
    val role: String,          // user / assistant / system
    val content: String,
    val img: String? = null,   // 图片附件文件名(filesDir/attachments/),仅 user 消息
    val reasoning: String = "",
    val ts: Long = System.currentTimeMillis(),
    val usage: TokenUsage? = null,
    val cost: Double = 0.0,
    val toolCalls: List<ToolCall> = emptyList(),
)

data class ToolCall(val id: String, val name: String, val arguments: String)

data class TokenUsage(
    val promptTokens: Int = 0,
    val cacheHit: Int = 0,
    val cacheMiss: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val reasoningTokens: Int = 0,
) {
    fun cacheHitRate(): Float =
        if (promptTokens > 0) cacheHit.toFloat() / promptTokens else 0f
}

data class Session(
    val id: String,
    var title: String,
    var createdAt: Long,
    var updatedAt: Long,
    val messages: MutableList<ChatMessage> = mutableListOf(),
    // 累计用量
    var totalTokens: Long = 0,
    var totalCacheHit: Long = 0,
    var totalCacheMiss: Long = 0,
    var totalCompletionTokens: Long = 0,
    var totalCost: Double = 0.0,
    var totalRequests: Int = 0,
    // 压缩标记
    var compressedCount: Int = 0,
)

class SessionStore(private val context: Context) {

    private val dir: File = File(context.filesDir, "sessions").apply { mkdirs() }

    fun list(): List<Session> =
        dir.listFiles()?.filter { it.name.endsWith(".json") }
            ?.mapNotNull { load(it) }
            ?.sortedByDescending { it.updatedAt } ?: emptyList()

    fun load(id: String): Session? = load(File(dir, "$id.json"))

    private fun load(f: File): Session? = try {
        val o = JSONObject(f.readText())
        val msgs = JSONArray(o.optString("messages", "[]").let {
            if (it.startsWith("[")) it else JSONArray()
        })
        Session(
            id = o.getString("id"),
            title = o.optString("title", "新会话"),
            createdAt = o.optLong("createdAt", 0L),
            updatedAt = o.optLong("updatedAt", 0L),
            messages = (0 until msgs.length()).mapNotNull { i ->
                val m = msgs.optJSONObject(i) ?: return@mapNotNull null
                ChatMessage(
                    role = m.optString("role"),
                    content = m.optString("content"),
                    img = m.optString("img").takeIf { it.isNotBlank() },
                    reasoning = m.optString("reasoning"),
                    ts = m.optLong("ts"),
                    usage = m.optJSONObject("usage")?.let { u ->
                        TokenUsage(
                            u.optInt("prompt"), u.optInt("hit"), u.optInt("miss"),
                            u.optInt("completion"), u.optInt("total"), u.optInt("reasoning"),
                        )
                    },
                    cost = m.optDouble("cost"),
                )
            }.toMutableList(),
            totalTokens = o.optLong("totalTokens"),
            totalCacheHit = o.optLong("totalCacheHit"),
            totalCacheMiss = o.optLong("totalCacheMiss"),
            totalCompletionTokens = o.optLong("totalCompletionTokens"),
            totalCost = o.optDouble("totalCost"),
            totalRequests = o.optInt("totalRequests"),
            compressedCount = o.optInt("compressedCount"),
        )
    } catch (e: Exception) { null }

    fun save(s: Session) {
        s.updatedAt = System.currentTimeMillis()
        val msgs = JSONArray()
        s.messages.forEach { m ->
            msgs.put(
                JSONObject().apply {
                    put("role", m.role)
                    put("content", m.content)
                    m.img?.let { put("img", it) }
                    put("reasoning", m.reasoning)
                    put("ts", m.ts)
                    m.usage?.let { u ->
                        put("usage", JSONObject().apply {
                            put("prompt", u.promptTokens); put("hit", u.cacheHit)
                            put("miss", u.cacheMiss); put("completion", u.completionTokens)
                            put("total", u.totalTokens); put("reasoning", u.reasoningTokens)
                        })
                    }
                    put("cost", m.cost)
                }
            )
        }
        val o = JSONObject().apply {
            put("id", s.id)
            put("title", s.title)
            put("createdAt", s.createdAt)
            put("updatedAt", s.updatedAt)
            put("messages", msgs.toString())
            put("totalTokens", s.totalTokens)
            put("totalCacheHit", s.totalCacheHit)
            put("totalCacheMiss", s.totalCacheMiss)
            put("totalCompletionTokens", s.totalCompletionTokens)
            put("totalCost", s.totalCost)
            put("totalRequests", s.totalRequests)
            put("compressedCount", s.compressedCount)
        }
        // 原子写:先写 .tmp 再 rename,防崩溃损坏
        val target = File(dir, "${s.id}.json")
        val tmp = File(dir, "${s.id}.json.tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(target)) { target.writeText(o.toString()); tmp.delete() }
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
        Attachments.deleteForSession(context, id)
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
        Attachments.clearAll(context)
    }

    fun totalSizeBytes(): Long =
        dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun create(title: String = "新会话"): Session {
        val s = Session(id = UUID.randomUUID().toString().replace("-", "").take(16), title = title, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
        save(s)
        return s
    }

    /** 追加消息并累计用量 */
    fun appendMessage(s: Session, m: ChatMessage) {
        s.messages.add(m)
        m.usage?.let { u ->
            s.totalTokens += u.totalTokens
            s.totalCacheHit += u.cacheHit
            s.totalCacheMiss += u.cacheMiss
            s.totalCompletionTokens += u.completionTokens
            s.totalRequests += 1
        }
        s.totalCost += m.cost
        save(s)
    }

    fun rename(s: Session, title: String) {
        s.title = title
        save(s)
    }

    /** 仅累计用量/费用(自动标题等不产生消息的请求),计入会话统计 */
    fun addUsageOnly(s: Session, usage: TokenUsage?, cost: Double) {
        usage?.let { u ->
            s.totalTokens += u.totalTokens
            s.totalCacheHit += u.cacheHit
            s.totalCacheMiss += u.cacheMiss
            s.totalCompletionTokens += u.completionTokens
            s.totalRequests += 1
        }
        s.totalCost += cost
        save(s)
    }
}
