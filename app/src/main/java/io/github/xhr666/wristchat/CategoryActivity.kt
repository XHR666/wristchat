package io.github.xhr666.wristchat

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.xhr666.wristchat.data.ImportParser
import io.github.xhr666.wristchat.data.Providers
import io.github.xhr666.wristchat.data.ReleaseInfo
import io.github.xhr666.wristchat.data.SettingsStore
import io.github.xhr666.wristchat.data.SyncServer
import io.github.xhr666.wristchat.data.UpdateRepository
import io.github.xhr666.wristchat.data.UpdateResult
import io.github.xhr666.wristchat.databinding.ActivityCategoryBinding
import android.widget.ImageView
import io.github.xhr666.wristchat.ui.common.QrUtil
import io.github.xhr666.wristchat.ui.common.RoundInsets
import io.github.xhr666.wristchat.ui.common.WristDialog
import io.github.xhr666.wristchat.ui.common.ThemeManager
import io.github.xhr666.wristchat.ui.settings.SettingRow
import io.github.xhr666.wristchat.ui.settings.SettingsAdapter
import io.github.xhr666.wristchat.ui.settings.SettingsViewModel
import io.github.xhr666.wristchat.ui.settings.SettingsViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 设置分类子页:通用宿主,按 category 键构建行 */
class CategoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoryBinding
    private lateinit var adapter: SettingsAdapter
    private val settings by lazy { (application as WristChatApp).settings }
    private val vm: SettingsViewModel by lazy {
        ViewModelProvider(this, SettingsViewModelFactory(application))[SettingsViewModel::class.java]
    }
    private var syncServer: SyncServer? = null
    private var updateRepo: UpdateRepository? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val importSkillLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            val name = queryName(uri) ?: "skill.md"
            val content = contentResolver.openInputStream(uri)?.use { it.readBytes()?.toString(Charsets.UTF_8) } ?: ""
            val saved = vm.importSkill(name, content)
            toast("已导入技能:$saved")
            rebuild()
        }.onFailure { toast("导入失败:${it.message}") }
    }

    private val importChatLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val size = runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (idx >= 0 && c.moveToFirst()) c.getLong(idx) else -1L
            } ?: -1L
        }.getOrDefault(-1L)
        if (size > 50L * 1024 * 1024) { toast("文件过大(>50MB)"); return@registerForActivityResult }
        scope.launch {
            val name = queryName(uri) ?: "chat.json"
            val content = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            } ?: return@launch
            val tmp = File(cacheDir, "import_$name")
            tmp.writeBytes(content)
            val result = ImportParser.parse(tmp, name)
            tmp.delete()
            withContext(Dispatchers.Main) {
                when (result) {
                    is ImportParser.ParseResult.Success -> confirm(
                        "导入确认",
                        "将导入 ${result.messages.size} 条消息,标题「${result.title}」"
                    ) {
                        toast(if (vm.importSession(result.messages, result.title)) "导入成功" else "导入失败")
                    }
                    is ImportParser.ParseResult.Error -> toast(result.message)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this, settings)
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        binding = ActivityCategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val category = intent.getStringExtra(EXTRA_CATEGORY) ?: CAT_SERVICE
        binding.tvTitle.text = titleOf(category)

        binding.root.post {
            val inset = RoundInsets.horizontalInsetPx(binding.root, binding.topBar.top.toFloat() + binding.topBar.height / 2f)
            val minInset = (10 * resources.displayMetrics.density).toInt()
            binding.topBar.setPadding(inset.coerceAtLeast(minInset), binding.topBar.paddingTop, inset.coerceAtLeast(minInset), binding.topBar.paddingBottom)
        }
        binding.btnBack.setOnClickListener { finish() }

        adapter = SettingsAdapter(emptyList())
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        rebuild()
    }

    override fun onResume() {
        super.onResume()
        rebuild()
    }

    override fun onPause() {
        super.onPause()
        syncServer?.stop()
        syncServer = null
    }

    // ---------- 行构建 ----------
    private fun rebuild() {
        val category = intent.getStringExtra(EXTRA_CATEGORY) ?: CAT_SERVICE
        adapter.submit(buildRows(category))
    }

    private fun buildRows(category: String): List<SettingRow> {
        val s = settings
        val rows = mutableListOf<SettingRow>()
        when (category) {
            CAT_SERVICE -> {
                rows += row("服务商", Providers.byId(s.providerId).name) {
                    val ids = Providers.all().map { it.id } + SettingsStore.PROVIDER_CUSTOM
                    singleChoice("服务商", ids.map { Providers.byId(it).name }, ids.indexOf(s.providerId).coerceAtLeast(0)) { idx ->
                        s.providerId = ids[idx]
                        if (ids[idx] != SettingsStore.PROVIDER_CUSTOM && s.baseUrl.isBlank()) {
                            s.baseUrl = Providers.byId(ids[idx]).defaultBaseUrl
                            s.apiPath = Providers.byId(ids[idx]).defaultPath
                        }
                        rebuild()
                    }
                }
                rows += row("API Key", if (s.apiKey.isBlank()) "(未设置)" else "已设置 · ${s.apiKey.takeLast(4)}") {
                    editText("API Key", s.apiKey, password = true) { s.apiKey = it; rebuild() }
                }
                rows += row("平台 Token(可选)", if (s.platformToken.isBlank()) "(未设置)" else "已设置") {
                    editText("平台 Token(仅今日用量)", s.platformToken, password = true) { s.platformToken = it; rebuild() }
                }
                rows += row("API Base URL", s.baseUrl.ifBlank { Providers.byId(s.providerId).defaultBaseUrl }) {
                    editText("API Base URL", s.baseUrl) { s.baseUrl = it; rebuild() }
                }
                rows += row("API 路径", s.apiPath.ifBlank { "/chat/completions" }) {
                    editText("API 路径", s.apiPath) { s.apiPath = it; rebuild() }
                }
            }
            CAT_MODEL -> {
                val modelLocked = s.apiKey.isBlank()
                rows += row(if (modelLocked) "模型(需先填 Key)" else "模型", s.model) {
                    if (modelLocked) toast("请先填写 API Key")
                    else {
                        val p = Providers.resolve(s)
                        val models = if (p.models.isEmpty()) listOf(s.model) else p.models
                        singleChoice("模型", models, models.indexOf(s.model).coerceAtLeast(0)) { idx ->
                            s.model = models[idx]; rebuild()
                        }
                    }
                }
                rows += toggle("思考模式(深度思考)", s.thinkingEnabled) { s.thinkingEnabled = it }
                rows += row("思考强度", s.reasoningEffort) {
                    val opts = listOf("low", "high", "max")
                    singleChoice("思考强度", opts, opts.indexOf(s.reasoningEffort).coerceAtLeast(0)) { idx ->
                        s.reasoningEffort = opts[idx]; rebuild()
                    }
                }
                rows += row("温度", s.temperature.toString()) { editNumber("温度(0-2)", s.temperature, 0f, 2f) { s.temperature = it; rebuild() } }
                rows += row("Top P", s.topP.toString()) { editNumber("Top P(0-1)", s.topP, 0f, 1f) { s.topP = it; rebuild() } }
                rows += row("最大输出 tokens", s.maxTokens.toString()) { editNumber("最大输出 tokens", s.maxTokens.toFloat(), 256f, 65536f) { s.maxTokens = it.toInt(); rebuild() } }
                rows += row("上下文窗口", s.contextWindow.toString()) { editNumber("上下文窗口", s.contextWindow.toFloat(), 10000f, 2000000f) { s.contextWindow = it.toInt(); rebuild() } }
                rows += row("压缩阈值 %", "${s.compressThreshold}%") { editNumber("压缩阈值(50-95)", s.compressThreshold.toFloat(), 50f, 95f) { s.compressThreshold = it.toInt(); rebuild() } }
                rows += row("自定义系统 Prompt", if (s.customPrompt.isBlank()) "(空)" else "已设置") { editMultiline("自定义系统 Prompt", s.customPrompt) { s.customPrompt = it; rebuild() } }
            }
            CAT_QUICK -> {
                rows += row("管理快捷输入", "${s.getQuickInputs().size} 条") { manageQuickInputs() }
            }
            CAT_SKILLS_MEMORY -> {
                rows += row("导入技能文件", "") { importSkillLauncher.launch(arrayOf("*/*")) }
                rows += row("刷新扫描", vm.skillsSummary()) {
                    vm.refreshSkillPrompt(); toast("已扫描 ${vm.skillStore.scan().size} 个技能")
                }
                rows += row("技能列表(点击切换)", vm.skillsSummary()) { manageSkills() }
                rows += toggle("自动记忆(模型写入)", s.memoryAuto) { s.memoryAuto = it }
                rows += row("查看/管理记忆", vm.memoriesSummary()) { manageMemories() }
            }
            CAT_SESSIONS -> {
                rows += row("会话列表(点击删除)", "${vm.sessionStore.list().size} 个 · ${vm.sessionsSize.value}") { manageSessions() }
                rows += row("导入聊天记录(文件)", "JSON / chatbox / 文本") { importChatLauncher.launch(arrayOf("*/*")) }
                rows += row("扫描导入文件夹", "filesDir/import/(无文件选择器备用)") {
                    val imported = vm.importFromFolder()
                    toast(if (imported.isEmpty()) "未发现可导入文件(请先放入 filesDir/import/)" else "已导入:${imported.joinToString()}")
                }
                rows += row("手机同步粘贴导入", "手机同步页粘贴聊天 JSON 后保存") { importFromWebPaste() }
            }
            CAT_STORAGE -> {
                rows += row("缓存详情", vm.cacheInfo.value ?: "") { vm.refreshSizes(); rebuild() }
                rows += row("清理缓存(安全)", "WebView/临时文件") {
                    confirm("清理缓存", "将删除 WebView 缓存和临时文件,不影响会话/记忆/设置") {
                        toast("已释放 ${vm.human(vm.clearCache())}")
                    }
                }
                rows += row("清理全部数据", "会话/记忆/技能/设置") {
                    confirm("清理全部数据", "将删除所有会话、记忆、技能和设置(不可恢复)") {
                        toast("已释放 ${vm.human(vm.clearAllData())}")
                    }
                }
            }
            CAT_UPDATE -> {
                rows += row("检查更新", "") { checkUpdate(manual = true) }
                if (syncServer != null) {
                    rows += row("手机同步", syncStatusText()) { startSync() }
                    rows += row("停止同步", "") { stopSync() }
                } else {
                    rows += row("手机同步", "未开启") { startSync() }
                }
                rows += row("仓库", "${s.repoOwner}/${s.repoName}") {
                    editText("仓库(owner/repo)", "${s.repoOwner}/${s.repoName}") { input ->
                        val parts = input.split("/")
                        if (parts.size == 2) { s.repoOwner = parts[0].trim(); s.repoName = parts[1].trim(); rebuild() }
                    }
                }
                rows += row("自动检查冷却(分钟)", s.updateCooldownMin.toString()) {
                    editNumber("冷却分钟(5-60)", s.updateCooldownMin.toFloat(), 5f, 60f) { s.updateCooldownMin = it.toInt(); rebuild() }
                }
            }
            CAT_SECURITY -> {
                rows += toggle("启动密码", s.passwordEnabled) { v -> if (v) enablePassword() else disablePassword() }
                rows += row("免密次数", s.graceDefault.toString()) {
                    editNumber("未来 N 次启动免密(0=每次都输)", s.graceDefault.toFloat(), 0f, 50f) { s.graceDefault = it.toInt(); rebuild() }
                }
                rows += row("主题", themeName(s.theme)) {
                    val ids = listOf(ThemeManager.LIGHT, ThemeManager.DARK, ThemeManager.AMOLED)
                    singleChoice("主题", listOf("亮色", "暗色", "AMOLED 纯黑"), ids.indexOf(s.theme).coerceAtLeast(0)) { idx ->
                        s.theme = ids[idx]
                        recreate()
                    }
                }
            }
            CAT_ABOUT -> {
                rows += SettingRow(SettingRow.VALUE, "版本", "${settings.versionName}(${BuildConfig.VERSION_CODE})")
                rows += row("开源仓库", "github.com/XHR666/wristchat") {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${s.repoOwner}/${s.repoName}")))
                }
            }
        }
        return rows
    }

    private fun row(title: String, value: String, onClick: () -> Unit) =
        SettingRow(SettingRow.VALUE, title, value, onClick = { onClick() })

    private fun toggle(title: String, checked: Boolean, onToggle: (Boolean) -> Unit) =
        SettingRow(SettingRow.TOGGLE, title, checked = checked, onToggle = onToggle)

    private fun titleOf(category: String): String = when (category) {
        CAT_SERVICE -> "服务"
        CAT_MODEL -> "模型与对话"
        CAT_QUICK -> "快捷输入"
        CAT_SKILLS_MEMORY -> "技能与记忆"
        CAT_SESSIONS -> "会话与导入"
        CAT_STORAGE -> "存储与缓存"
        CAT_UPDATE -> "更新与同步"
        CAT_SECURITY -> "安全与外观"
        CAT_ABOUT -> "关于"
        else -> "设置"
    }

    // ---------- 通用对话框 ----------
    private fun singleChoice(title: String, items: List<String>, checked: Int, onPick: (Int) -> Unit) {
        WristDialog.singleChoice(this, title, items, checked, onPick)
    }

    private fun editText(title: String, initial: String, password: Boolean = false, onSave: (String) -> Unit) {
        val et = EditText(this).apply {
            setText(initial); setSelection(initial.length)
            if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(4000))
        }
        WristDialog.build(this)
            .setTitle(title)
            .setView(et)
            .setPositive("保存") { onSave(et.text.toString()) }
            .setNegative("取消", null)
            .show()
    }

    private fun editNumber(title: String, initial: Float, min: Float, max: Float, onSave: (Float) -> Unit) {
        val et = EditText(this).apply {
            setText(if (initial % 1f == 0f) initial.toInt().toString() else initial.toString())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        WristDialog.build(this)
            .setTitle(title)
            .setView(et)
            .setPositive("保存") {
                val v = et.text.toString().toFloatOrNull()
                if (v == null || v < min || v > max) toast("无效输入") else onSave(v)
            }
            .setNegative("取消", null)
            .show()
    }

    private fun editMultiline(title: String, initial: String, onSave: (String) -> Unit) {
        val et = EditText(this).apply {
            setText(initial); gravity = Gravity.TOP; minLines = 4
        }
        WristDialog.build(this)
            .setTitle(title)
            .setView(et)
            .setPositive("保存") { onSave(et.text.toString()) }
            .setNegative("取消", null)
            .show()
    }

    private fun confirm(title: String, message: String, onOk: () -> Unit) {
        WristDialog.build(this)
            .setTitle(title)
            .setMessage(message)
            .setPositive("确定", onOk)
            .setNegative("取消", null)
            .show()
    }

    /** 无文件选择器备用:在手表上直接粘贴聊天 JSON 导入 */
    private fun importFromWebPaste() {
        val et = EditText(this).apply {
            gravity = Gravity.TOP
            minLines = 6
            hint = "粘贴聊天记录 JSON/文本(user:/assistant: 前缀)"
        }
        WristDialog.build(this)
            .setTitle("粘贴导入")
            .setView(et)
            .setPositive("导入") {
                val text = et.text.toString().trim()
                if (text.isEmpty()) { toast("内容为空") } else toast(vm.importFromText(text, "paste.json"))
            }
            .setNegative("取消", null)
            .show()
    }

    // ---------- 密码 ----------
    private fun enablePassword() {
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "4 位数字"
        }
        WristDialog.build(this)
            .setTitle("设置密码")
            .setView(et)
            .setPositive("开启") {
                val pin = et.text.toString()
                if (pin.length != 4 || !pin.all { it.isDigit() }) {
                    toast("需为 4 位数字")
                    settings.passwordEnabled = false
                } else {
                    val salt = AppLockActivity.randomSalt()
                    settings.passwordSalt = salt
                    settings.passwordHash = AppLockActivity.hash(pin, salt)
                    settings.passwordEnabled = true
                    settings.graceLeft = settings.graceDefault
                    toast("密码已开启")
                }
                rebuild()
            }
            .setNegative("取消") { rebuild() }
            .show()
    }

    private fun disablePassword() {
        confirm("关闭密码", "关闭后启动不再需要密码") { settings.passwordEnabled = false; rebuild() }
    }

    // ---------- 快捷输入 / 技能 / 记忆 / 会话 ----------
    private fun manageQuickInputs() {
        val et = EditText(this).apply {
            setText(settings.getQuickInputs().joinToString("\n"))
            gravity = Gravity.TOP; minLines = 5; hint = "每行一条"
        }
        WristDialog.build(this)
            .setTitle("快捷输入(每行一条)")
            .setView(et)
            .setPositive("保存") {
                settings.setQuickInputs(et.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() })
                rebuild()
            }
            .setNegative("取消", null)
            .show()
    }

    private fun manageSkills() {
        val skills = vm.skillStore.scan()
        if (skills.isEmpty()) { toast("暂无技能"); return }
        val names = skills.map { "${if (it.enabled) "✓" else "○"} ${it.name}" }.toList()
        WristDialog.items(this, "技能(点击切换)", names, { which ->
            val skill = skills[which]
            vm.setSkillEnabled(skill.fileName, !skill.enabled)
            rebuild()
        }, neutralText = "删除") {
            val names2 = skills.map { it.name }
            WristDialog.items(this, "选择要删除的技能", names2, onPick = { which ->
                vm.deleteSkill(skills[which].fileName)
                rebuild()
            })
        }
    }

    private fun manageMemories() {
        val memories = vm.memoryStore.list()
        if (memories.isEmpty()) { toast("暂无记忆"); return }
        val names = memories.map { "${it.id}: ${it.content.take(30)}" }.toList()
        WristDialog.items(this, "记忆(点击删除)", names, onPick = { which ->
            vm.deleteMemory(memories[which].id); rebuild()
        }, neutralText = "清空", onNeutral = {
            vm.clearMemories(); rebuild()
        })
    }

    private fun manageSessions() {
        val sessions = vm.sessionStore.list()
        if (sessions.isEmpty()) { toast("暂无会话"); return }
        val names = sessions.map { "${it.title.take(12)} · ${it.messages.size}条" }.toList()
        WristDialog.items(this, "会话(点击删除)", names, onPick = { which ->
            vm.deleteSession(sessions[which].id); rebuild()
        })
    }

    // ---------- 更新 ----------
    private fun checkUpdate(manual: Boolean) {
        if (updateRepo == null) updateRepo = UpdateRepository(settings)
        if (manual) toast("检查更新中…")
        scope.launch {
            val result = updateRepo!!.check()
            settings.lastUpdateCheck = System.currentTimeMillis()
            withContext(Dispatchers.Main) {
                when (result) {
                    is UpdateResult.Found -> promptUpdate(result.info)
                    is UpdateResult.UpToDate -> if (manual) toast("已是最新版本(${result.latest})")
                    is UpdateResult.NoApk -> if (manual) toast("发现 ${result.latest},但无 APK 资产")
                    is UpdateResult.Error -> if (manual) toast(result.message)
                }
            }
        }
    }

    private fun promptUpdate(info: ReleaseInfo) {
        WristDialog.build(this)
            .setTitle("发现新版本 ${info.tagName}")
            .setMessage("当前版本:${settings.versionName}\n${info.body.take(200)}")
            .setPositive("下载更新") { downloadAndInstall(info) }
            .setNegative("取消", null)
            .show()
    }

    private fun downloadAndInstall(info: ReleaseInfo) {
        val url = info.apkUrl ?: return
        val target = File(cacheDir, info.apkName ?: "update.apk")
        val progressText = TextView(this).apply {
            setTextColor(textColorAttr())
            textSize = 14f
            gravity = Gravity.CENTER
            text = "0%"
        }
        val dialog = WristDialog.build(this)
            .setTitle("下载中…")
            .hidePositive().hideNegative()
            .setCancelable(false)
            .setView(progressText)
        dialog.show()
        scope.launch {
            val ok = updateRepo!!.download(url, target) { done, total ->
                val pct = if (total > 0) (done * 100 / total).toInt() else -1
                runOnUiThread { progressText.text = if (pct >= 0) "$pct%" else "已下载 ${done / 1024}KB" }
            }
            runOnUiThread {
                dialog.dismiss()
                if (ok) {
                    val hashOk = scopeCoroutineVerifier(info, target)
                    if (hashOk) installApk(target) else { target.delete(); toast("下载校验失败,已取消安装") }
                } else toast("下载失败,请重试")
            }
        }
    }

    private fun scopeCoroutineVerifier(info: ReleaseInfo, target: File): Boolean {
        // 简单同步校验(下载已完成,量小)
        val sha = info.sha256
        if (sha.isNullOrBlank()) return true
        return try {
            val conn = java.net.URL(sha).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10000; conn.readTimeout = 10000
            val text = conn.inputStream.readBytes().toString(Charsets.UTF_8)
            val expected = text.trim().split(Regex("\\s+")).firstOrNull()?.lowercase() ?: return false
            val actual = java.security.MessageDigest.getInstance("SHA-256")
                .digest(target.readBytes()).joinToString("") { "%02x".format(it) }
            expected == actual
        } catch (e: Exception) { false }
    }

    private fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            toast("安装失败:${e.message}")
        }
    }

    // ---------- 手机同步 ----------
    private fun startSync() {
        val s = settings
        if (s.syncPin.length != 4) s.syncPin = (1000..9999).random().toString()
        val server = SyncServer(
            pin = s.syncPin,
            readConfig = { configJson() },
            applyConfig = { body -> applyConfigJson(body) },
        )
        if (!server.start()) { toast("同步服务启动失败"); return }
        syncServer = server
        val ip = server.localIp() ?: "未知"
        val url = "http://$ip:${server.port}"

        // 二维码 + 地址 + PIN(关闭弹窗不停服务,离开本页才停)
        val column = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val qrSize = (150 * resources.displayMetrics.density).toInt()
        val qr = ImageView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(qrSize, qrSize)
            setImageBitmap(QrUtil.generate(url, qrSize))
        }
        column.addView(qr)
        column.addView(TextView(this).apply {
            text = url
            setTextColor(textColorAttr()); textSize = 11f; gravity = Gravity.CENTER
        })
        column.addView(TextView(this).apply {
            text = "密钥:${s.syncPin}(手机需先输入才能操作)"
            setTextColor(textColorAttr()); textSize = 12f; gravity = Gravity.CENTER
        })

        val d = WristDialog.build(this)
            .setTitle("手机同步已开启")
            .setView(column)
            .setPositive("复制") {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("sync", "$url\nPIN:${s.syncPin}"))
                toast("已复制")
            }
            .setNegative("关闭", null)  // 仅关弹窗,服务继续(离开本页自动停)
        d.show()
        rebuild()
    }

    private fun stopSync() {
        syncServer?.stop()
        syncServer = null
        rebuild()
    }

    private fun syncStatusText(): String {
        val s = syncServer
        if (s == null) return "未开启"
        val ip = s.localIp() ?: "?"
        return "运行中 http://$ip:${s.port} · PIN:${settings.syncPin}"
    }

    private fun configJson(): String {
        val s = settings
        return org.json.JSONObject().apply {
            put("temperature", s.temperature); put("topP", s.topP)
            put("maxTokens", s.maxTokens); put("thinking", s.thinkingEnabled)
            put("effort", s.reasoningEffort); put("model", s.model)
            put("provider", s.providerId); put("baseUrl", s.baseUrl); put("apiPath", s.apiPath)
            put("customPrompt", s.customPrompt)
            put("quickInputs", org.json.JSONArray(s.getQuickInputs()))
        }.toString()
    }

    private fun applyConfigJson(body: String): String {
        return try {
            val o = org.json.JSONObject(body)
            val s = settings
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
            o.optJSONArray("quickInputs")?.let { arr -> s.setQuickInputs((0 until arr.length()).map { arr.optString(it) }) }
            o.optString("apiKey").takeIf { it.isNotBlank() }?.let { s.apiKey = it }
            val skillName = o.optString("skillName"); val skillContent = o.optString("skillContent")
            if (skillContent.isNotBlank()) vm.importSkill(skillName.ifBlank { "web_import.md" }, skillContent)
            // 聊天记录粘贴导入(手机同步页)
            val chatImport = o.optString("chatImport")
            if (chatImport.isNotBlank()) {
                val r = vm.importFromText(chatImport, "web_chat.json")
                return if (r.startsWith("已导入")) "已保存;$r" else "已保存(聊天导入失败:$r)"
            }
            rebuild()
            "已保存"
        } catch (e: Exception) { "保存失败:${e.message}" }
    }

    private fun queryName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()

    private fun themeName(t: String) = when (t) {
        ThemeManager.LIGHT -> "亮色"; ThemeManager.DARK -> "暗色"; else -> "AMOLED 纯黑"
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun textColorAttr(): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(io.github.xhr666.wristchat.R.attr.wristText, tv, true)
        return tv.data
    }

    override fun onDestroy() {
        syncServer?.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CATEGORY = "category"
        const val CAT_SERVICE = "service"
        const val CAT_MODEL = "model"
        const val CAT_QUICK = "quick"
        const val CAT_SKILLS_MEMORY = "skills_memory"
        const val CAT_SESSIONS = "sessions"
        const val CAT_STORAGE = "storage"
        const val CAT_UPDATE = "update"
        const val CAT_SECURITY = "security"
        const val CAT_ABOUT = "about"
    }
}
