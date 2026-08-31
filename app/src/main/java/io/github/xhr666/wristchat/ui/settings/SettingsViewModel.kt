package io.github.xhr666.wristchat.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.github.xhr666.wristchat.WristChatApp
import io.github.xhr666.wristchat.data.MemoryStore
import io.github.xhr666.wristchat.data.SessionStore
import io.github.xhr666.wristchat.data.SettingsStore
import io.github.xhr666.wristchat.data.SkillStore
import io.github.xhr666.wristchat.data.SkillStoreProvider
import io.github.xhr666.wristchat.ui.chat.SkillStoreProviderInject
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    val settings: SettingsStore = (app as WristChatApp).settings
    val sessionStore = SessionStore(app)
    val memoryStore = MemoryStore(app)
    val skillStore = SkillStore(app)

    private val _sessionWarn = MutableLiveData(false)
    val sessionWarn: LiveData<Boolean> = _sessionWarn

    private val _cacheInfo = MutableLiveData("")
    val cacheInfo: LiveData<String> = _cacheInfo

    private val _sessionsSize = MutableLiveData("")
    val sessionsSize: LiveData<String> = _sessionsSize

    init {
        refreshSkillPrompt()
        refreshSizes()
    }

    fun refreshSkillPrompt() {
        SkillStoreProviderInject.refresh(skillStore)
        refreshSizes()
    }

    fun refreshSizes() {
        val sessions = sessionStore.list()
        _sessionWarn.value = sessions.size >= MAX_SESSIONS || sessions.any { it.messages.size >= MAX_MESSAGES }
        val sesBytes = sessionStore.totalSizeBytes()
        val memBytes = memoryStore.sizeBytes()
        val skillBytes = skillStore.totalSizeBytes()
        val webCache = webViewCacheSize()
        _sessionsSize.value = human(sesBytes)
        _cacheInfo.value = "WebView 缓存:${human(webCache)} · 会话:${human(sesBytes)} · 记忆:${human(memBytes)} · 技能:${human(skillBytes)}"
    }

    private fun webViewCacheSize(): Long {
        val app = getApplication<Application>()
        val dirs = listOf(
            File(app.cacheDir, "WebView"),
            File(app.cacheDir, "http_cache"),
        )
        return dirs.sumOf { d -> if (d.exists()) d.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L }
    }

    fun clearCache(): Long {
        val app = getApplication<Application>()
        var freed = 0L
        listOf(File(app.cacheDir, "WebView"), File(app.cacheDir, "http_cache"), File(app.cacheDir, "katex_tmp"))
            .forEach { d -> if (d.exists()) { freed += d.walkTopDown().filter { it.isFile }.sumOf { it.length() }; d.deleteRecursively() } }
        refreshSizes()
        return freed
    }

    fun clearAllData(): Long {
        val app = getApplication<Application>()
        var freed = 0L
        freed += sessionStore.totalSizeBytes()
        sessionStore.clear()
        memoryStore.clear()
        skillStore.scan().forEach { skillStore.delete(it.fileName) }
        settings.setQuickInputs(emptyList())
        refreshSizes()
        return freed
    }

    fun sessionsSummary(): String {
        val sessions = sessionStore.list()
        return sessions.joinToString("\n") { s ->
            "${s.title.take(14)} · ${s.messages.size}条 · ¥%.3f".format(s.totalCost)
        }
    }

    fun memoriesSummary(): String {
        val m = memoryStore.list()
        return if (m.isEmpty()) "(空)" else m.joinToString("\n") { "${it.id}: ${it.content.take(40)}" }
    }

    fun skillsSummary(): String {
        val s = skillStore.scan()
        return if (s.isEmpty()) "(空)" else s.joinToString("\n") { "${if (it.enabled) "✓" else "○"} ${it.name}" }
    }

    fun importSkill(fileName: String, content: String): String {
        val name = skillStore.importFile(fileName, content)
        refreshSkillPrompt()
        return name
    }

    fun setSkillEnabled(fileName: String, enabled: Boolean) {
        skillStore.setEnabled(fileName, enabled)
        refreshSkillPrompt()
    }

    fun deleteSkill(fileName: String) {
        skillStore.delete(fileName)
        refreshSkillPrompt()
    }

    fun deleteMemory(id: String) {
        memoryStore.delete(id)
        refreshSizes()
    }

    fun clearMemories() {
        memoryStore.clear()
        refreshSizes()
    }

    fun deleteSession(id: String) {
        sessionStore.delete(id)
        refreshSizes()
    }

    fun importSession(messages: List<io.github.xhr666.wristchat.data.ChatMessage>, title: String): Boolean {
        if (messages.isEmpty()) return false
        val s = sessionStore.create(title.ifBlank { "导入会话" })
        messages.take(MAX_MESSAGES).forEach { m ->
            sessionStore.appendMessage(s, m)
        }
        refreshSizes()
        return true
    }

    fun human(bytes: Long): String = when {
        bytes > 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        bytes > 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    companion object {
        const val MAX_SESSIONS = 20
        const val MAX_MESSAGES = 100
    }
}
