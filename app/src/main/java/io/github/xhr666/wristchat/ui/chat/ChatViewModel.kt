package io.github.xhr666.wristchat.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.github.xhr666.wristchat.WristChatApp
import io.github.xhr666.wristchat.data.ChatMessage
import io.github.xhr666.wristchat.data.ChatRepository
import io.github.xhr666.wristchat.data.ChatResult
import io.github.xhr666.wristchat.data.MemoryStore
import io.github.xhr666.wristchat.data.Session
import io.github.xhr666.wristchat.data.SessionStore
import io.github.xhr666.wristchat.data.SettingsStore
import io.github.xhr666.wristchat.data.SkillStore
import io.github.xhr666.wristchat.data.TokenUsage
import kotlinx.coroutines.launch

data class ConvDetails(
    val totalTokens: Long,
    val cacheHitRate: Float,
    val contextTokens: Int,
    val contextWindow: Int,
    val messageCount: Int,
    val totalCost: Double,
    val requests: Int,
    val compressed: Int,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    val settings: SettingsStore = (app as WristChatApp).settings
    private val sessionStore = SessionStore(app)
    private val memoryStore = MemoryStore(app)
    private val skillStore = SkillStore(app)
    private val repo = ChatRepository(settings, memoryStore, app)

    private val _session = MutableLiveData<Session>()
    val session: LiveData<Session> = _session

    private val _draft = MutableLiveData("")
    val draft: LiveData<String> = _draft

    private val _sending = MutableLiveData(false)
    val sending: LiveData<Boolean> = _sending

    private val _status = MutableLiveData<String?>(null)
    val status: LiveData<String?> = _status

    private val _quickInputs = MutableLiveData<List<String>>(emptyList())
    val quickInputs: LiveData<List<String>> = _quickInputs

    private val _quickPanelVisible = MutableLiveData(false)
    val quickPanelVisible: LiveData<Boolean> = _quickPanelVisible

    private val _details = MutableLiveData<ConvDetails?>(null)
    val details: LiveData<ConvDetails?> = _details

    private val _title = MutableLiveData("")
    val title: LiveData<String> = _title

    private val _sessions = MutableLiveData<List<SessionStore.SessionBrief>>(emptyList())
    val sessions: LiveData<List<SessionStore.SessionBrief>> = _sessions

    init {
        _quickInputs.value = settings.getQuickInputs()
        SkillStoreProviderInject.refresh(skillStore)
        // 冷启动只解析最近一个会话文件(其余在进入负一屏/设置页时异步加载),避免首帧卡顿
        loadOrCreateSession()
        refreshSessions()
    }

    fun loadOrCreateSession() {
        val s = sessionStore.latest() ?: sessionStore.create()
        _session.value = s
        _title.value = s.title
        refreshDetails()
    }

    /** 全量会话列表(负一屏/删除后刷新):解析放 IO 线程,避免主线程卡顿 */
    fun refreshSessions() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _sessions.postValue(sessionStore.briefs())
        }
    }

    /** 负一屏选择会话(草稿保留):单文件解析,切换后会话完整加载 */
    fun setCurrentSession(id: String) {
        val s = sessionStore.load(id) ?: return
        _session.value = s
        _title.value = s.title
        refreshDetails()
    }

    /** 负一屏删除会话 */
    fun deleteSession(id: String) {
        sessionStore.delete(id)
        refreshSessions()
        val cur = _session.value
        if (cur != null && cur.id == id) loadOrCreateSession()
    }

    /** 手动重命名会话 */
    fun renameSession(id: String, title: String) {
        val s = sessionStore.load(id) ?: return
        sessionStore.rename(s, title)
        refreshSessions()
        if (_session.value?.id == id) _title.value = title
    }

    fun setDraft(text: String) { _draft.value = text }

    /** 图片上传辅助 */
    fun currentSessionId(): String? = _session.value?.id
    fun providerIsDeepSeek(): Boolean = settings.providerId == SettingsStore.PROVIDER_DEEPSEEK
    fun modelSupportsVision(): Boolean =
        io.github.xhr666.wristchat.data.ChatRepository.supportsVision(settings.model)
    fun switchToVisionModel() {
        settings.model = io.github.xhr666.wristchat.data.ChatRepository.VISION_MODEL
    }

    fun clearDraft() { _draft.value = "" }

    fun toggleQuickPanel() {
        _quickInputs.value = settings.getQuickInputs()
        _quickPanelVisible.value = !(_quickPanelVisible.value ?: false)
    }

    fun useQuickInput(text: String) {
        _draft.value = text
        _quickPanelVisible.value = false
    }

    fun refreshQuickInputs() { _quickInputs.value = settings.getQuickInputs() }

    fun newSession() {
        val s = sessionStore.create()
        _session.value = s
        _title.value = s.title
        clearDraft()
        refreshSessions()
        refreshDetails()
    }

    fun clearMessages() {
        _session.value?.let { s ->
            s.messages.clear()
            s.totalTokens = 0; s.totalCacheHit = 0; s.totalCacheMiss = 0
            s.totalCompletionTokens = 0; s.totalCost = 0.0; s.totalRequests = 0
            s.compressedCount = 0
            io.github.xhr666.wristchat.data.Attachments.deleteForSession(getApplication(), s.id)
            sessionStore.save(s)
            _session.value = s
            refreshSessions()
            refreshDetails()
        }
    }

    fun refreshDetails() {
        val s = _session.value ?: return
        _details.value = detailsOf(s)
    }

    private fun detailsOf(s: Session): ConvDetails {
        val lastUsage = s.messages.lastOrNull { it.usage != null }?.usage
        return ConvDetails(
            totalTokens = s.totalTokens,
            cacheHitRate = if (s.totalTokens > 0) s.totalCacheHit.toFloat() / (s.totalCacheHit + s.totalCacheMiss).coerceAtLeast(1) else 0f,
            contextTokens = lastUsage?.promptTokens ?: 0,
            contextWindow = settings.contextWindow,
            messageCount = s.messages.size,
            totalCost = s.totalCost,
            requests = s.totalRequests,
            compressed = s.compressedCount,
        )
    }

    /** 处理斜杠命令,返回 true 表示已消费(不发送给模型) */
    private fun handleSlash(text: String): Boolean {
        if (!text.startsWith("/")) return false
        val parts = text.split(Regex("\\s+"), limit = 2)
        return when (parts[0]) {
            "/new" -> { newSession(); pushLocal("已新建会话"); true }
            "/clear" -> { clearMessages(); pushLocal("已清空当前会话"); true }
            "/remember" -> {
                val content = parts.getOrNull(1)?.trim()
                if (content.isNullOrBlank()) pushLocal("用法:/remember 内容") else { memoryStore.add(content); pushLocal("已记住") }
                true
            }
            "/memories" -> {
                val list = memoryStore.list()
                pushLocal(if (list.isEmpty()) "暂无记忆" else list.joinToString("\n") { "${it.id}: ${it.content.take(60)}" })
                true
            }
            "/forget" -> {
                val id = parts.getOrNull(1)?.trim()
                if (id.isNullOrBlank()) pushLocal("用法:/forget <id>") else { memoryStore.delete(id); pushLocal("已删除 $id") }
                true
            }
            "/skills" -> {
                val list = skillStore.scan()
                pushLocal(if (list.isEmpty()) "暂无技能" else list.joinToString("\n") { "${if (it.enabled) "✓" else "○"} ${it.name}" })
                true
            }
            "/on", "/off" -> {
                val name = parts.getOrNull(1)?.trim()
                if (name.isNullOrBlank()) { pushLocal("用法:${parts[0]} <技能名>"); return true }
                val match = skillStore.scan().firstOrNull { it.name.contains(name, ignoreCase = true) || it.fileName.contains(name, ignoreCase = true) }
                if (match == null) pushLocal("未找到技能:$name") else {
                    skillStore.setEnabled(match.fileName, parts[0] == "/on")
                    SkillStoreProviderInject.refresh(skillStore)
                    pushLocal("${if (parts[0] == "/on") "已启用" else "已禁用"}:${match.name}")
                }
                true
            }
            else -> false
        }
    }

    private fun pushLocal(text: String) {
        val s = _session.value ?: return
        s.messages.add(ChatMessage(role = "assistant", content = text))
        sessionStore.save(s)
        _session.value = s
    }

    fun send(text: String, imageFile: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && imageFile == null) return
        if (_sending.value == true) return
        if (trimmed.isNotEmpty() && handleSlash(trimmed)) { clearDraft(); return }

        val s = _session.value ?: return
        val ts = System.currentTimeMillis()
        val userMsg = ChatMessage(role = "user", content = trimmed, img = imageFile, ts = ts)
        // 先落库(即使请求失败,用户消息也保留)
        s.messages.add(userMsg)
        sessionStore.save(s)
        _session.value = s
        if (trimmed.isEmpty()) _draft.value = ""
        else clearDraft()
        _sending.value = true

        viewModelScope.launch {
            // 上下文压缩检查
            maybeCompress(s)
            // 历史不含刚追加的这条用户消息(仓库会把它作为本轮提问带上去,避免重复)
            val history = s.messages.filter { it.role != "system" && it !== userMsg }
            val result = repo.chat(history, trimmed, imageFile = imageFile,
                enableMemory = settings.memoryAuto && settings.providerId == SettingsStore.PROVIDER_DEEPSEEK)
            when (result) {
                is ChatResult.Success -> {
                    val ai = ChatMessage(
                        role = "assistant",
                        content = result.content,
                        reasoning = result.reasoning,
                        ts = System.currentTimeMillis(),
                        usage = result.usage,
                        cost = result.cost,
                    )
                    s.messages.add(ai)
                    sessionStore.save(s)
                    _session.value = s
                    _status.value = if (result.cost > 0) "消耗 ¥%.4f".format(result.cost) else null
                    if (result.memoryOps.isNotEmpty()) {
                        _status.value = "已更新 ${result.memoryOps.size} 条记忆"
                    }
                    refreshSessions()
                    maybeAutoTitle(s, trimmed, result.content)
                }
                is ChatResult.Error -> {
                    _status.value = result.message
                    // 错误信息也入会话(便于查看)
                    s.messages.add(ChatMessage(role = "assistant", content = "⚠️ ${result.message}"))
                    sessionStore.save(s)
                    _session.value = s
                }
            }
            _sending.value = false
            refreshDetails()
        }
    }

    /** 估算 token:1 中文字≈0.6,1 英文≈0.3(官方 token_usage 文档) */
    private fun estimateTokens(text: String): Int {
        var chinese = 0; var other = 0
        text.forEach { c -> if (c.code in 0x4E00..0x9FFF) chinese++ else other++ }
        return (chinese * 0.6 + other * 0.3).toInt()
    }

    /** 新会话首轮回复后自动生成标题(默认开,设置可关;消耗计入会话) */
    private fun maybeAutoTitle(s: Session, userText: String, reply: String) {
        if (!settings.autoTitle) return
        if (s.title != "新会话") return
        if (s.messages.count { it.role == "user" } > 1) return
        viewModelScope.launch {
            val (title, usage) = repo.genTitle(userText, reply)
            if (title.isNotBlank()) {
                sessionStore.rename(s, title)
                _title.value = title
                refreshSessions()
            }
            usage?.let { u ->
                val cost = io.github.xhr666.wristchat.data.Pricing.cost(
                    settings.model, System.currentTimeMillis() / 1000,
                    u.cacheHit.toLong(), u.cacheMiss.toLong(), u.completionTokens.toLong()
                )
                sessionStore.addUsageOnly(s, u, cost)
                refreshDetails()
            }
        }
    }

    private suspend fun maybeCompress(s: Session) {
        val lastUsage = s.messages.lastOrNull { it.usage != null }?.usage
        val projected = (lastUsage?.promptTokens ?: 0) + estimateTokens(s.messages.lastOrNull()?.content ?: "")
        val threshold = settings.contextWindow * settings.compressThreshold / 100
        if (projected <= threshold) return
        // 触发压缩:取最旧消息压缩(保留最近 10 条)
        val all = s.messages.filter { it.role != "system" }
        if (all.size < 12) return
        val keep = 10
        val toCompress = all.subList(0, all.size - keep)
        if (toCompress.isEmpty()) return
        val model = settings.model
        val key = settings.apiKey
        val provider = io.github.xhr666.wristchat.data.Providers.resolve(settings)
        if (key.isBlank()) return
        val summary = repo.compress(toCompress, model, key, provider.defaultBaseUrl, provider.defaultPath)
        if (summary.isBlank()) return // 压缩失败不阻塞
        // 替换:system 摘要 + 保留最近消息
        val kept = all.subList(all.size - keep, all.size).toMutableList()
        s.messages.clear()
        s.messages.add(ChatMessage(role = "system", content = "对话摘要(自动压缩):\n$summary", ts = System.currentTimeMillis()))
        s.messages.addAll(kept)
        s.compressedCount += toCompress.size
        sessionStore.save(s)
        _status.value = "📌 已自动压缩 ${toCompress.size} 条消息"
    }

    fun destroy() {}
}

/** 供 ViewModel 使用(避免循环依赖) */
object SkillStoreProviderInject {
    fun refresh(store: SkillStore) {
        io.github.xhr666.wristchat.data.SkillStoreProvider.skillsPrompt = store.buildSkillsPrompt()
    }
}
