package io.github.xhr666.wristchat.ui.screens

import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.xhr666.wristchat.data.Session
import io.github.xhr666.wristchat.data.SettingsStore
import io.github.xhr666.wristchat.ui.*
import io.github.xhr666.wristchat.ui.chat.ChatViewModel
import io.github.xhr666.wristchat.ui.chat.KatexWebView
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(settings: SettingsStore, vm: ChatViewModel) {
    val session by vm.session.observeAsState()
    val sending by vm.sending.observeAsState(false)
    val title by vm.title.observeAsState("")
    val status by vm.status.observeAsState()
    val quickInputs by vm.quickInputs.observeAsState(emptyList())
    var quickPanel by remember { mutableStateOf(false) }
    var fullscreenInput by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var newSessionAsk by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val currentPage = LocalCurrentPage.current
    val c = LocalWrist.current

    val messages = session?.messages ?: emptyList()
    val draft by vm.draft.observeAsState("")
    // Markwon 全屏共享一份,避免每条消息各自初始化插件链(内存/耗时)
    val screenCtx = LocalContext.current
    val markwon = remember { Markwon.create(screenCtx) }
    // 自动滚到底(以最后一条消息时间戳为 key:压缩/清空/新增都可靠触发)
    LaunchedEffect(messages.lastOrNull()?.ts, messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    WToast(status)

    if (fullscreenInput) {
        FullscreenInputOverlay(vm, onClose = { fullscreenInput = false })
        return
    }

    settings.rev   // 订阅设置变化(模型名等)
    ScreenScaffold(
        title = title.ifBlank { settings.model }.let { if (it == "新会话") settings.model else it },
        showTimeAlways = true,
        scrollIndicator = listState,
        actions = {
            SmallAction("ⓘ") { showDetails = true }
            SmallAction("⌨") { quickPanel = !quickPanel }
            SmallAction("⟳") { newSessionAsk = true }
        },
    ) {
        Column(Modifier.fillMaxSize().padding(bottom = 10.dp)) {
            if (quickPanel) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(c.surface)
                        .border(1.dp, c.border)
                        .padding(vertical = 4.dp),
                ) {
                    quickInputs.take(6).forEach { q ->
                        Text(q, color = c.text, fontSize = 13.sp, maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.useQuickInput(q); quickPanel = false }
                                .padding(horizontal = 14.dp, vertical = 9.dp))
                    }
                    if (quickInputs.isEmpty()) Text("暂无快捷输入(设置里添加)", color = c.hint, fontSize = 12.sp, modifier = Modifier.padding(14.dp))
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = LocalRoundBottom.current),
            ) {
                itemsIndexed(messages, key = { i, m -> "$i-${m.ts}" }) { _, m ->
                    MessageItem(m, markwon) { text ->
                        // 翻译:用翻译模型 + 翻译提示词(变量自动替换)
                        val lang = java.util.Locale.getDefault().displayLanguage
                        val p = vm.settings.promptTranslate
                            .replace("{target_lang}", lang)
                            .replace("{source_text}", text)
                        vm.send(p, emptyList(), modelOverride = vm.settings.modelTranslate)
                    }
                }
                if (sending) item { Text("思考中…", color = c.hint, fontSize = 12.sp) }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp)
                    .background(c.surface)
                    .border(1.dp, c.border)
                    .clickable { fullscreenInput = true }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(draft.ifEmpty { "输入消息…" },
                    color = if (draft.isBlank()) c.hint else c.text,
                    fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Text("➤", color = c.accent, fontSize = 18.sp)
            }
        }
        RotaryList(listState, enabled = currentPage == 1)
    }

    // 只在打开详情时才刷新一次,不再在组合期间调用
    LaunchedEffect(showDetails) { if (showDetails) vm.refreshDetails() }
    val details by vm.details.observeAsState()
    if (showDetails && details != null) {
        WConfirm("对话详情", detailsText(details!!), okText = "关闭", cancelText = "",
            onOk = { showDetails = false }, onCancel = { showDetails = false })
    }
    if (newSessionAsk) {
        WConfirm("新会话", "开始新会话?当前对话保留在历史中", okText = "确定",
            onOk = { vm.newSession(); newSessionAsk = false }, onCancel = { newSessionAsk = false })
    }
}

private fun detailsText(d: io.github.xhr666.wristchat.ui.chat.ConvDetails): String = buildString {
    append("消息数:${d.messageCount}\n")
    append("请求次数:${d.requests}\n")
    append("总 tokens:${d.totalTokens}\n")
    append("缓存命中率:${"%.1f".format(d.cacheHitRate * 100)}%\n")
    append("当前上下文:${d.contextTokens} / ${d.contextWindow}\n")
    append("消耗余额:¥${"%.4f".format(d.totalCost)}\n")
    append("已压缩消息:${d.compressed}")
}

/** 消息气泡 */
@Composable
fun MessageItem(m: io.github.xhr666.wristchat.data.ChatMessage, markwon: Markwon, onTranslate: ((String) -> Unit)? = null) {
    val c = LocalWrist.current
    var expanded by remember { mutableStateOf(false) }
    val isUser = m.role == "user"
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (!isUser && m.reasoning.isNotBlank()) {
            Text(if (expanded) "💭 思考过程 ▾" else "💭 思考过程 ▸",
                color = c.hint, fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.surface)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 8.dp, vertical = 3.dp))
            if (expanded) {
                Text(m.reasoning, color = c.hint, fontSize = 12.sp, modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.surface)
                    .padding(8.dp))
            }
        }
        Box(
            Modifier
                .widthIn(max = 175.dp)
                .padding(top = 3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isUser) c.bubbleUser else c.bubbleAi)
                .padding(10.dp),
        ) {
            Column {
                m.imgs.firstOrNull()?.let { MsgImage(it) }
                if (m.imgs.size > 1) {
                    Text("共 ${m.imgs.size} 张图片", color = LocalWrist.current.hint, fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 2.dp))
                }
                if (m.content.isNotBlank() || m.imgs.isEmpty()) MsgContent(m.content, isUser, markwon)
            }
        }
        if (!isUser) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (m.cost > 0 || (m.usage?.totalTokens ?: 0) > 0) {
                    Text("tokens:${m.usage?.totalTokens ?: 0} · ¥%.4f".format(m.cost), color = c.hint, fontSize = 9.sp)
                }
                val ctx = LocalContext.current
                if (onTranslate != null && m.content.isNotBlank()) {
                    Text("  翻译", color = c.accent, fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onTranslate(m.content) }
                            .padding(horizontal = 4.dp, vertical = 2.dp))
                }
                Text("  复制", color = c.accent, fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("reply", m.content))
                            android.widget.Toast.makeText(ctx, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp))
            }
        }
    }
}

/** 消息内图片缩略图(IO 解码,不卡主线程) */
@Composable
private fun MsgImage(name: String) {
    val ctx = LocalContext.current
    val bmp by produceState<android.graphics.Bitmap?>(null, name) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            io.github.xhr666.wristchat.data.Attachments.loadThumb(ctx, name)
        }
    }
    bmp?.let { b ->
        Box(Modifier.fillMaxWidth().heightIn(max = 150.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "图片",
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun MsgContent(text: String, isUser: Boolean, markwon: Markwon) {
    val ctx = LocalContext.current
    val c = LocalWrist.current
    if (text.contains("$")) {
        // 数学/含公式:WebView(marked + KaTeX)
        AndroidView(
            factory = { KatexWebView(it).apply { layoutParams = android.view.ViewGroup.LayoutParams(
                (175 * ctx.resources.displayMetrics.density).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT) } },
            update = { it.render(text) },
        )
    } else {
        AndroidView(
            factory = { ctx2 ->
                TextView(ctx2).apply {
                    setTextColor(c.text.toArgbCompat())
                    textSize = 14f
                }
            },
            update = { tv -> tv.text = markwon.toMarkdown(text) },
        )
    }
}

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

/**
 * 全屏输入页:改用 View 体系的原生 EditText(AndroidView)。
 * 参考应用(词典 / Via / QQ)的输入法表现正常,因为它们用的是 View 的 EditText,
 * 而 Compose 的 TextField 在这台表的输入法上会出现"删除后候选/预览不同步"的问题。
 * 同时支持:实时保存草稿、系统剪贴板粘贴、图片附件。
 */
@Composable
fun FullscreenInputOverlay(vm: ChatViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalWrist.current
    var attachNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var attachThumb by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var visionAsk by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf<android.widget.EditText?>(null) }
    SideEffect { PagerLock.locked = true }
    DisposableEffect(Unit) { onDispose { PagerLock.locked = false } }

    fun body(): String = edit?.text?.toString() ?: vm.draft.value.orEmpty()
    fun closeWithSave() { vm.setDraft(body()); vm.saveDraftNow(); onClose() }

    // 图片:优先用内置相册(集成在软件里,不调用系统选择器)
    var galleryOpen by remember { mutableStateOf(false) }

    if (galleryOpen) {
        GalleryScreen(
            onPickMany = { picked ->
                galleryOpen = false
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val sid = vm.currentSessionId() ?: "s"
                    val added = picked.mapNotNull { item ->
                        if (item.file != null) io.github.xhr666.wristchat.data.Attachments.importFromFile(ctx, item.file, sid)
                        else item.uri?.let { io.github.xhr666.wristchat.data.Attachments.importFromUri(ctx, it, sid) }
                    }
                    val thumb = added.firstOrNull()?.let { io.github.xhr666.wristchat.data.Attachments.loadThumb(ctx, it) }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (added.isEmpty()) hint = "图片读取失败"
                        else { attachNames = attachNames + added; attachThumb = thumb }
                    }
                }
            },
            onBack = { galleryOpen = false },
        )
        return
    }

    fun launchGallery() {
        if (!vm.providerIsDeepSeek()) { hint = "图片仅 DeepSeek 支持(需 deepseek-flash)"; return }
        if (!vm.modelSupportsVision()) { visionAsk = true; return }
        hint = null
        galleryOpen = true
    }

    // 用独立 Dialog 窗口承载:键盘弹出不会把按钮挤出屏幕,也不受 Pager 影响(不会划出空白页)
    val dialogView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { closeWithSave() },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
    SideEffect {
        // 关掉系统窗口动画(切换显示大小后弹窗会从角落滑入的 bug)
        (dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.apply {
            setWindowAnimations(0)
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallAction("‹") { closeWithSave() }
            Text(if (attachNames.isNotEmpty()) "发送图片" else "输入消息", color = c.text, fontSize = 14.sp,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            SmallAction("📋") {
                // 粘贴:读系统剪贴板
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val txt = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                if (txt.isBlank()) { hint = "剪贴板是空的" } else {
                    val e = edit
                    if (e != null) {
                        val s0 = e.selectionStart.coerceAtLeast(0)
                        e.text.replace(s0, e.selectionEnd.coerceAtLeast(s0), txt)
                        e.setSelection((s0 + txt.length).coerceAtMost(e.text.length))
                    }
                    hint = null
                }
            }
            SmallAction("🖼") { launchGallery() }
            if (attachNames.isNotEmpty()) {
                SmallAction("OCR") {
                    val p = vm.settings.promptOcr.replace("{images}", "(见附图)")
                    vm.setDraft(p)
                    vm.send(p, attachNames, modelOverride = vm.settings.modelOcr)
                    vm.clearDraft()
                    onClose()
                }
            }
            SmallAction("➤") {
                val t = body()
                vm.setDraft(t)
                // send() 成功时内部会清草稿;失败(空内容/上一条还在发)时保留草稿,不能丢字
                val accepted = vm.send(t, attachNames)
                if (accepted) vm.clearDraft()
                else vm.saveDraftNow()
                onClose()
            }
        }
        attachThumb?.let { bmp ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.size(38.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                )
                // 多张只显示数量,不堆叠
                Text("已选 ${attachNames.size} 张图片(JPEG 压缩后上传)", color = c.hint, fontSize = 11.sp,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                SmallAction("✕") { attachNames = emptyList(); attachThumb = null }
            }
        }
        hint?.let { Text(it, color = Color(0xFFFFB4A9), fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { cx ->
                android.widget.EditText(cx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                        android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN or
                        android.view.inputmethod.EditorInfo.IME_ACTION_NONE
                    setText(vm.draft.value.orEmpty())
                    setSelection(text.length)
                    textSize = 16f
                    setTextColor(c.text.toArgbCompat())
                    setHintTextColor(c.hint.toArgbCompat())
                    hint = "在此输入…"
                    gravity = android.view.Gravity.TOP or android.view.Gravity.START
                    background = null
                    setPadding(8, 4, 8, 4)
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                        override fun afterTextChanged(s: android.text.Editable?) { vm.setDraft(s?.toString().orEmpty()) }
                    })
                    edit = this
                    requestFocus()
                }
            },
        )
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        edit?.requestFocus()
        val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(edit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    if (visionAsk) {
        WConfirm("需要视觉模型", "图片需要 deepseek-flash(旧名 v4-flash / vision-exp 会自动路由)。\n切换当前对话模型?",
            okText = "切换", onOk = {
                vm.switchToVisionModel()
                visionAsk = false
                galleryOpen = true
            }, onCancel = { visionAsk = false })
    }
}
}
