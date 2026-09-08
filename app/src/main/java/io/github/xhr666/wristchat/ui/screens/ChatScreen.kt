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
    // 自动滚到底(以最后一条消息时间戳为 key:压缩/清空/新增都可靠触发)
    LaunchedEffect(messages.lastOrNull()?.ts, messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    WToast(status)

    if (fullscreenInput) {
        FullscreenInputOverlay(vm, onClose = { fullscreenInput = false })
        return
    }

    ScreenScaffold(
        title = title.ifBlank { settings.model }.let { if (it == "新会话") settings.model else it },
        showTimeAlways = true,
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
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
            ) {
                itemsIndexed(messages) { i, m -> MessageItem(m) }
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
fun MessageItem(m: io.github.xhr666.wristchat.data.ChatMessage) {
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
                m.img?.let { MsgImage(it) }
                if (m.content.isNotBlank() || m.img == null) MsgContent(m.content, isUser)
            }
        }
        if (!isUser && (m.cost > 0 || (m.usage?.totalTokens ?: 0) > 0)) {
            Text("tokens:${m.usage?.totalTokens ?: 0} · ¥%.4f".format(m.cost), color = c.hint, fontSize = 9.sp)
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
fun MsgContent(text: String, isUser: Boolean) {
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
        val markwon = remember { Markwon.create(ctx) }
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

/** 全屏输入页:自动唤输入法,关闭不发送 */
@Composable
fun FullscreenInputOverlay(vm: ChatViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalWrist.current
    var text by remember { mutableStateOf(vm.draft.value ?: "") }
    var attachName by remember { mutableStateOf<String?>(null) }
    var attachThumb by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var visionAsk by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    // 组合期不写全局状态:SideEffect 提交后再锁定,退出时 DisposableEffect 解锁
    SideEffect { PagerLock.locked = true }
    DisposableEffect(Unit) { onDispose { PagerLock.locked = false } }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    // 图片选择:系统相册/文件选择器;无可用选择器时给提示
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            hint = null
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val name = io.github.xhr666.wristchat.data.Attachments
                    .importFromUri(ctx, uri, vm.currentSessionId() ?: "s")
                val thumb = name?.let { io.github.xhr666.wristchat.data.Attachments.loadThumb(ctx, it) }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (name == null) hint = "图片读取失败或不支持的格式"
                    else { attachName = name; attachThumb = thumb; text = "" }
                }
            }
        }
    }
    fun launchPicker() {
        if (!vm.providerIsDeepSeek()) { hint = "图片仅 DeepSeek 支持(需 deepseek-v4-flash-vision-exp)"; return }
        if (!vm.modelSupportsVision()) { visionAsk = true; return }
        hint = null
        try { picker.launch("image/*") }
        catch (e: Exception) { hint = "没有可用的图片选择器" }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .imePadding()
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallAction("‹") { vm.setDraft(text); keyboard?.hide(); onClose() }
            Text(if (attachName != null) "发送图片" else "输入消息", color = c.text, fontSize = 14.sp,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            SmallAction("🖼") { launchPicker() }
            SmallAction("➤") {
                vm.setDraft(text)
                vm.send(text, attachName)
                keyboard?.hide()
                onClose()
            }
        }
        attachThumb?.let { bmp ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                )
                Text("已选图片(JPEG 压缩后上传)", color = c.hint, fontSize = 11.sp,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                SmallAction("✕") { attachName = null; attachThumb = null }
            }
        }
        hint?.let { Text(it, color = Color(0xFFFFB4A9), fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        TextField(
            value = text, onValueChange = { if (it.length <= 8000) text = it },
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = c.text),
            placeholder = { Text(if (attachName != null) "补充说明(可留空)…" else "在此输入…", color = c.hint) },
        )
    }

    if (visionAsk) {
        WConfirm("需要视觉模型", "图片需要 deepseek-v4-flash-vision-exp。\n切换当前对话模型?",
            okText = "切换", onOk = {
                vm.switchToVisionModel()
                visionAsk = false
                try { picker.launch("image/*") } catch (e: Exception) { hint = "没有可用的图片选择器" }
            }, onCancel = { visionAsk = false })
    }
}
