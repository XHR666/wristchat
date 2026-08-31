package io.github.xhr666.wristchat.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.xhr666.wristchat.AppLockActivity
import io.github.xhr666.wristchat.BuildConfig
import io.github.xhr666.wristchat.MainActivity
import io.github.xhr666.wristchat.R
import io.github.xhr666.wristchat.data.ImportParser
import io.github.xhr666.wristchat.data.Providers
import io.github.xhr666.wristchat.data.ReleaseInfo
import io.github.xhr666.wristchat.data.SettingsStore
import io.github.xhr666.wristchat.data.SyncServer
import io.github.xhr666.wristchat.data.UpdateRepository
import io.github.xhr666.wristchat.databinding.FragmentSettingsBinding
import io.github.xhr666.wristchat.ui.common.RoundInsets
import io.github.xhr666.wristchat.ui.common.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val vm: SettingsViewModel by viewModels { SettingsViewModelFactory(requireActivity().application) }
    private lateinit var adapter: SettingsAdapter
    private var syncServer: SyncServer? = null
    private val syncScope = CoroutineScope(Dispatchers.IO)
    private val lastSyncActive = AtomicBoolean(false)

    private val importSkillLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        requireContext().contentResolver.openInputStream(uri)?.use { input ->
            val name = queryName(uri) ?: "skill.md"
            val content = input.readBytes().toString(Charsets.UTF_8)
            val saved = vm.importSkill(name, content)
            Toast.makeText(requireContext(), "已导入技能:$saved", Toast.LENGTH_SHORT).show()
            rebuildRows()
        }
    }

    private val importChatLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        // 大小限制,防 OOM
        val size = try {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (idx >= 0 && c.moveToFirst()) c.getLong(idx) else -1L
            } ?: -1L
        } catch (e: Exception) { -1L }
        if (size > 50L * 1024 * 1024) {
            Toast.makeText(requireContext(), "文件过大(>50MB),无法导入", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        syncScope.launch {
            val name = queryName(uri) ?: "chat.json"
            val content = withContext(Dispatchers.IO) {
                requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } ?: return@launch
            val tmp = File(requireContext().cacheDir, "import_$name")
            tmp.writeBytes(content)
            val result = ImportParser.parse(tmp, name)
            tmp.delete()
            withContext(Dispatchers.Main) {
                when (result) {
                    is ImportParser.ParseResult.Success -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle("导入确认")
                            .setMessage("将导入 ${result.messages.size} 条消息,标题「${result.title}」")
                            .setPositiveButton("导入") { _, _ ->
                                if (vm.importSession(result.messages, result.title)) {
                                    Toast.makeText(requireContext(), "导入成功", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(requireContext(), "导入失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                    is ImportParser.ParseResult.Error -> {
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 圆屏安全区:顶部栏动态收窄
        binding.root.post {
            val inset = RoundInsets.horizontalInsetPx(binding.root, binding.topBar.top.toFloat() + binding.topBar.height / 2f)
            val minInset = (6 * resources.displayMetrics.density).toInt()
            binding.topBar.setPadding(inset.coerceAtLeast(minInset), binding.topBar.paddingTop, inset.coerceAtLeast(minInset), binding.topBar.paddingBottom)
        }
        adapter = SettingsAdapter(emptyList())
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.btnCheckUpdate.setOnClickListener { checkUpdate(manual = true) }

        vm.sessionWarn.observe(viewLifecycleOwner) { warn ->
            binding.tvSessionWarn.visibility = if (warn) View.VISIBLE else View.GONE
        }
        vm.cacheInfo.observe(viewLifecycleOwner) { rebuildRows() }
        vm.sessionsSize.observe(viewLifecycleOwner) { rebuildRows() }
        rebuildRows()

        // 进设置页自动静默检查(冷却内不重复)
        autoCheckUpdate()
    }

    override fun onResume() {
        super.onResume()
        rebuildRows()
        stopSyncIfIdle()
    }

    override fun onPause() {
        super.onPause()
        stopSync()
    }

    private fun rebuildRows() {
        val s = vm.settings
        val rows = mutableListOf<SettingRow>()

        rows += SettingRow(SettingRow.SECTION, "服务")
        rows += SettingRow(SettingRow.VALUE, "服务商", Providers.byId(s.providerId).name) {
            val names = Providers.all().map { it.name } + "自定义"
            val ids = Providers.all().map { it.id } + SettingsStore.PROVIDER_CUSTOM
            singleChoice("服务商", names, ids.indexOf(s.providerId).coerceAtLeast(0)) { idx ->
                s.providerId = ids[idx]
                if (ids[idx] != SettingsStore.PROVIDER_CUSTOM && s.baseUrl.isBlank()) {
                    s.baseUrl = Providers.byId(ids[idx]).defaultBaseUrl
                    s.apiPath = Providers.byId(ids[idx]).defaultPath
                }
                rebuildRows()
            }
        }
        rows += SettingRow(SettingRow.VALUE, "API Key", if (s.apiKey.isBlank()) "(未设置)" else "已设置 · ${s.apiKey.takeLast(4)}") { editText("API Key", s.apiKey, true) { s.apiKey = it; rebuildRows() } }
        rows += SettingRow(SettingRow.VALUE, "平台 Token(可选)", if (s.platformToken.isBlank()) "(未设置)" else "已设置") { editText("平台 Token(仅余额页今日用量)", s.platformToken, true) { s.platformToken = it; rebuildRows() } }
        rows += SettingRow(SettingRow.VALUE, "API Base URL", s.baseUrl.ifBlank { Providers.byId(s.providerId).defaultBaseUrl }) { editText("API Base URL", s.baseUrl) { s.baseUrl = it } }
        rows += SettingRow(SettingRow.VALUE, "API 路径", s.apiPath.ifBlank { "/chat/completions" }) { editText("API 路径", s.apiPath) { s.apiPath = it } }

        rows += SettingRow(SettingRow.SECTION, "模型与参数")
        val modelLocked = s.apiKey.isBlank()
        rows += SettingRow(
            if (modelLocked) SettingRow.ACTION else SettingRow.VALUE,
            "模型" + if (modelLocked) "(需先填 Key)" else "",
            s.model,
        ) {
            if (modelLocked) Toast.makeText(requireContext(), "请先填写 API Key", Toast.LENGTH_SHORT).show()
            else {
                val p = Providers.resolve(s)
                val models = if (p.models.isEmpty()) listOf(s.model) else p.models
                singleChoice("模型", models, models.indexOf(s.model).coerceAtLeast(0)) { idx ->
                    s.model = models[idx]
                    rebuildRows()
                }
            }
        }
        rows += SettingRow(SettingRow.TOGGLE, "思考模式(深度思考)", checked = s.thinkingEnabled) { v -> s.thinkingEnabled = v }
        rows += SettingRow(SettingRow.VALUE, "思考强度", s.reasoningEffort) {
            singleChoice("思考强度", listOf("low", "high", "max"), listOf("low", "high", "max").indexOf(s.reasoningEffort).coerceAtLeast(0)) { idx ->
                s.reasoningEffort = listOf("low", "high", "max")[idx]
                rebuildRows()
            }
        }
        rows += SettingRow(SettingRow.VALUE, "温度", s.temperature.toString()) { editNumber("温度(0-2)", s.temperature, 0f, 2f) { s.temperature = it; rebuildRows() } }
        rows += SettingRow(SettingRow.VALUE, "Top P", s.topP.toString()) { editNumber("Top P(0-1)", s.topP, 0f, 1f) { s.topP = it; rebuildRows() } }
        rows += SettingRow(SettingRow.VALUE, "最大输出 tokens", s.maxTokens.toString()) { editNumber("最大输出 tokens", s.maxTokens.toFloat(), 256f, 65536f) { s.maxTokens = it.toInt(); rebuildRows() } }
        rows += SettingRow(SettingRow.VALUE, "上下文窗口", s.contextWindow.toString()) { editNumber("上下文窗口(默认1000000)", s.contextWindow.toFloat(), 10000f, 2000000f) { s.contextWindow = it.toInt(); rebuildRows() } }
        rows += SettingRow(SettingRow.VALUE, "压缩阈值 %", "${s.compressThreshold}%") { editNumber("压缩阈值(50-95)", s.compressThreshold.toFloat(), 50f, 95f) { s.compressThreshold = it.toInt(); rebuildRows() } }
        rows += SettingRow(SettingRow.VALUE, "自定义系统 Prompt", if (s.customPrompt.isBlank()) "(空)" else "已设置") { editMultiline("自定义系统 Prompt", s.customPrompt) { s.customPrompt = it; rebuildRows() } }

        rows += SettingRow(SettingRow.SECTION, "快捷输入")
        rows += SettingRow(SettingRow.ACTION, "管理快捷输入", "${vm.settings.getQuickInputs().size} 条") { manageQuickInputs() }

        rows += SettingRow(SettingRow.SECTION, "技能 Skills")
        rows += SettingRow(SettingRow.ACTION, "导入技能文件", "") { importSkillLauncher.launch(arrayOf("*/*")) }
        rows += SettingRow(SettingRow.ACTION, "刷新扫描", vm.skillsSummary()) {
            vm.refreshSkillPrompt()
            Toast.makeText(requireContext(), "已扫描 ${vm.skillStore.scan().size} 个技能", Toast.LENGTH_SHORT).show()
        }
        rows += SettingRow(SettingRow.ACTION, "技能列表管理", vm.skillsSummary()) { manageSkills() }

        rows += SettingRow(SettingRow.SECTION, "记忆")
        rows += SettingRow(SettingRow.TOGGLE, "自动记忆(模型写入)", checked = s.memoryAuto) { v -> s.memoryAuto = v }
        rows += SettingRow(SettingRow.ACTION, "查看/管理记忆", vm.memoriesSummary()) { manageMemories() }

        rows += SettingRow(SettingRow.SECTION, "会话")
        rows += SettingRow(SettingRow.ACTION, "会话列表(查看/删除)", "${vm.sessionStore.list().size} 个 · 占用 ${vm.sessionsSize.value}") { manageSessions() }
        rows += SettingRow(SettingRow.ACTION, "导入聊天记录", "") { importChatLauncher.launch(arrayOf("*/*")) }

        rows += SettingRow(SettingRow.SECTION, "存储与缓存")
        rows += SettingRow(SettingRow.ACTION, "缓存详情", vm.cacheInfo.value ?: "") { vm.refreshSizes() }
        rows += SettingRow(SettingRow.ACTION, "清理缓存(安全)", "WebView/临时文件") {
            confirm("清理缓存", "将删除 WebView 缓存和临时文件,不影响会话/记忆/设置") {
                val freed = vm.clearCache()
                Toast.makeText(requireContext(), "已释放 ${vm.human(freed)}", Toast.LENGTH_SHORT).show()
            }
        }
        rows += SettingRow(SettingRow.ACTION, "清理全部数据", "会话/记忆/技能/设置") {
            confirm("清理全部数据", "将删除所有会话、记忆、技能和设置(不可恢复)") {
                val freed = vm.clearAllData()
                Toast.makeText(requireContext(), "已释放 ${vm.human(freed)}", Toast.LENGTH_SHORT).show()
            }
        }

        rows += SettingRow(SettingRow.SECTION, "更新")
        rows += SettingRow(SettingRow.ACTION, "检查更新", "") { checkUpdate(manual = true) }
        rows += SettingRow(SettingRow.VALUE, "仓库", "${s.repoOwner}/${s.repoName}") { editText("仓库(owner/repo)", "${s.repoOwner}/${s.repoName}") { input ->
            val parts = input.split("/")
            if (parts.size == 2) { s.repoOwner = parts[0].trim(); s.repoName = parts[1].trim() }
        } }
        rows += SettingRow(SettingRow.VALUE, "自动检查冷却(分钟)", s.updateCooldownMin.toString()) { editNumber("冷却分钟(5-60)", s.updateCooldownMin.toFloat(), 5f, 60f) { s.updateCooldownMin = it.toInt(); rebuildRows() } }

        rows += SettingRow(SettingRow.SECTION, "手机同步")
        rows += SettingRow(SettingRow.ACTION, "开启手机同步", "") { startSync() }

        rows += SettingRow(SettingRow.SECTION, "安全与外观")
        rows += SettingRow(SettingRow.TOGGLE, "启动密码", checked = s.passwordEnabled) { v ->
            if (v) enablePassword() else disablePassword()
        }
        rows += SettingRow(SettingRow.VALUE, "免密次数", s.graceDefault.toString()) { editNumber("未来 N 次启动免密(0=每次都输)", s.graceDefault.toFloat(), 0f, 50f) { s.graceDefault = it.toInt(); rebuildRows() } }
        rows += SettingRow(SettingRow.VALUE, "主题", themeName(s.theme)) {
            singleChoice("主题", listOf("亮色", "暗色", "AMOLED 纯黑"), listOf(ThemeManager.LIGHT, ThemeManager.DARK, ThemeManager.AMOLED).indexOf(s.theme).coerceAtLeast(0)) { idx ->
                s.theme = listOf(ThemeManager.LIGHT, ThemeManager.DARK, ThemeManager.AMOLED)[idx]
                requireActivity().recreate()
            }
        }

        rows += SettingRow(SettingRow.SECTION, "关于")
        rows += SettingRow(SettingRow.VALUE, "版本", "${vm.settings.versionName}(${BuildConfig.VERSION_CODE})")
        rows += SettingRow(SettingRow.ACTION, "开源仓库", "github.com/XHR666/wristchat") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${s.repoOwner}/${s.repoName}")))
        }

        adapter.submit(rows)
    }

    // ---------- 对话框工具 ----------
    private fun themeName(t: String) = when (t) {
        ThemeManager.LIGHT -> "亮色"
        ThemeManager.DARK -> "暗色"
        else -> "AMOLED 纯黑"
    }

    private fun singleChoice(title: String, items: List<String>, checked: Int, onPick: (Int) -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(items.toTypedArray(), checked) { d, which -> onPick(which); d.dismiss() }
            .show()
    }

    private fun editText(title: String, initial: String, password: Boolean = false, onSave: (String) -> Unit) {
        val et = EditText(requireContext()).apply { setText(initial); setSelection(initial.length) }
        if (password) {
            et.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(et)
            .setPositiveButton("保存") { _, _ -> onSave(et.text.toString()) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun editNumber(title: String, initial: Float, min: Float, max: Float, onSave: (Float) -> Unit) {
        val et = EditText(requireContext()).apply {
            setText(if (initial % 1f == 0f) initial.toInt().toString() else initial.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(et)
            .setPositiveButton("保存") { _, _ ->
                val v = et.text.toString().toFloatOrNull()
                if (v == null || v < min || v > max) Toast.makeText(requireContext(), "无效输入", Toast.LENGTH_SHORT).show()
                else onSave(v)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun editMultiline(title: String, initial: String, onSave: (String) -> Unit) {
        val et = EditText(requireContext()).apply {
            setText(initial)
            gravity = android.view.Gravity.TOP
            minLines = 4
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(et)
            .setPositiveButton("保存") { _, _ -> onSave(et.text.toString()) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirm(title: String, message: String, onOk: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定") { _, _ -> onOk() }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------- 密码 ----------
    private fun enablePassword() {
        val et = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "4 位数字"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("设置密码")
            .setView(et)
            .setPositiveButton("开启") { _, _ ->
                val pin = et.text.toString()
                if (pin.length != 4 || !pin.all { it.isDigit() }) {
                    Toast.makeText(requireContext(), "需为 4 位数字", Toast.LENGTH_SHORT).show()
                    vm.settings.passwordEnabled = false
                    rebuildRows()
                } else {
                    val s = vm.settings
                    val salt = AppLockActivity.randomSalt()
                    s.passwordSalt = salt
                    s.passwordHash = AppLockActivity.hash(pin, salt)
                    s.passwordEnabled = true
                    s.graceLeft = s.graceDefault
                    Toast.makeText(requireContext(), "密码已开启", Toast.LENGTH_SHORT).show()
                    rebuildRows()
                }
            }
            .setNegativeButton("取消") { _, _ -> rebuildRows() }
            .show()
    }

    private fun disablePassword() {
        confirm("关闭密码", "关闭后启动不再需要密码") {
            vm.settings.passwordEnabled = false
            rebuildRows()
        }
    }

    // ---------- 快捷输入 ----------
    private fun manageQuickInputs() {
        val s = vm.settings
        val list = s.getQuickInputs().toMutableList()
        val et = EditText(requireContext()).apply {
            setText(list.joinToString("\n"))
            gravity = android.view.Gravity.TOP
            minLines = 5
            hint = "每行一条"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("快捷输入(每行一条)")
            .setView(et)
            .setPositiveButton("保存") { _, _ ->
                s.setQuickInputs(et.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() })
                rebuildRows()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------- Skills ----------
    private fun manageSkills() {
        val skills = vm.skillStore.scan()
        if (skills.isEmpty()) { Toast.makeText(requireContext(), "暂无技能", Toast.LENGTH_SHORT).show(); return }
        val names = skills.map { "${if (it.enabled) "✓" else "○"} ${it.name}" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("技能(点击切换启用)")
            .setItems(names) { _, which ->
                val skill = skills[which]
                vm.setSkillEnabled(skill.fileName, !skill.enabled)
                rebuildRows()
            }
            .setNeutralButton("删除") { _, _ ->
                // 第二次弹窗删除
                val names2 = skills.map { it.name }.toTypedArray()
                AlertDialog.Builder(requireContext())
                    .setTitle("选择要删除的技能")
                    .setItems(names2) { _, which ->
                        vm.deleteSkill(skills[which].fileName)
                        rebuildRows()
                    }
                    .show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    // ---------- 记忆 ----------
    private fun manageMemories() {
        val memories = vm.memoryStore.list()
        if (memories.isEmpty()) { Toast.makeText(requireContext(), "暂无记忆", Toast.LENGTH_SHORT).show(); return }
        val names = memories.map { "${it.id}: ${it.content.take(30)}" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("记忆")
            .setItems(names) { _, which -> vm.deleteMemory(memories[which].id); rebuildRows() }
            .setNeutralButton("清空") { _, _ -> vm.clearMemories(); rebuildRows() }
            .setNegativeButton("关闭", null)
            .show()
    }

    // ---------- 会话 ----------
    private fun manageSessions() {
        val sessions = vm.sessionStore.list()
        if (sessions.isEmpty()) { Toast.makeText(requireContext(), "暂无会话", Toast.LENGTH_SHORT).show(); return }
        val names = sessions.map { "${it.title.take(12)} · ${it.messages.size}条" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("会话(点击删除)")
            .setItems(names) { _, which -> vm.deleteSession(sessions[which].id); rebuildRows() }
            .setNegativeButton("关闭", null)
            .show()
    }

    // ---------- 更新 ----------
    private var updateRepo: UpdateRepository? = null
    private fun autoCheckUpdate() {
        val s = vm.settings
        val now = System.currentTimeMillis()
        if (now - s.lastUpdateCheck < s.updateCooldownMin * 60_000L) return
        checkUpdate(manual = false)
    }

    private fun checkUpdate(manual: Boolean) {
        val s = vm.settings
        if (updateRepo == null) updateRepo = UpdateRepository(s)
        Toast.makeText(requireContext(), "检查更新中…", Toast.LENGTH_SHORT).show()
        syncScope.launch {
            val result = updateRepo!!.check()
            s.lastUpdateCheck = System.currentTimeMillis()
            withContext(Dispatchers.Main) {
                when (result) {
                    is io.github.xhr666.wristchat.data.UpdateResult.Found -> promptUpdate(result.info)
                    is io.github.xhr666.wristchat.data.UpdateResult.UpToDate ->
                        if (manual) Toast.makeText(requireContext(), "已是最新版本(${result.latest})", Toast.LENGTH_SHORT).show()
                    is io.github.xhr666.wristchat.data.UpdateResult.NoApk ->
                        if (manual) Toast.makeText(requireContext(), "发现 ${result.latest},但无 APK 资产", Toast.LENGTH_LONG).show()
                    is io.github.xhr666.wristchat.data.UpdateResult.Error ->
                        if (manual) Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun promptUpdate(info: ReleaseInfo) {
        AlertDialog.Builder(requireContext())
            .setTitle("发现新版本 ${info.tagName}")
            .setMessage("当前版本:${vm.settings.versionName}\n${info.body.take(200)}")
            .setPositiveButton("下载更新") { _, _ -> downloadAndInstall(info) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun downloadAndInstall(info: ReleaseInfo) {
        val url = info.apkUrl ?: return
        val target = File(requireContext().cacheDir, info.apkName ?: "update.apk")
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("下载中…")
            .setMessage("0%")
            .setCancelable(false)
            .show()
        syncScope.launch {
            val ok = updateRepo!!.download(url, target) { done, total ->
                val main = android.os.Handler(android.os.Looper.getMainLooper())
                main.post {
                    val pct = if (total > 0) (done * 100 / total).toInt() else -1
                    dialog.setMessage(if (pct >= 0) "$pct%" else "已下载 ${done / 1024}KB")
                }
            }
            withContext(Dispatchers.Main) {
                dialog.dismiss()
                if (ok) {
                    // 校验 SHA-256(如 Release 提供)
                    val hashOk = updateRepo!!.verifySha256(info.sha256, target)
                    if (hashOk) {
                        installApk(target)
                    } else {
                        target.delete()
                        Toast.makeText(requireContext(), "下载校验失败,已取消安装", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "下载失败,请重试", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "安装失败:${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- 手机同步 ----------
    private fun startSync() {
        val s = vm.settings
        if (s.syncPin.length != 4) s.syncPin = (1000..9999).random().toString()
        val server = SyncServer(
            pin = s.syncPin,
            readConfig = { configJson() },
            applyConfig = { body -> applyConfigJson(body) },
        )
        if (!server.start()) {
            Toast.makeText(requireContext(), "同步服务启动失败", Toast.LENGTH_SHORT).show()
            return
        }
        syncServer = server
        val ip = server.localIp() ?: "未知"
        val url = "http://$ip:${server.port}"
        AlertDialog.Builder(requireContext())
            .setTitle("手机同步已开启")
            .setMessage("手机连接同一网络后,浏览器打开:\n$url\n\nPIN:${s.syncPin}\n\n3 分钟无操作自动关闭")
            .setPositiveButton("复制地址") { _, _ ->
                val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("sync", "$url\nPIN:${s.syncPin}"))
                Toast.makeText(requireContext(), "已复制", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .setOnDismissListener { stopSync() }
            .show()
    }

    private fun stopSync() {
        syncServer?.stop()
        syncServer = null
    }

    private fun stopSyncIfIdle() {
        // 页面离开即停(简单处理)
    }

    private fun configJson(): String {
        val s = vm.settings
        val obj = org.json.JSONObject().apply {
            put("temperature", s.temperature)
            put("topP", s.topP)
            put("maxTokens", s.maxTokens)
            put("thinking", s.thinkingEnabled)
            put("effort", s.reasoningEffort)
            put("model", s.model)
            put("provider", s.providerId)
            put("baseUrl", s.baseUrl)
            put("apiPath", s.apiPath)
            put("customPrompt", s.customPrompt)
            put("quickInputs", org.json.JSONArray(s.getQuickInputs()))
        }
        return obj.toString()
    }

    private fun applyConfigJson(body: String): String {
        return try {
            val o = org.json.JSONObject(body)
            val s = vm.settings
            s.temperature = o.optDouble("temperature", 1.0).toFloat().coerceIn(0f, 2f)
            s.topP = o.optDouble("topP", 1.0).toFloat().coerceIn(0f, 1f)
            s.maxTokens = o.optInt("maxTokens", 4096).coerceIn(256, 65536)
            s.thinkingEnabled = o.optBoolean("thinking", true)
            s.reasoningEffort = o.optString("effort", "low")
            s.model = o.optString("model", s.model)
            s.providerId = o.optString("provider", s.providerId)
            s.baseUrl = o.optString("baseUrl", s.baseUrl)
            s.apiPath = o.optString("apiPath", s.apiPath)
            s.customPrompt = o.optString("customPrompt", s.customPrompt)
            o.optJSONArray("quickInputs")?.let { arr ->
                s.setQuickInputs((0 until arr.length()).map { arr.optString(it) })
            }
            o.optString("apiKey").takeIf { it.isNotBlank() }?.let { s.apiKey = it }
            val skillName = o.optString("skillName")
            val skillContent = o.optString("skillContent")
            if (skillContent.isNotBlank()) {
                vm.importSkill(skillName.ifBlank { "web_import.md" }, skillContent)
            }
            rebuildRows()
            "已保存"
        } catch (e: Exception) {
            "保存失败:${e.message}"
        }
    }

    private fun queryName(uri: Uri): String? {
        return try {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (e: Exception) { null }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopSync()
        _binding = null
    }
}
