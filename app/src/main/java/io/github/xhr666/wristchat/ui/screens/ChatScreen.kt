package io.github.xhr666.wristchat.ui.screens

import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.xhr666.wristchat.data.ThinkLabel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import io.github.xhr666.wristchat.ui.chat.RichText
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(settings: SettingsStore, vm: ChatViewModel) {
    val session by vm.session.observeAsState()
    val title by vm.title.observeAsState("")
    val status by vm.status.observeAsState()
    val quickInputs by vm.quickInputs.observeAsState(emptyList())
    var quickPanel by remember { mutableStateOf(false) }
    // 设置里改了快捷输入 → 聊天页立刻刷新(原来要重启应用)
    LaunchedEffect(settings.rev) { vm.refreshQuickInputs() }
    var fullscreenInput by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var newSessionAsk by remember { mutableStateOf(false) }
    val currentPage = LocalCurrentPage.current
    val c = LocalWrist.current

    val frame by vm.frame.observeAsState(0)
    val streamingTs by vm.streamingTs.observeAsState(0L)
    val thinkStart by vm.thinkStartMs.observeAsState(0L)
    val messages = session?.messages ?: emptyList()
    val draft by vm.draft.observeAsState("")
    // Markwon 全屏共享一份,避免每条消息各自初始化插件链(内存/耗时)
    val screenCtx = LocalContext.current
    val markwon = remember { Markwon.create(screenCtx) }
    // 列表倒序:第 0 项就是最后一条,打开时已经在底部,不会从顶上滚下来。
    val sessionId = session?.id
    val listState = remember(sessionId) { androidx.compose.foundation.lazy.LazyListState(0, 0) }
    var stickToEnd by remember(sessionId) { mutableStateOf(true) }
    LaunchedEffect(sessionId, listState) {
        androidx.compose.runtime.snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling && listState.firstVisibleItemIndex > 0) stickToEnd = false
                if (!scrolling && listState.firstVisibleItemIndex == 0) stickToEnd = true
            }
    }
    LaunchedEffect(sessionId, messages.size, frame) {
        if (stickToEnd && messages.isNotEmpty()) listState.scrollToItem(0)
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
        scrollFromEnd = true,
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
                        // 限高 + 可滚动:快捷输入再多也不会占满屏幕
                        .heightIn(max = 150.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(vertical = 4.dp),
                ) {
                    quickInputs.forEach { q ->
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
                reverseLayout = true,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = LocalRoundBottom.current),
            ) {
                itemsIndexed(messages.asReversed(), key = { _, m -> "${m.role}-${m.ts}" }) { _, m ->
                    MessageItem(
                        m, markwon, settings, listState,
                        streaming = m.ts == streamingTs && streamingTs != 0L,
                        thinkStartMs = thinkStart,
                    )
                }
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
        RotaryList(listState, enabled = currentPage == 1, sign = -1)
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
fun MessageItem(
    m: io.github.xhr666.wristchat.data.ChatMessage,
    markwon: Markwon,
    settings: SettingsStore,
    listState: androidx.compose.foundation.lazy.LazyListState,
    streaming: Boolean = false,
    thinkStartMs: Long = 0L,
) {
    val c = LocalWrist.current
    val isUser = m.role == "user"
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        // 左右互换:开启后用户消息在左、智能体在右
        horizontalAlignment = if (isUser != settings.messageSwap) Alignment.End else Alignment.Start,
    ) {
        if (!isUser && m.reasoning.isNotBlank()) {
            ThinkBlock(m, streaming, thinkStartMs, listState)
        }
        if (!isUser && m.toolNote.isNotBlank()) {
            Text(m.toolNote, color = c.hint, fontSize = 11.sp, lineHeight = 14.sp,
                modifier = Modifier.padding(bottom = 2.dp).widthIn(max = 200.dp))
        }
        Box(
            Modifier
                .widthIn(max = if (isUser) 172.dp else 200.dp)
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
                if (m.content.isBlank() && m.reasoning.isBlank() && !isUser) {
                    Text("思考中…", color = c.hint, fontSize = 12.sp)
                } else if (m.content.isNotBlank() || m.imgs.isEmpty()) {
                    MsgContent(m.content, isUser, markwon, settings)
                }
            }
        }
        if (!isUser) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (m.cost > 0 || (m.usage?.totalTokens ?: 0) > 0) {
                    Text("tokens:${m.usage?.totalTokens ?: 0} · ¥%.4f".format(m.cost), color = c.hint, fontSize = 9.sp)
                }
                val ctx = LocalContext.current
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
            if (m.citations.isNotBlank()) {
                Text(m.citations, color = c.hint, fontSize = 9.sp, lineHeight = 12.sp,
                    modifier = Modifier.padding(top = 2.dp).widthIn(max = 200.dp))
            }
        }
    }
}

@Composable
private fun ThinkBlock(
    m: io.github.xhr666.wristchat.data.ChatMessage,
    streaming: Boolean,
    thinkStartMs: Long,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    val c = LocalWrist.current
    var open by remember(m.ts) { mutableStateOf(false) }
    var wide by remember(m.ts) { mutableStateOf(false) }
    var follow by remember(m.ts) { mutableStateOf(true) }
    val scroll = androidx.compose.foundation.rememberScrollState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(streaming) {
        while (streaming) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(500)
        }
    }
    val seconds = if (streaming && thinkStartMs > 0L) {
        ((now - thinkStartMs).coerceAtLeast(0L)) / 1000L
    } else m.reasoningMs / 1000L
    val scope = rememberCoroutineScope()
    val gate = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && scroll.value < scroll.maxValue - 8) follow = false
                return available
            }
        }
    }
    LaunchedEffect(scroll) {
        androidx.compose.runtime.snapshotFlow { scroll.value to scroll.maxValue }.collect { (v, max) ->
            if (!follow && max > 0 && v >= max - 4) follow = true
        }
    }
    LaunchedEffect(m.reasoning, follow, streaming, wide) {
        if (streaming && follow) scroll.scrollTo(scroll.maxValue)
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (streaming) ThinkLabel.streaming(seconds) else ThinkLabel.done(seconds),
                color = c.hint, fontSize = 11.sp, modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                if (streaming) "↕" else if (open) "▾" else "▸",
                color = c.accent, fontSize = 14.sp,
                modifier = Modifier
                    .clickable {
                        if (streaming) {
                            wide = !wide
                            if (wide) follow = true
                        } else {
                            val index = listState.firstVisibleItemIndex
                            val offset = listState.firstVisibleItemScrollOffset
                            open = !open
                            scope.launch {
                                androidx.compose.runtime.withFrameNanos { }
                                androidx.compose.runtime.withFrameNanos { }
                                val size = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == index }?.size ?: 0
                                listState.scrollToItem(index, ScrollAnchor.clamp(offset, size))
                            }
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (streaming || open) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (streaming) Modifier.height(if (wide) 200.dp else 72.dp) else Modifier.heightIn(max = 160.dp))
                    .nestedScroll(gate)
                    .verticalScroll(scroll),
            ) {
                Text(m.reasoning, color = c.hint, fontSize = 12.sp, lineHeight = 16.sp,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
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
fun MsgContent(text: String, isUser: Boolean, markwon: Markwon, settings: SettingsStore) {
    val c = LocalWrist.current
    val shown = if (!isUser && io.github.xhr666.wristchat.ui.chat.TextTransform.shouldReverse(settings.textRtl, text) &&
        !io.github.xhr666.wristchat.ui.chat.Latex.hasMath(text)) {
        io.github.xhr666.wristchat.ui.chat.TextTransform.reversePerLine(text)
    } else text
    val color = c.text.toArgbCompat()
    AndroidView(
        factory = { ctx2 ->
            TextView(ctx2).apply {
                setTextColor(color)
                textSize = 14f
                setHorizontallyScrolling(!settings.codeWrap)
                textAlignment = when (settings.textAlignMode) {
                    "center" -> android.view.View.TEXT_ALIGNMENT_CENTER
                    "right" -> android.view.View.TEXT_ALIGNMENT_VIEW_END
                    else -> android.view.View.TEXT_ALIGNMENT_VIEW_START
                }
                if (settings.textRtl && !io.github.xhr666.wristchat.ui.chat.Latex.hasMath(text)) {
                    textDirection = android.view.View.TEXT_DIRECTION_RTL
                    gravity = android.view.Gravity.END
                }
            }
        },
        update = { tv ->
            tv.setHorizontallyScrolling(!settings.codeWrap)
            RichText.bind(tv, markwon, shown, color)
        },
    )
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
            setDimAmount(0.75f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }
    Box(Modifier.fillMaxSize().background(c.bg)) {
    // 底层:吃掉落在空白处的触摸,避免手势穿透到下层页面(否则会闪出空白页的黑块)。
    // 必须放在内容"下面",否则会挡住工具栏按钮与输入框。
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } },
    )
    // 圆屏:工具栏下移并按其垂直中心位置算出左右内缩,否则贴角的按钮会落在圆外(看不到)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    val w = maxWidth
    val h = maxHeight
    val topPad = 32.dp
    val barH = 46.dp
    val barInset = roundInset(w, h, topPad + barH / 2)
    val editorInset = maxOf(roundInset(w, h, topPad + barH + 18.dp), 16.dp)
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = topPad, bottom = 6.dp),
    ) {
        // 工具栏固定高度:键盘再高也先保证返回/发送/粘贴/图片这几个键在屏幕上
        Row(
            Modifier
                .fillMaxWidth()
                .height(barH)
                .padding(horizontal = barInset + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.size(38.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                )
                // 多张只显示数量,不堆叠
                Text("已选 ${attachNames.size} 张图片", color = c.hint, fontSize = 11.sp,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
                Text("OCR", color = c.accent, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                        val t = vm.settings.promptOcr
                        vm.setDraft(t)
                        vm.send(t, attachNames, modelOverride = vm.settings.modelOcr)
                        vm.clearDraft()
                        onClose()
                    }.padding(horizontal = 6.dp, vertical = 2.dp))
                SmallAction("✕") { attachNames = emptyList(); attachThumb = null }
            }
        }
        hint?.let { Text(it, color = Color(0xFFFFB4A9), fontSize = 11.sp, maxLines = 1) }
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = editorInset + 8.dp),
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
                    val d = cx.resources.displayMetrics.density
                    setPadding((18 * d).toInt(), (10 * d).toInt(), (18 * d).toInt(), (8 * d).toInt())
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
}
