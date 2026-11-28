package io.github.xhr666.wristchat.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
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

private val CATS = SettingsCatalog.root
private val CAT_TITLE = CATS.associate { it.key to it.title } +
    mapOf("mp_models" to "默认模型", "mp_prompts" to "提示词",
        "pref_ui" to "界面", "pref_theme" to "主题", "pref_notify" to "通知",
        "pref_general" to "常规", "pref_net" to "网络", "quick" to "输入设置")

@Composable
fun SettingsMenuScreen(settings: SettingsStore) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as WristChatApp
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(app))
    val warn by vm.sessionWarn.observeAsState(false)
    var openCat by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { vm.refreshSizes() }

    Box(Modifier.fillMaxSize()) {
        // 菜单常驻组合:分类页右滑返回时下面露出的就是它(不再黑屏)
        ScreenScaffold(title = "设置", showTimeAlways = true,
            scrollIndicator = if (openCat == null) listState else null) {
            Column(Modifier.fillMaxSize()) {
                if (warn) Row(
                    Modifier.fillMaxWidth().background(Color(0xFF3D2E14)).padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("会话内容占用过多，建议清理", color = Color(0xFFFFD9A0), fontSize = 12.sp,
                        modifier = Modifier.weight(1f), maxLines = 2)
                    Text("X", color = Color(0xFFFFD9A0), fontSize = 14.sp,
                        modifier = Modifier.clickable { vm.dismissStorageWarn() }.padding(8.dp))
                }
                LazyColumn(state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    // 圆表:首项居中 + 末项也能滚到中间(官方 ScalingLazyColumn 的居中语义)
                    contentPadding = PaddingValues(
                        start = 18.dp, end = 18.dp,
                        top = LocalListCenterPad.current,
                        bottom = LocalListBottomPad.current),
                ) {
                    itemsIndexed(CATS) { _, cat ->
                        WCard(cat.title, cat.desc) { openCat = cat.key }
                    }
                }
            }
            RotaryList(listState, enabled = LocalCurrentPage.current == 3 && openCat == null)
        }
        openCat?.let { cat ->
            CategoryScreen(settings, vm, cat) { openCat = null }
        }
    }
}

@Composable
fun CategoryScreen(settings: SettingsStore, vm: SettingsViewModel, cat: String, onBack: () -> Unit) {
    val dialogs = remember { DialogController() }
    val listState = rememberLazyListState()
    var syncOpen by remember { mutableStateOf(false) }
    var logPage by remember { mutableStateOf<String?>(null) }  // crash / anr / run
    var subPage by remember { mutableStateOf<String?>(null) }  // mp_models / mp_prompts

    // 分类页/同步页期间锁定横滑翻页:SideEffect 提交后写(不在组合期直接写)
    SideEffect { PagerLock.locked = true }
    DisposableEffect(Unit) { onDispose { PagerLock.locked = false } }

    settings.rev   // 订阅设置变化:服务商/Key/地址等改完立即反映到界面
    Box(Modifier.fillMaxSize()) {
        // 分类页本体:右滑跟手退出时,下面露出的是设置菜单(由上层组合提供)
        SwipeBackContainer(onBack = onBack) {
            ScreenScaffold(title = CAT_TITLE[cat] ?: "设置",
                actions = { SmallAction("‹") { onBack() } },
                onHeaderSwipeBack = onBack,
                scrollIndicator = if (!syncOpen && logPage == null) listState else null) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 18.dp, end = 18.dp,
                        top = LocalListCenterPad.current,
                        bottom = LocalListBottomPad.current)) {
                    settings.rev
                    when (cat) {
                        "service" -> serviceRows(settings, dialogs)
                        "quick" -> quickRows(settings, dialogs)
                        "skills" -> skillRows(settings, vm, dialogs)
                        "assistant" -> assistantRows(settings, vm, dialogs)
                        "backup" -> backupRows(settings, vm, dialogs)
                        "storage" -> storageRows(settings, vm, dialogs)
                        "security" -> securityRows(settings, dialogs)
                        "about" -> {
                            updateRows(settings, dialogs)
                            aboutRows(settings, vm, dialogs) { logPage = it }
                        }
                        "search" -> searchRows(settings, dialogs)
                        "sync" -> item { WCard("打开同步", "四位 PIN，只在同一局域网") { syncOpen = true } }
                        "models_prompts" -> modelsPromptsRows(settings, dialogs) { subPage = it }
                        "prefs" -> prefRows { subPage = it }
                        "pref_ui" -> uiPrefRows(settings, dialogs)
                        "pref_theme" -> themePrefRows(settings, dialogs)
                        "pref_notify" -> notifyRows(settings, dialogs)
                        "pref_general" -> generalRows(settings, dialogs) { subPage = it }
                        "pref_net" -> netRows(settings, dialogs)
                        "mp_models" -> defaultModelRows(settings, dialogs)
                        "mp_prompts" -> promptRows(settings, dialogs)
                    }
                }
                RotaryList(listState, enabled = LocalCurrentPage.current == 3 && !syncOpen && logPage == null)
            }
        }
        // 子页(默认模型 / 提示词):叠在分类页之上,右滑返回露出上一层
        subPage?.let { sp ->
            CategoryScreen(settings, vm, sp, onBack = { subPage = null })
        }
        // 同步页 / 日志页:叠在分类页之上,返回时露出分类页
        if (syncOpen) {
            SyncOverlay(settings, vm) { syncOpen = false }
        }
        logPage?.let { t ->
            val app = LocalContext.current.applicationContext as WristChatApp
            LogViewerScreen(
                title = when (t) { "crash" -> "崩溃日志"; "anr" -> "ANR/卡死日志"; "sess" -> "会话自检"; else -> "运行日志" },
                load = when (t) {
                    "crash" -> { { app.crashLogText() } }
                    "anr" -> { { app.anrLogText() } }
                    else -> { { io.github.xhr666.wristchat.data.AppLog.read() } }
                },
                onBack = { logPage = null },
            )
        }
    }
    WDialogHost(dialogs)
}

// ---------- 各行内容 ----------

/** 偏好设置:主题、通知、常规、界面、网络。颜色模式在圆表上没有对应能力，不放。 */
private fun LazyListScope.prefRows(openSub: (String) -> Unit) {
    item { WCard("主题", "浅色 / 深色 / AMOLED") { openSub("pref_theme") } }
    item { WCard("通知", "更新提醒 / 回复完成") { openSub("pref_notify") } }
    item { WCard("常规", "思考 / 参数 / 输入") { openSub("pref_general") } }
    item { WCard("界面", "消息显示 / 字体 / 代码块") { openSub("pref_ui") } }
    item { WCard("网络", "User-Agent / 超时") { openSub("pref_net") } }
}

private fun LazyListScope.uiPrefRows(s: SettingsStore, d: DialogController) {
    s.rev
    item { WCard("文字对齐", when (s.textAlignMode) { "center" -> "居中"; "right" -> "右对齐"; else -> "左对齐" }) {
        val opts = listOf("左对齐", "居中", "右对齐")
        val ids = listOf("left", "center", "right")
        d.choice("文字对齐", opts, ids.indexOf(s.textAlignMode).coerceAtLeast(0)) { i -> s.textAlignMode = ids[i] }
    } }
    item { WToggle("每行从右往左读", s.textRtl) { s.textRtl = it } }
    item { WToggle("用户和回复换边", s.messageSwap) { s.messageSwap = it } }
    item { WToggle("代码块自动换行", s.codeWrap) { s.codeWrap = it } }
    item {
        val act = LocalContext.current as? android.app.Activity
        WCard("字体大小", "%.2f".format(s.displayScale)) {
            val opts = (0 until 9).map { 0.9f + it * 0.05f }
            d.choice("字体大小", opts.map { "%.2f".format(it) }, opts.indexOf(s.displayScale).coerceAtLeast(0)) { i ->
                s.displayScale = opts[i]; act?.recreate()
            }
        }
    }
}

private fun LazyListScope.themePrefRows(s: SettingsStore, d: DialogController) {
    s.rev
    item {
        val act = LocalContext.current as? android.app.Activity
        WCard("主题", themeName(s.theme)) {
            val ids = listOf("light", "dark", "amoled")
            d.choice("主题", ids.map { themeName(it) }, ids.indexOf(s.theme).coerceAtLeast(0)) { i ->
                s.theme = ids[i]; act?.recreate()
            }
        }
    }
}

private fun LazyListScope.notifyRows(s: SettingsStore, d: DialogController) {
    s.rev
    item {
        val ctx = LocalContext.current
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) s.notifyUpdate = true
        }
        WToggle("更新提醒", s.notifyUpdate) { on ->
            if (!on) s.notifyUpdate = false
            else if (android.os.Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else s.notifyUpdate = true
        }
    }
    item {
        val ctx = LocalContext.current
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) s.notifyReply = true
        }
        WToggle("回复完成时通知", s.notifyReply) { on ->
            if (!on) s.notifyReply = false
            else if (android.os.Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else s.notifyReply = true
        }
    }
    item { WCard("检查间隔(分钟)", s.updateCooldownMin.toString()) {
        d.num("间隔(5-60)", s.updateCooldownMin.toFloat(), 5f, 60f) { s.updateCooldownMin = it.toInt() }
    } }
    item { WCard("说明", "离开应用后回复完成才会通知。通知里只有“已完成”，没有回复内容。") }
}

private fun LazyListScope.generalRows(s: SettingsStore, d: DialogController, openSub: (String) -> Unit) {
    s.rev
    item { WToggle("思考模式", s.thinkingEnabled) { s.thinkingEnabled = it } }
    item { WCard("思考强度", s.reasoningEffort) { val o = listOf("low", "high", "max")
        d.choice("思考强度", o, o.indexOf(s.reasoningEffort).coerceAtLeast(0)) { i -> s.reasoningEffort = o[i] } } }
    item { WCard("温度", s.temperature.toString()) { d.num("温度(0-2)", s.temperature, 0f, 2f) { s.temperature = it } } }
    item { WCard("Top P", s.topP.toString()) { d.num("Top P(0-1)", s.topP, 0f, 1f) { s.topP = it } } }
    item { WCard("最大输出 tokens", if (s.maxTokens <= 0) "不限制" else s.maxTokens.toString()) {
        d.num("max tokens(0=不限制)", s.maxTokens.toFloat(), 0f, 65536f) { s.maxTokens = it.toInt() } } }
    item { WCard("上下文窗口", s.contextWindow.toString()) { d.num("上下文窗口", s.contextWindow.toFloat(), 10000f, 2000000f) { s.contextWindow = it.toInt() } } }
    item { WCard("压缩阈值 %", "${s.compressThreshold}%") { d.num("压缩阈值(50-95)", s.compressThreshold.toFloat(), 50f, 95f) { s.compressThreshold = it.toInt() } } }
    item { WToggle("自动生成会话标题", s.autoTitle) { s.autoTitle = it } }
    item { WCard("输入设置", "快捷输入") { openSub("quick") } }
}

private fun LazyListScope.netRows(s: SettingsStore, d: DialogController) {
    s.rev
    item { WCard("User-Agent", s.userAgent.ifBlank { "WristChat" }) {
        d.input("User-Agent", s.userAgent) { s.userAgent = it }
    } }
    item { WCard("请求超时(秒)", s.netTimeoutSec.toString()) {
        d.num("超时(20-180)", s.netTimeoutSec.toFloat(), 20f, 180f) { s.netTimeoutSec = it.toInt() }
    } }
}

private fun LazyListScope.searchRows(s: SettingsStore, d: DialogController) {
    s.rev
    item { WToggle("联网搜索", s.webSearch) { s.webSearch = it } }
    item { WToggle("阅读消息里的链接", s.webRead) { s.webRead = it } }
    item {
        val flash = s.model == "deepseek-flash"
        WCard("聊天模型", if (flash) "deepseek-flash" else "${s.model} · 搜索要用 deepseek-flash") {
            if (!flash) s.model = "deepseek-flash"
        }
    }
    item { WCard("说明", "搜索或阅读开着时，这一轮用 deepseek-flash，并在 tools 里声明 deepseek_web_search / deepseek_read_url。模型自己决定要不要搜。它报“工具列表里没有搜索”是因为这是服务端工具，不是函数。真正搜的时候，回复上方会写出搜索内容。") }
}

/** 默认模型与提示词:两个入口 */
private fun LazyListScope.modelsPromptsRows(s: SettingsStore, d: DialogController, openSub: (String) -> Unit) {
    item { WCard("默认模型", "聊天 / 快速 / 标题 / 翻译 / OCR / 压缩") { openSub("mp_models") } }
    item { WCard("提示词", "翻译 / 标题 / OCR / 压缩(可改,可恢复默认)") { openSub("mp_prompts") } }
    item { WCard("说明", "每类任务各用一个模型;提示词里的 {变量} 会被自动替换") }
}

/** 全局模型设置:一次把六个任务模型都设为同一个 */
private fun LazyListScope.globalModelRow(s: SettingsStore, d: DialogController) {
    item { WCard("全局模型设置", "把下面 6 个模型全部设为同一个") {
        val ms = Providers.resolve(s).models.ifEmpty { listOf("deepseek-flash") }
        d.choice("全局模型", ms, ms.indexOf(s.model).coerceAtLeast(0)) { i ->
            val m = ms[i]
            s.model = m; s.modelQuick = m; s.modelTitle = m
            s.modelTranslate = m; s.modelOcr = m; s.modelCompress = m
        }
    } }
}

/** 六个任务模型 */
private fun LazyListScope.defaultModelRows(s: SettingsStore, d: DialogController) {
    fun choose(title: String, cur: String, apply: (String) -> Unit) {
        val ms = Providers.resolve(s).models.ifEmpty { listOf("deepseek-flash") }
        d.choice(title, ms, ms.indexOf(cur).coerceAtLeast(0)) { i -> apply(ms[i]) }
    }
    globalModelRow(s, d)
    item { WCard("聊天模型", s.model) { choose("聊天模型", s.model) { s.model = it } } }
    item { WCard("快速模型", s.modelQuick) { choose("快速模型", s.modelQuick) { s.modelQuick = it } } }
    item { WCard("标题总结模型", s.modelTitle) { choose("标题总结模型", s.modelTitle) { s.modelTitle = it } } }
    item { WCard("翻译模型", s.modelTranslate) { choose("翻译模型", s.modelTranslate) { s.modelTranslate = it } } }
    item { WCard("OCR 模型", s.modelOcr) { choose("OCR 模型", s.modelOcr) { s.modelOcr = it } } }
    item { WCard("压缩模型", s.modelCompress) { choose("压缩模型", s.modelCompress) { s.modelCompress = it } } }
}

/** 四段提示词 */
private fun LazyListScope.promptRows(s: SettingsStore, d: DialogController) {
    item { WCard("翻译提示词", "变量:{source_text} {target_lang}") { d.input("翻译提示词", s.promptTranslate, ml = true) { s.promptTranslate = it } } }
    item { WCard("标题生成提示词", "变量:{content} {locale}") { d.input("标题生成提示词", s.promptTitle, ml = true) { s.promptTitle = it } } }
    item { WCard("OCR 识别提示词", "变量:{images}") { d.input("OCR 识别提示词", s.promptOcr, ml = true) { s.promptOcr = it } } }
    item { WCard("上下文压缩提示词", "变量:{content} {target_tokens} {additional_context} {locale}") { d.input("上下文压缩提示词", s.promptCompress, ml = true) { s.promptCompress = it } } }
    item { WCard("恢复默认提示词", "全部还原为内置默认文本") {
        d.confirm("恢复默认", "四段提示词都会恢复为内置默认,确定?", ok = "恢复") { s.resetPrompts() }
    } }
}
private fun LazyListScope.serviceRows(s: SettingsStore, d: DialogController) {
    s.rev
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

private fun LazyListScope.assistantRows(s: SettingsStore, vm: SettingsViewModel, d: DialogController) {
    s.rev
    item { WCard("系统提示词", if (s.systemPrompt.isBlank()) "未设置" else "已设置") {
        d.input("系统提示词", s.systemPrompt, ml = true) { s.systemPrompt = it } } }
    item { WCard("自定义提示词", if (s.customPrompt.isBlank()) "未设置" else "已设置") {
        d.input("自定义提示词", s.customPrompt, ml = true) { s.customPrompt = it } } }
    item { WToggle("自动记忆", s.memoryAuto) { s.memoryAuto = it } }
    item { val mems = vm.memoryStore.list()
        WCard("记忆(${mems.size})", if (mems.isEmpty()) "(空)" else "点击删除") {
            if (mems.isEmpty()) { d.text("记忆", "暂无记忆"); return@WCard }
            d.items("记忆(点击删除)", mems.map { "${it.id}: ${it.content.take(30)}" }, { i -> vm.deleteMemory(mems[i].id) },
                neutral = "清空全部") { vm.clearMemories() }
        }
    }
}

private fun LazyListScope.quickRows(s: SettingsStore, d: DialogController) {
    s.rev
    item { WCard("＋ 添加快捷输入", "单行,上限 20") { quickEditDialog(s, d, null, null) } }
    // 以设置版本号为 key:增删改后立刻刷新(原来要退出再进才看到)
    val quick = s.getQuickInputs()
    quick.forEachIndexed { i, q ->
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
    item { val briefs = vm.sessionStore.briefs()
        WCard("会话列表", "${briefs.size} 个 · ${vm.sessionsSize.value}") {
            if (briefs.isEmpty()) { d.text("会话", "暂无会话"); return@WCard }
            d.items("会话(点击删除)", briefs.map { "${it.title.take(12)} · ${it.msgCount}条" }, { i -> vm.deleteSession(briefs[i].id) })
        }
    }
    item { val scope = rememberCoroutineScope()
        WCard("扫描导入文件夹", "filesDir/import/ 备用") {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val r = vm.importFromFolder()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    d.text("扫描导入", if (r.isEmpty()) "未发现可导入文件" else "已导入:${r.joinToString()}")
                }
            }
        }
    }
    item { WCard("粘贴导入(手表端)", "") {
        d.input("粘贴聊天 JSON/文本", "", ml = true, ok = "导入") { t ->
            d.text("导入结果", if (t.isBlank()) "内容为空" else vm.importFromText(t, "paste.json"))
        }
    } }
}

private fun LazyListScope.backupRows(s: SettingsStore, vm: SettingsViewModel, d: DialogController) {
    item {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        WCard("导出会话", "只含会话，不含密钥") {
            scope.launch(Dispatchers.IO) {
                val text = vm.sessionStore.exportBody()
                val r = exportText(ctx, "sessions.json", text)
                kotlinx.coroutines.withContext(Dispatchers.Main) { d.text("导出", r) }
            }
        }
    }
    sessionRows(s, vm, d)
}

private fun LazyListScope.storageRows(s: SettingsStore, vm: SettingsViewModel, d: DialogController) {
    item { WCard("缓存详情", vm.cacheInfo.value ?: "") { vm.refreshSizes() } }
    // 文件删除移出主线程,避免 ANR(H10)
    item { val scope = rememberCoroutineScope()
        WCard("清理缓存", "只清临时文件;保留日志与更新包") {
            d.confirm("清理缓存", "删除临时文件,不影响会话和设置") {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val freed = vm.clearCache()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { d.text("结果", "已释放 ${vm.human(freed)}") }
                }
            }
        }
    }
    item { val scope = rememberCoroutineScope()
        WCard("清理全部数据", "会话/记忆/技能/设置") {
            d.confirm("清理全部数据", "将删除所有会话、记忆、技能和设置(不可恢复)", countdown = 5) {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val freed = vm.clearAllData()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { d.text("结果", "已释放 ${vm.human(freed)}") }
                }
            }
        }
    }
}

private fun LazyListScope.updateRows(s: SettingsStore, d: DialogController) {
    item {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var prog by remember { mutableStateOf<Int?>(null) }                  // null=无弹窗; -1=进行中
        var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        var pendingApk by remember { mutableStateOf<java.io.File?>(null) }    // 等授权后自动安装
        var resultTitle by remember { mutableStateOf("更新") }
        var progLabel by remember { mutableStateOf<String?>(null) }
        var cachedKey by remember { mutableStateOf(0) }
        var autoInstall by remember { mutableStateOf<java.io.File?>(null) }

        // 授权页返回 → 自动继续安装(不管用户是右滑返回还是返回键)
        val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) {
            val f = pendingApk
            pendingApk = null
            if (f != null) {
                if (io.github.xhr666.wristchat.data.Installer.canInstall(ctx)) autoInstall = f
                else d.text("需要授权", "仍未允许安装未知应用;开启后再点一次“安装已下载的包”")
            }
        }

        fun launchInstall(file: java.io.File) {
            progLabel = "正在打开系统安装器…"
            prog = -1
            job = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val out = try { doInstall(ctx, file) } catch (e: Exception) {
                    InstallOutcome.Failed(e.message ?: "安装异常")
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    prog = null; progLabel = null
                    when (out) {
                        is InstallOutcome.Done -> { cachedKey++; d.text(resultTitle, out.text) }
                        is InstallOutcome.Failed -> d.text("安装失败", out.text)
                        is InstallOutcome.NeedPermission -> {
                            // 没权限:记下来,等授权页返回后自动继续
                            pendingApk = out.apk
                            d.confirm("需要授权",
                                "安装需要允许“安装未知应用”。\n点“去设置”开启后返回,会自动打开系统安装器。",
                                ok = "去设置") {
                                try {
                                    permLauncher.launch(android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        android.net.Uri.parse("package:${ctx.packageName}")))
                                } catch (e: Exception) { d.text("打不开设置", e.message ?: "") }
                            }
                        }
                    }
                }
            }
        }

        // 需要安装时统一从这里发起(授权返回后也会走这里)
        LaunchedEffect(autoInstall) {
            val f = autoInstall ?: return@LaunchedEffect
            autoInstall = null
            launchInstall(f)
        }

        // 双保险:授权页返回时如果没收到 ActivityResult(例如从最近任务返回),
        // 轮询检测到已授权也自动安装一次,不需要用户再点
        LaunchedEffect(pendingApk) {
            val f = pendingApk ?: return@LaunchedEffect
            repeat(150) {
                kotlinx.coroutines.delay(800)
                if (io.github.xhr666.wristchat.data.Installer.canInstall(ctx)) {
                    pendingApk = null
                    autoInstall = f
                    return@LaunchedEffect
                }
            }
        }

        WCard("版本 ${s.versionName}", "检查更新") {
            val repo = UpdateRepository(s)
            io.github.xhr666.wristchat.data.AppLog.i("upd", "check start v=${s.versionName}")
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val r = repo.check()
                    s.lastUpdateCheck = System.currentTimeMillis()
                    io.github.xhr666.wristchat.data.AppLog.i("upd", "check=$r")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        when (r) {
                            is UpdateResult.Found -> {
                                resultTitle = "更新 ${r.info.tagName}"
                                d.confirm("发现新版本 ${r.info.tagName}",
                                    "当前:${s.versionName} → ${r.info.tagName}\n${shortNote(r.info.body)}",
                                    ok = "下载更新") {
                                    io.github.xhr666.wristchat.data.AppLog.i("upd", "user taps download")
                                    progLabel = null
                                    prog = -1
                                    job = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val out = installRelease(ctx, repo, r.info) { done, total ->
                                                scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                                    prog = if (total > 0) (done * 100 / total).toInt() else -1
                                                }
                                            }
                                            io.github.xhr666.wristchat.data.AppLog.i("upd", "outcome: $out")
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                prog = null
                                                when (out) {
                                                    is InstallOutcome.Done -> { cachedKey++; d.text(resultTitle, out.text) }
                                                    is InstallOutcome.Failed -> d.text("更新失败", out.text)
                                                    is InstallOutcome.NeedPermission -> { pendingApk = out.apk; launchInstall(out.apk) }
                                                }
                                            }
                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                            io.github.xhr666.wristchat.data.AppLog.i("upd", "cancelled by user")
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                prog = null
                                                d.text("已取消", "下载已取消。已下载的部分会保留,下次点更新可续传。")
                                            }
                                            throw e
                                        } catch (e: Exception) {
                                            io.github.xhr666.wristchat.data.AppLog.i("upd", "err $e")
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                prog = null; d.text("更新失败", e.message ?: "未知错误")
                                            }
                                        }
                                    }
                                }
                            }
                            is UpdateResult.UpToDate -> d.text("更新", "已是最新(${r.latest})")
                            is UpdateResult.NoApk -> d.text("更新", "发现 ${r.latest},但无 APK 资产")
                            is UpdateResult.Error -> d.text("更新失败", r.message)
                        }
                    }
                } catch (e: Exception) {
                    io.github.xhr666.wristchat.data.AppLog.i("upd", "check err $e")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        d.text("更新失败", e.message ?: "未知错误")
                    }
                }
            }
        }
        // 已下载但没装上的包:补救入口(与上面的安装/授权流程共用)
        val cachedApk = remember(cachedKey) {
            java.io.File(ctx.cacheDir, "updates").listFiles()
                ?.filter { it.isFile && it.name.endsWith(".apk") }
                ?.maxByOrNull { it.lastModified() }
        }
        WCard("安装已下载的包",
            cachedApk?.let { "${it.name} · ${it.length() / 1024 / 1024}MB" } ?: "暂无(先点上面的检查更新)") {
            val f = cachedApk
            if (f == null) { d.text("安装", "没有已下载的安装包"); return@WCard }
            d.confirm("安装", "用系统安装器安装已下载的\n${f.name}?", ok = "安装") { launchInstall(f) }
        }
        prog?.let { p ->
            CompactDialog(
                title = if (progLabel != null) "安装更新" else "下载更新",
                onDismiss = {},
                dismissText = "取消",
                onDismissButton = { job?.cancel() },
            ) {
                Text(progLabel ?: (if (p < 0) "连接中…" else "下载中 $p%"),
                    color = LocalWrist.current.text, fontSize = 13.sp)
            }
        }
    }
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
    item { WCard("免密次数", s.graceDefault.toString()) { d.num("未来 N 次免密(0=每次都输)", s.graceDefault.toFloat(), 0f, 50f) { s.graceDefault = it.toInt(); s.graceLeft = it.toInt() } } }
}

private fun LazyListScope.aboutRows(s: SettingsStore, vm: SettingsViewModel, d: DialogController, openLog: (String) -> Unit) {
    item { WCard("开源许可", "MIT + 第三方库") { d.text("开源许可", licenseText()) } }
    item {
        WCard("会话文件自检", "磁盘上有哪些会话、是否能解析") { openLog("sess") }
        WCard("查看崩溃日志", "crash.log · 闪退堆栈") { openLog("crash") }
        WCard("ANR/卡死日志", "anr.log · 界面卡死的主线程堆栈") { openLog("anr") }
        WCard("请求与运行日志", "只记状态码和工具名，不含对话和密钥") { openLog("run") }
    }
    item { val scope = rememberCoroutineScope()
        val ctx = LocalContext.current
        WCard("导出日志到 Download", "Download/WristChat/ · 数据线可取出") {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val r = exportLogs(ctx.applicationContext)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(ctx, r, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        WCard("日志", "可在本页查看，或导出到 Download/WristChat")
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
- JLatexMath (GPL-2.0,带链接例外)
- CN Wear Overlay (Apache-2.0)
- AndroidX / Jetpack Compose (Apache-2.0)
- kotlinx-coroutines (Apache-2.0)
"""
