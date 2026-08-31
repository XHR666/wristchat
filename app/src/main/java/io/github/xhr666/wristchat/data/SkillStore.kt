package io.github.xhr666.wristchat.data

import android.content.Context
import java.io.File

/** 快捷输入存储:纯文本列表(存于 SharedPreferences,经 SettingsStore) */

/** Skills 存储:filesDir/skills/ 下的 .md 文件(SKILL.md 风格) */
data class Skill(
    val fileName: String,
    val name: String,
    val description: String,
    val body: String,
    val enabled: Boolean,
)

class SkillStore(private val context: Context) {

    private val dir: File = File(context.filesDir, "skills").apply { mkdirs() }
    private val stateFile: File = File(context.filesDir, "skills_state.json")

    fun scan(): List<Skill> {
        val enabledSet = loadEnabled()
        return dir.listFiles()?.filter { it.isFile && (it.name.endsWith(".md") || it.name.endsWith(".txt")) }
            ?.map { f ->
                val text = f.readText()
                val (name, desc, body) = parse(text, f.name)
                Skill(f.name, name, desc, body, f.name in enabledSet)
            } ?: emptyList()
    }

    private fun parse(text: String, fallbackName: String): Triple<String, String, String> {
        // front-matter: ---\nname: xxx\ndescription: xxx\n---\nbody
        val m = Regex("^---\\s*\\n([\\s\\S]*?)\\n---\\s*\\n([\\s\\S]*)$").find(text.trimStart())
        if (m != null) {
            val meta = m.groupValues[1]
            val body = m.groupValues[2].trim()
            val name = Regex("name\\s*[:=]\\s*(.+)").find(meta)?.groupValues?.get(1)?.trim()
                ?: fallbackName.removeSuffix(".md").removeSuffix(".txt")
            val desc = Regex("description\\s*[:=]\\s*(.+)").find(meta)?.groupValues?.get(1)?.trim() ?: ""
            return Triple(name, desc, body)
        }
        return Triple(fallbackName.removeSuffix(".md").removeSuffix(".txt"), "", text.trim())
    }

    fun setEnabled(fileName: String, enabled: Boolean) {
        val set = loadEnabled().toMutableSet()
        if (enabled) set.add(fileName) else set.remove(fileName)
        saveEnabled(set)
    }

    fun delete(fileName: String) {
        File(dir, fileName).delete()
        setEnabled(fileName, false)
    }

    fun importFile(displayName: String, content: String): String {
        val safe = displayName.replace(Regex("[^\\w.\\-]"), "_")
        val name = if (safe.endsWith(".md") || safe.endsWith(".txt")) safe else "$safe.md"
        val f = File(dir, name)
        var finalName = name
        var i = 1
        while (f.exists()) { finalName = name.replace(Regex("\\.(md|txt)$"), "_$i.$1"); f.renameTo(File(dir, finalName)); i++ }
        File(dir, finalName).writeText(content)
        return finalName
    }

    private fun loadEnabled(): Set<String> = try {
        org.json.JSONArray(stateFile.readText()).let { arr ->
            (0 until arr.length()).map { arr.optString(it) }.toSet()
        }
    } catch (e: Exception) { emptySet() }

    private fun saveEnabled(set: Set<String>) {
        val arr = org.json.JSONArray()
        set.forEach { arr.put(it) }
        stateFile.writeText(arr.toString())
    }

    /** 启用 skills 的指令拼接(注入系统提示) */
    fun buildSkillsPrompt(): String {
        val enabled = scan().filter { it.enabled }
        if (enabled.isEmpty()) return ""
        val sb = StringBuilder("可用技能指令:\n")
        enabled.forEach { s ->
            sb.append("### 技能:${s.name}\n").append(s.body).append("\n\n")
        }
        return sb.toString().trim()
    }

    fun totalSizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L
}
