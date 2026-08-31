package io.github.xhr666.wristchat.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** 记忆存储:filesDir/memories.json,注入 <memories> 到系统提示 */
data class MemoryItem(
    val id: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)

class MemoryStore(private val context: Context) {

    private val file: File = File(context.filesDir, "memories.json")

    fun list(): List<MemoryItem> = try {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            MemoryItem(o.optString("id"), o.optString("content"), o.optLong("createdAt"), o.optLong("updatedAt"))
        }
    } catch (e: Exception) { emptyList() }

    fun add(content: String): MemoryItem {
        val item = MemoryItem(
            UUID.randomUUID().toString().replace("-", "").take(12),
            content.trim(), System.currentTimeMillis(), System.currentTimeMillis(),
        )
        val all = list().toMutableList()
        // 相似内容合并:同关键词视为更新
        val existing = all.firstOrNull { it.content == item.content }
        if (existing != null) {
            update(existing.id, item.content)
            return existing
        }
        all.add(item)
        write(all)
        return item
    }

    fun update(id: String, content: String) {
        val all = list().map {
            if (it.id == id) it.copy(content = content.trim(), updatedAt = System.currentTimeMillis()) else it
        }
        write(all)
    }

    fun delete(id: String) {
        write(list().filterNot { it.id == id })
    }

    fun clear() {
        file.delete()
    }

    private fun write(items: List<MemoryItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().apply {
            put("id", it.id); put("content", it.content)
            put("createdAt", it.createdAt); put("updatedAt", it.updatedAt)
        }) }
        file.writeText(arr.toString())
    }

    /** 生成 <memories> 注入文本(仿 rikkahub 设计) */
    fun buildMemoriesTag(): String {
        val items = list()
        if (items.isEmpty()) return ""
        val sb = StringBuilder("<memories>\n")
        items.forEachIndexed { i, m ->
            sb.append("${i + 1}. ${m.content}\n")
        }
        sb.append("</memories>")
        return sb.toString()
    }

    fun sizeBytes(): Long = if (file.exists()) file.length() else 0L
}
