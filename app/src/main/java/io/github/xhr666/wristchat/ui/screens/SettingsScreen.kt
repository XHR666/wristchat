package io.github.xhr666.wristchat.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xhr666.wristchat.WristChatApp
import io.github.xhr666.wristchat.data.*
import io.github.xhr666.wristchat.ui.*
import io.github.xhr666.wristchat.ui.settings.SettingsViewModel
import io.github.xhr666.wristchat.ui.settings.SettingsViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private data class Cat(val key: String, val title: String, val desc: String)
private val CATS = listOf(
    Cat("service", "服务", "服务商 / Key / 地址"),
    Cat("model", "模型与对话", "模型 / 思考 / 参数"),
    Cat("quick", "快捷输入", "逐条管理"),
    Cat("skills_memory", "技能与记忆", "Skills / 记忆"),
    Cat("sessions", "会话与导入", "导入 / 管理"),
    Cat("storage", "存储与缓存", "清理 / 占用"),
    Cat("update", "更新与同步", "检查更新 / 手机同步"),
    Cat("security", "安全与外观", "密码 / 主题 / 缩放"),
    Cat("about", "关于", "版本 / 许可 / 日志"),
)
private val CAT_TITLE = CATS.associate { it.key to it.title }

@Composable
fun SettingsMenuScreen(settings: SettingsStore) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as WristChatApp
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(app))
    val warn by vm.sessionWarn.observeAsState(false)
    var openCat by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { vm.refreshSizes() }

    openCat?.let { cat ->
        CategoryScreen(settings, vm, cat) { openCat = null }
        return
    }

    ScreenScaffold(title = "设置", showTimeAlways = true) {
        Column(Modifier.fillMaxSize()) {
            if (warn) Text("会话已达上限,建议清理", color = Color(0xFFFFD9A0), fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(Color(0xFF3D2E14)).padding(6.dp))
            LazyColumn(state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp).scrollArc(listState),
                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)) {
                items(CATS) { cat -> WCard(cat.title, cat.desc) { openCat = cat.key } }
            }
        }
        RotaryList(listState, enabled = LocalCurrentPage.current == 3)
    }
}

@Composable
fun CategoryScreen(settings: SettingsStore, vm: SettingsViewModel, cat: String, onBack: () -> Unit) {
    val dialogs = remember { DialogController() }
    val listState = rememberLazyListState()
    var syncOpen by remember { mutableStateOf(false) }

    if (syncOpen) {
        PagerLock.locked = true
        DisposableEffect(Unit) { onDispose { PagerLock.locked = false } }
        SyncOverlay(settings, vm) { syncOpen = false }
        return
    }
    LaunchedEffect(Unit) { PagerLock.locked = true }
    DisposableEffect(Unit) { onDispose { PagerLock.locked = false } }

    SwipeBack(onBack) {
        ScreenScaffold(title = CAT_TITLE[cat] ?: "设置", actions = { SmallAction("‹") { onBack() } }) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp).scrollArc(listState),
            contentPadding = PaddingValues(top = 4.dp, bottom = 14.dp)) {
            when (cat) {
                "service" -> serviceRows(settings, dialogs)
                "model" -> modelRows(settings, dialogs)
                "quick" -> quickRows(settings, dialogs)
                "skills_memory" -> skillRows(settings, vm, dialogs)
                "sessions" -> sessionRows(settings, vm, dialogs)
                "storage" -> storageRows(settings, vm, dialogs)
                "update" -> updateRows(settings, dialogs) { syncOpen = true }
                "security" -> securityRows(settings, dialogs)
                "about" -> aboutRows(settings, vm, dialogs)
            }
        }
            RotaryList(listState, enabled = LocalCurrentPage.current == 3)
        }
        WDialogHost(dialogs)
    }
}

// ---------- 各行内容 ----------
private fun LazyListScope.serviceRows(s: SettingsStore, d: DialogController) {
    item { WCard("服务商", Providers.byId(s.providerId).name) {
        val ids = listOf("deepseek", "qwen", "glm", "kimi", "volcano", "custom")
        d.choice("服务商", ids.map { Providers.byId(it).name }, ids.indexOf(s.providerId).coerceAtLeast(0)) { i ->
            s.providerId = ids[i]
            if (ids[i] != "custom") { val p = Providers.byId(ids[i]); s.baseUrl = p.defaultBaseUrl; s.apiPath = p.defaultPath }
        }
    } }
    item { WCard("API Key", if (s.apiKey.isBlank()) "(未设置)" else "已设置 · ${s.apiKey.takeLast(4)}") {
        d.input("API Key", s.apiKey, pw = true) { s.apiKey = it }
    } }
    item { WCard("平台 Token(可选)",
        (if (s.platformToken.isBlank()) "(未设置)\n" else "已设置\n") +
        "用途:余额页「今日已用」实时查询\n获取:DeepSeek 开放平台网页登录后,浏览器开发者工具里复制 Authorization 的 Bearer 值\n留空:自动用余额差值本地估算") {
        d.input("平台 Token", s.platformToken, pw = true) { s.platformToken = it }
    } }
    item { WCard("API Base URL", s.baseUrl.ifBlank { Providers.byId(s.providerId).defaultBaseUrl }) {
        d.input("API Base URL", s.baseUrl.ifBlank { Providers.byId(s.providerId).defaultBaseUrl }) { s.baseUrl = it }
    } }
    item { WCard("API 路径", s.apiPath.ifBlank { "/chat/completions" }) {
        d.input("API 路径", s.apiPath.ifBlank { "/chat/completions" }) { s.apiPath = it }
    } }
}

private fun LazyListScope.modelRows(s: SettingsStore, d: DialogController) {
    item { val locked = s.apiKey.isBlank()
        WCard(if (locked) "模型(需先填 Key)" else "模型", s.model) {
            if (locked) d.text("提示", "请先填写 API Key")
            else { val ms = Providers.resolve(s).models.ifEmpty { listOf(s.model) }
                d.choice("模型", ms, ms.indexOf(s.model).coerceAtLeast(0)) { i -> s.model = ms[i] } }
        }
    }
    item { WToggle("思考模式(深度思考)", s.thinkingEnabled) { s.thinkingEnabled = it } }
    item { WCard("思考强度", s.reasoningEffort) { val o = listOf("low", "high", "max")
        d.choice("思考强度", o, o.indexOf(s.reasoningEffort).coerceAtLeast(0)) { i -> s.reasoningEffort = o[i] } } }
    item { WCard("温度", s.temperature.toString() + "\n(高级参数:不要修改,除非你知道自己在干什么)") { d.num("温度(0-2)", s.temperature, 0f, 2f) { s.temperature = it } } }
    item { WCard("Top P", s.topP.toString() + "\n(高级参数:不要修改,除非你知道自己在干什么)") { d.num("Top P(0-1)", s.topP, 0f, 1f) { s.topP = it } } }
    item { WCard("最大输出 tokens", s.maxTokens.toString()) { d.num("max tokens", s.maxTokens.toFloat(), 256f, 65536f) { s.maxTokens = it.toInt() } } }
    item { WCard("上下文窗口", s.contextWindow.toString()) { d.num("上下文窗口", s.contextWindow.toFloat(), 10000f, 2000000f) { s.contextWindow = it.toInt() } } }
    item { WCard("压缩阈值 %", "${s.compressThreshold}%") { d.num("压缩阈值(50-95)", s.compressThreshold.toFloat(), 50f, 95f) { s.compressThreshold = it.toInt() } } }
    item { WCard("自定义系统 Prompt", if (s.customPrompt.isBlank()) "(空)" else "已设置") { d.input("自定义 Prompt", s.customPrompt, ml = true) { s.customPrompt = it } } }
    item { WToggle("自动生成会话标题", s.autoTitle) { s.autoTitle = it } }
}

private fun LazyListScope.quickRows(s: SettingsStore, d: DialogController) {
    item { WCard("＋ 添加快捷输入", "单行,上限 20") { quickEditDialog(s, d, null, null) } }
    s.getQuickInputs().forEachIndexed { i, q ->
        item { WCard(q.take(26), "点击编辑") { quickEditDialog(s, d, q, i) } }
    }
}

private fun quickEditDialog(s: SettingsStore, d: DialogController, existing: String?, index: Int?) {
    d.input(if (existing == null) "添加快捷输入" else "编辑快捷输入", existing ?: "") { text ->
        val v = text.trim()
        if (v.isEmpty()) { d.text("提示", "内容为空"); return@input }
        val l = s.getQuickInputs().toMutableList()
        if (existing == null) { if (l.size >= 20) { d.text("提示", "已达 20 条上限"); return@input }; l.add(v) }
        else if (index != null && index < l.size) l[index] = v
        s.setQuickInputs(l)
    }
}

private fun LazyListScope.skillRows(s: SettingsStore, vm: SettingsViewModel, d: DialogController) {
    item {
        val ctx = LocalContext.current
        val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                val name = queryName(ctx, uri) ?: "skill.md"
                val content = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes()?.toString(Charsets.UTF_8) } ?: ""
                vm.importSkill(name, content); d.text("导入", "已导入技能:$name")
            }.onFailure { d.text("导入失败", it.message ?: "") }
        }
        WCard("导入技能文件", "需系统文件选择器") { importer.launch(arrayOf("*/*")) }
    }
    item { WCard("刷新扫描", vm.skillsSummary()) { vm.refreshSkillPrompt(); d.text("扫描", "已扫描 ${vm.skillStore.scan().size} 个技能") } }
    item { WCard("技能管理", "点击切换启用") {
        val skills = vm.skillStore.scan()
        if (skills.isEmpty()) { d.text("技能", "暂无技能"); return@WCard }
        d.items("技能(点击切换)", skills.map { "${if (it.enabled) "✓" else "○"} ${it.name}" }, { i ->
            val sk = skills[i]; vm.setSkillEnabled(sk.fileName, !sk.enabled)
        }, neutral = "删除一个") {
            val s2 = vm.skillStore.scan()
            if (s2.isNotEmpty()) d.items("选择要删除的技能", s2.map { it.name }, { i -> vm.deleteSkill(s2[i].fileName) })
        }
    } }
    item { WToggle("自动记忆(模型写入)", s.memoryAuto) { s.memoryAuto = it } }
    item { val mems = vm.memoryStore.list()
        WCard("记忆(${mems.size})", if (mems.isEmpty()) "(空)" else "点击删除") {
            if (mems.isEmpty()) { d.text("记忆", "暂无记忆"); return@WCard }
            d.items("记忆(点击删除)", mems.map { "${it.id}: ${it.content.take(30)}" }, { i -> vm.deleteMemory(mems[i].id) },
                neutral = "清空全部") { vm.clearMemories() }
        }
    }
}

private fun LazyListScope.sessionRows(s: SettingsStore, vm: SettingsViewModel, d: DialogController) {
    item {
        val ctx = LocalContext.current
        val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                val name = queryName(ctx, uri) ?: "chat.json"
                val content = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes()?.toString(Charsets.UTF_8) } ?: return@runCatching
                when (val r = ImportParser.parseString(content, name)) {
                    is ImportParser.ParseResult.Success -> { vm.importSession(r.messages, r.title); d.text("导入", "已导入 ${r.messages.size} 条") }
                    is ImportParser.ParseResult.Error -> d.text("导入失败", r.message)
                }
            }.onFailure { d.text("导入失败", it.message ?: "") }
        }
        WCard("导入聊天记录", "JSON / chatbox / 文本") { importer.launch(arrayOf("*/*")) }
    }
    item { WCard("会话列表", "${vm.sessionStore.list().size} 个 · ${vm.sessionsSize.value}") {
        val sessions = vm.sessionStore.list()
        if (sessions.isEmpty()) { d.text("会话", "暂无会话"); return@WCard }
        d.items("会话(点击删除)", sessions.map { "${it.title.take(12)} · ${it.messages.size}条" }, { i -> vm.deleteSession(sessions[i].id) })
    } }
    item { WCard("扫描导入文件夹", "filesDir/import/ 备用") {
        val r = vm.importFromFolder()
        d.text("扫描导入", if (r.isEmpty()) "未发现可导入文件" else "已导入:${r.joinToString()}")
    } }
    item { WCard("粘贴导入(手表端)", "") {
        d.input("粘贴聊天 JSON/文本", "", ml = true, ok = "导入") { t ->
            d.text("导入结果", if (t.isBlank()) "内容为空" else vm.importFromText(t, "paste.json"))
        }
    } }
}

private fun LazyListScope.storageRows(s: SettingsStore, vm: SettingsViewModel, d: DialogController) {
    item { WCard("缓存详情", vm.cacheInfo.value ?: "") { vm.refreshSizes() } }
    item { WCard("清理缓存(安全)", "WebView/临时文件") {
        d.confirm("清理缓存", "删除 WebView 缓存与临时文件,不影响数据") { d.text("结果", "已释放 ${vm.human(vm.clearCache())}") }
    } }
    item { WCard("清理全部数据", "会话/记忆/技能/设置") {
        d.confirm("清理全部数据", "将删除所有会话、记忆、技能和设置(不可恢复)", countdown = 5) { d.text("结果", "已释放 ${vm.human(vm.clearAllData())}") }
    } }
}

private fun LazyListScope.updateRows(s: SettingsStore, d: DialogController, openSync: () -> Unit) {
    item {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var prog by remember { mutableStateOf<Int?>(null) }
        var job: kotlinx.coroutines.Job? = null
        WCard("检查更新", "") {
            val repo = UpdateRepository(s)
            io.github.xhr666.wristchat.data.AppLog.i("upd", "check start v=${s.versionName}")
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val r = repo.check()
                    s.lastUpdateCheck = System.currentTimeMillis()
                    io.github.xhr666.wristchat.data.AppLog.i("upd", "check=$r")
                    launch(kotlinx.coroutines.Dispatchers.Main) {
                        when (r) {
                            is UpdateResult.Found -> d.confirm("发现新版本 ${r.info.tagName}",
                                "当前:${s.versionName}\n${r.info.body.take(160)}", ok = "下载更新") {
                                io.github.xhr666.wristchat.data.AppLog.i("upd", "user taps download")
                                prog = -1
                                job = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        val msg = installRelease(ctx, repo, r.info) { done, total ->
                                            launch(kotlinx.coroutines.Dispatchers.Main) {
                                                prog = if (total > 0) (done * 100 / total).toInt() else -1
                                            }
                                        }
                                        io.github.xhr666.wristchat.data.AppLog.i("upd", "done: $msg")
                                        launch(kotlinx.coroutines.Dispatchers.Main) { prog = null; d.text("更新", msg) }
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        io.github.xhr666.wristchat.data.AppLog.i("upd", "cancelled by user")
                                        launch(kotlinx.coroutines.Dispatchers.Main) { prog = null }
                                        throw e
                                    } catch (e: Exception) {
                                        io.github.xhr666.wristchat.data.AppLog.i("upd", "err ${e}")
                                        launch(kotlinx.coroutines.Dispatchers.Main) { prog = null; d.text("更新失败", e.message ?: "未知错误") }
                                    }
                                }
                            }
                            is UpdateResult.UpToDate -> d.text("更新", "已是最新(${r.latest})")
                            is UpdateResult.NoApk -> d.text("更新", "发现 ${r.latest},但无 APK 资产")
                            is UpdateResult.Error -> d.text("更新失败", r.message)
                        }
                    }
                } catch (e: Exception) {
                    io.github.xhr666.wristchat.data.AppLog.i("upd", "check err ${e}")
                    launch(kotlinx.coroutines.Dispatchers.Main) { d.text("更新失败", e.message ?: "未知错误") }
                }
            }
        }
        prog?.let { p ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("下载更新", fontSize = 15.sp) },
                text = { Text(if (p < 0) "连接中…" else "下载中 $p%", fontSize = 13.sp) },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { job?.cancel() }) { Text("取消") } },
                containerColor = MaterialTheme.colorScheme.surface,
            )
        }
        }
    item { WCard("手机同步(二维码)", "") { openSync() } }
    item { WCard("自动检查冷却(分钟)", s.updateCooldownMin.toString()) { d.num("冷却(5-60)", s.updateCooldownMin.toFloat(), 5f, 60f) { s.updateCooldownMin = it.toInt() } } }
}

private fun LazyListScope.securityRows(s: SettingsStore, d: DialogController) {
    item { WToggle("启动密码", s.passwordEnabled) { on ->
        if (on) d.input("设置密码(4 位数字)", "", pw = true, ok = "开启") { pin ->
            if (pin.length == 4 && pin.all { it.isDigit() }) {
                val salt = randomSalt(); s.passwordSalt = salt
                s.passwordHash = hashPin(pin, salt); s.passwordEnabled = true
                s.graceLeft = s.graceDefault
            } else d.text("提示", "需为 4 位数字")
        } else d.confirm("关闭密码", "关闭后启动不再需要密码", ok = "关闭") { s.passwordEnabled = false }
    } }
    item { WCard("免密次数", s.graceDefault.toString()) { d.num("未来 N 次免密(0=每次都输)", s.graceDefault.toFloat(), 0f, 50f) { s.graceDefault = it.toInt() } } }
    item {
        val act = LocalContext.current as? android.app.Activity
        WCard("主题", themeName(s.theme)) {
            val ids = listOf("light", "dark", "amoled")
            d.choice("主题", ids.map { themeName(it) }, ids.indexOf(s.theme).coerceAtLeast(0)) { i ->
                s.theme = ids[i]; act?.recreate()
            }
        }
    }
    item {
        val act = LocalContext.current as? android.app.Activity
        WCard("显示大小", "%.2f".format(s.displayScale) + if (s.displayScale == 1f) " (默认)" else "") {
            val opts = (0 until 9).map { 0.9f + it * 0.05f }
            d.choice("显示大小(即时生效)", opts.map { "%.2f".format(it) }, opts.indexOf(s.displayScale).coerceAtLeast(0)) { i ->
                s.displayScale = opts[i]; act?.recreate()
            }
        }
    }
}

private fun LazyListScope.aboutRows(s: SettingsStore, vm: SettingsViewModel, d: DialogController) {
    item { WCard("版本", s.versionName) }
    item { WCard("开源许可", "MIT + 第三方库") { d.text("开源许可", LICENSE_TEXT) } }
    item {
        val app = LocalContext.current.applicationContext as WristChatApp
        WCard("运行日志(含更新)", "") {
            d.text("运行日志", io.github.xhr666.wristchat.data.AppLog.read())
        }
        WCard("查看崩溃日志", "") { d.text("崩溃日志", app.crashLogText().take(1200)) }
    }
    item {
        val ctx = LocalContext.current
        WCard("开源仓库", "github.com/XHR666/wristchat") {
            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://github.com/XHR666/wristchat")))
        }
    }
}

private const val LICENSE_TEXT = """
本项目:MIT License
第三方组件:
- ZXing core (Apache-2.0)
- Markwon (Apache-2.0)
- KaTeX (MIT) / marked.js (MIT)
- AndroidX / Jetpack Compose (Apache-2.0)
- kotlinx-coroutines (Apache-2.0)
"""

private suspend fun installRelease(
    ctx: Context, repo: UpdateRepository, info: ReleaseInfo,
    onProgress: (Long, Long) -> Unit = { _, _ -> },
): String {
    val dir = java.io.File(ctx.cacheDir, "updates").apply { mkdirs() }
    val target = java.io.File(dir, info.apkName ?: "update.apk")
    val note = if (target.exists() && target.length() > 0) "(有 ${target.length() / 1024}KB 部分,将续传)" else ""
    if (!repo.download(info.apkUrl ?: "", target, onProgress)) {
        return if (target.exists() && target.length() > 0)
            "下载中断,已保留 ${target.length() / 1024}KB\n恢复网络后重新下载会断点续传$note"
        else "下载失败,请重试"
    }
    if (!repo.verifySha256(info.sha256, target)) { target.delete(); return "下载校验失败,已取消" }
    return try {
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", target)
        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        "已调起系统安装器"
    } catch (e: Exception) { "安装失败:${e.message}" }
}

private fun themeName(t: String) = when (t) { "light" -> "亮色"; "dark" -> "暗色"; else -> "AMOLED 纯黑" }
private fun hashPin(pin: String, salt: String): String {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    return md.digest("$salt:$pin".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
private fun randomSalt(): String {
    val b = ByteArray(16); java.security.SecureRandom().nextBytes(b)
    return b.joinToString("") { "%02x".format(it) }
}
private fun queryName(ctx: Context, uri: Uri): String? = runCatching {
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && c.moveToFirst()) c.getString(i) else null
    }
}.getOrNull()
