package io.github.xhr666.wristchat.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.material.curvedText
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.sqrt

data class WristColors(
    val bg: Color, val surface: Color, val text: Color, val hint: Color,
    val accent: Color, val bubbleUser: Color, val bubbleAi: Color, val border: Color,
)
val Light = WristColors(Color(0xFFF5F6F8), Color.White, Color(0xFF101418), Color(0xFF8A9199),
    Color(0xFF3D8BFD), Color(0xFFD2E5FF), Color(0xFFEFF1F4), Color(0xFFE0E3E7))
val Dark = WristColors(Color(0xFF101418), Color(0xFF1B2228), Color(0xFFE8EAED), Color(0xFF6F7880),
    Color(0xFF4D9FFF), Color(0xFF1E3A5F), Color(0xFF1B2228), Color(0xFF2A323A))
val Amoled = WristColors(Color.Black, Color(0xFF11161A), Color(0xFFE8EAED), Color(0xFF6F7880),
    Color(0xFF4D9FFF), Color(0xFF14304F), Color(0xFF11161A), Color(0xFF1D242A))
val LocalWrist = staticCompositionLocalOf { Amoled }

/** 全屏覆盖层(分类/同步/输入)打开时锁定横向翻页 */
object PagerLock {
    var locked by mutableStateOf(false)
}

object RotaryBus {
    // 累积式:表冠快速旋转时事件非常密集,原实现用 SharedFlow 缓冲(16)溢出即丢事件,
    // 于是"转得快反而走得慢"。改为累加 + 每帧消费一次,快转不丢步。
    private val acc = java.util.concurrent.atomic.AtomicInteger(0)
    private const val LIMIT = 900
    @Volatile private var lastEventAt = 0L
    fun emit(delta: Int) {
        val v = acc.addAndGet(delta)
        if (v > LIMIT) acc.set(LIMIT) else if (v < -LIMIT) acc.set(-LIMIT)
        lastEventAt = System.currentTimeMillis()
    }
    fun drain(): Int = acc.getAndSet(0)
    /** 表冠是否已停手(用于触发惯性滑行) */
    fun idle(ms: Long = 110): Boolean = System.currentTimeMillis() - lastEventAt > ms
}

@Composable
fun RotaryList(listState: LazyListState, enabled: Boolean) {
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        var velocity = 0f                                  // 平滑后的速度(px/ms)
        var flingJob: kotlinx.coroutines.Job? = null
        while (true) {
            withFrameNanos { }                              // 每帧消费一次累积量
            val d = RotaryBus.drain()
            if (d != 0) {
                flingJob?.cancel(); flingJob = null          // 新的转动立刻接管惯性
                val px = d.coerceIn(-240, 240)
                listState.dispatchRawDelta(px.toFloat())
                velocity = 0.65f * velocity + 0.35f * (px / 16.7f)
            } else if (velocity != 0f && RotaryBus.idle()) {
                // 官方 rotaryScrollable 的做法:松手后按最后速度滑行一段(惯性/滑行)
                val v = velocity
                velocity = 0f
                if (kotlin.math.abs(v) > 0.5f) {
                    val distance = (v * 150f).coerceIn(-1800f, 1800f)
                    flingJob = launch {
                        runCatching {
                            listState.animateScrollBy(distance,
                                androidx.compose.animation.core.tween(
                                    300, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 焦点缩放/高亮:按"自身中心离屏幕中心的距离"自动缩放并调整亮度。
 * 中间那一项最大最亮,越靠上/下越小越暗(wear ScalingLazyColumn 的视觉语义)。
 * 用 onGloballyPositioned 记录位置 + graphicsLayer 绘制阶段读取,滚动时不触发重组。
 */
@Composable
fun Modifier.centerFocus(maxShrink: Float = 0.14f, maxDim: Float = 0.45f): Modifier {
    val scale = remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    val alpha = remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    val density = LocalDensity.current
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHpx = with(density) { cfg.screenHeightDp.dp.toPx() }
    return this
        .onGloballyPositioned { c ->
            val cy = c.positionInRoot().y + c.size.height / 2f
            val half = (screenHpx / 2f).coerceAtLeast(1f)
            val d = (kotlin.math.abs(cy - half) / half).coerceIn(0f, 1f)
            scale.floatValue = 1f - maxShrink * d
            alpha.floatValue = 1f - maxDim * d
        }
        .graphicsLayer {
            scaleX = scale.floatValue
            scaleY = scale.floatValue
            this.alpha = alpha.floatValue
        }
}

/** 圆形屏底部安全内边距:列表最后一项用它做 contentPadding.bottom,防止被圆边切掉 */
val LocalRoundBottom = staticCompositionLocalOf { 26.dp }

/**
 * 列表首/末项居中留白(官方 ScalingLazyColumn 的 autoCentering 语义):
 * 滚到顶部时第一项正好在屏幕中间,滚到底部时最后一项也能停在中间。
 */
val LocalListCenterPad = staticCompositionLocalOf { 0.dp }

/** 列表末项居中留白:让最后一项也能停在屏幕中间(配合 LocalListCenterPad 使用) */
val LocalListBottomPad = staticCompositionLocalOf { 0.dp }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScreenScaffold(
    title: String,
    showTimeAlways: Boolean = false,
    showTimeAtTop: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    onHeaderSwipeBack: (() -> Unit)? = null,
    scrollIndicator: LazyListState? = null,
    content: @Composable () -> Unit,
) {
    val c = LocalWrist.current
    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        val w = maxWidth; val h = maxHeight
        // 底部圆边内缩量:越靠底部,可视宽度越窄 → 给内容列表留出安全边距
        // 圆边内缩量按几何算会到 ~90dp(那是水平方向的内缩),作为"底部留白"太大,
        // 会导致滑到底还有一大段空白;这里收敛到 26dp:末项四角可能被圆边轻切,和微思一致。
        val bottomSafe = (roundInset(w, h, h - 4.dp) + 12.dp).coerceAtMost(26.dp)
        val showClock = showTimeAlways || showTimeAtTop
        val topPad = if (showClock) 30.dp else 12.dp
        // 让首/末项中心落在"屏幕中心"(h/2),而不是列表可视区中心
        val itemHalf = 36.dp
        val listTop = topPad + 40.dp
        val listCenterPad = ((h / 2) - listTop - itemHalf).coerceAtLeast(0.dp)
        // 末项不再额外留白(否则底部会有一大段"滑不完"的空白),只保证圆边安全
        val listBottomPad = 6.dp
        CompositionLocalProvider(
            LocalRoundBottom provides bottomSafe,
            LocalListCenterPad provides listCenterPad,
            LocalListBottomPad provides listBottomPad,
        ) {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = topPad)) {
            val inset = roundInset(w, h, 12.dp + 20.dp)
            var swipeAcc by remember { mutableStateOf(0f) }
            var rowMod = Modifier.fillMaxWidth().height(40.dp).padding(start = inset + 6.dp, end = inset + 6.dp)
            if (onHeaderSwipeBack != null) {
                // key 固定 Unit + rememberUpdatedState:onBack 换新实例不再重启手势检测
                val currentBack by rememberUpdatedState(onHeaderSwipeBack)
                rowMod = rowMod.pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { swipeAcc = 0f },
                        onHorizontalDrag = { _, amount -> swipeAcc += amount },
                        onDragEnd = { if (swipeAcc > 80f) currentBack(); swipeAcc = 0f }, // 右滑=返回
                        onDragCancel = { swipeAcc = 0f },
                    )
                }
            }
            Row(rowMod, verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, modifier = Modifier.weight(1f))
                actions()
            }
            content()
        }
        // 顶部弧形时间(官方 CurvedText:文字沿圆表上沿弧线排布)
        if (showClock) CurvedClock()
        // 右侧小巧滚动进度条(仿 wear PositionIndicator,滚动时出现)
        if (scrollIndicator != null) SmallScrollIndicator(scrollIndicator)
        }
        }
    }
}

/** 顶部弧形时间:官方 wear CurvedLayout + CurvedText,沿圆表上沿排布 */
@OptIn(androidx.wear.compose.foundation.ExperimentalWearFoundationApi::class)
@Composable
private fun CurvedClock() {
    val c = LocalWrist.current
    var now by remember { mutableStateOf(TextTime.now()) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(5_000); now = TextTime.now() }
    }
    CurvedLayout(
        anchor = 270f,
        modifier = Modifier.fillMaxSize(),
    ) {
        curvedText(
            text = now,
            color = c.hint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * 右侧小巧滚动进度条(仿 wear PositionIndicator):
 * - 表冠/触屏只要列表在动就出现,停 900ms 后淡出(原来用 isScrollInProgress,表冠滑动不会触发)
 * - 进度按"第几项 + 项内偏移"计算,变量高度列表也不会跳
 */
@Composable
private fun SmallScrollIndicator(state: LazyListState) {
    val barW = 3.dp
    val barH = 26.dp
    val edge = 5.dp
    var visible by remember { mutableStateOf(false) }
    var activity by remember { mutableStateOf(0) }
    val progressTarget = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    // 进度只在布局变化时重算,再由动画平滑过渡 → 视觉上是"滑"而不是"跳"
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo }.collect { info ->
            val first = info.visibleItemsInfo.firstOrNull()
            if (first != null && info.totalItemsCount > 1) {
                val visibleCount = info.visibleItemsInfo.size.coerceAtLeast(1)
                val scrollable = (info.totalItemsCount - visibleCount).coerceAtLeast(1)
                val perItem = first.size.coerceAtLeast(1).toFloat()
                val fraction = (info.viewportStartOffset - first.offset).toFloat() / perItem
                progressTarget.floatValue = ((first.index + fraction) / scrollable).coerceIn(0f, 1f)
            }
        }
    }
    val smooth by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progressTarget.floatValue,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "scrollIndicator",
    )
    // 监听列表布局变化(触屏拖动、表冠 dispatchRawDelta 都会改变 layoutInfo)
    LaunchedEffect(state) {
        snapshotFlow {
            val i = state.layoutInfo.visibleItemsInfo.firstOrNull()
            (i?.index ?: -1) to (i?.offset ?: 0)
        }.collect { activity++ }
    }
    // 每次变化都重置计时:连续滚动时一直显示,停下 900ms 才淡出
    LaunchedEffect(activity) {
        if (activity == 0) return@LaunchedEffect
        visible = true
        kotlinx.coroutines.delay(900)
        visible = false
    }
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        if (!visible) return@Canvas
        val progress = smooth
        val r = size.minDimension / 2f - edge.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val deg = -42f + 84f * progress
        val rad = Math.toRadians(deg.toDouble())
        val px = cx + r * kotlin.math.cos(rad).toFloat()
        val py = cy + r * kotlin.math.sin(rad).toFloat()
        rotate(degrees = deg, pivot = androidx.compose.ui.geometry.Offset(px, py)) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = androidx.compose.ui.geometry.Offset(px - barW.toPx() / 2f, py - barH.toPx() / 2f),
                size = androidx.compose.ui.geometry.Size(barW.toPx(), barH.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW.toPx() / 2f),
            )
        }
    }
}

/**
 * 跟手可打断的右滑返回容器:手指拖动时内容实时跟手位移,
 * 松手超过阈值就完成返回,否则回弹;过程中随时可反向拖回(interruptible)。
 */
@Composable
fun SwipeBackContainer(onBack: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { androidx.compose.animation.core.Animatable(0f) }
    var widthPx by remember { mutableStateOf(1f) }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, drag ->
                        scope.launch { offset.snapTo((offset.value + drag).coerceIn(0f, widthPx)) }
                    },
                    onDragEnd = {
                        val w = widthPx
                        scope.launch {
                            if (offset.value > w * 0.26f) {
                                offset.animateTo(w, androidx.compose.animation.core.tween(150))
                                offset.snapTo(0f)
                                onBack()
                            } else {
                                offset.animateTo(0f, androidx.compose.animation.core.spring(
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offset.animateTo(0f, androidx.compose.animation.core.spring(
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)) }
                    },
                )
            },
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            val p = (offset.value / widthPx).coerceIn(0f, 1f)
            translationX = offset.value * 0.55f
            scaleX = 1f - 0.05f * p
            scaleY = 1f - 0.05f * p
            alpha = 1f - 0.22f * p
        }) { content() }
    }
}

object TextTime {
    private val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    fun now(): String = fmt.format(java.util.Date())
}

/** 水平左滑返回容器:透明覆盖层在内容之上抓横向拖拽(点击仍透传,竖向滚动透传给列表) */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeBack(onBack: () -> Unit, content: @Composable () -> Unit) {
    var acc by remember { mutableStateOf(0f) }
    Box(Modifier.fillMaxSize()) {
        content()
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(onBack) {
                    detectHorizontalDragGestures(
                        onDragStart = { acc = 0f },
                        onHorizontalDrag = { _, amount -> acc += amount },
                        onDragEnd = { if (acc < -80f) onBack(); acc = 0f },
                        onDragCancel = { acc = 0f },
                    )
                },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WCard(title: String, value: String = "", modifier: Modifier = Modifier,
          onLongClick: (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    val c = LocalWrist.current
    var m = modifier
        .centerFocus()
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(c.surface)
        .border(1.dp, c.border, RoundedCornerShape(16.dp))
        .padding(horizontal = 17.dp, vertical = 16.dp)
    m = if (onLongClick != null) m.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
    else if (onClick != null) m.clickable { onClick() } else m
    Column(m) {
        Text(title, color = c.text, fontSize = 16.sp)
        if (value.isNotEmpty()) Text(value, color = c.hint, fontSize = 12.5.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
fun WToggle(title: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val c = LocalWrist.current
    // 不再保存内部状态:取消设置时开关立即回到真实状态;卡片同样带焦点缩放
    Row(
        modifier
            .centerFocus()
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = c.text, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
        SmallToggle(checked, onChange = onChange)
    }
}

/** 小号胶囊开关 */
@Composable
fun SmallToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalWrist.current
    Box(
        Modifier
            .width(26.dp).height(15.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (checked) c.accent else c.hint.copy(alpha = 0.4f))
            .clickable { onChange(!checked) }
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(11.dp).clip(RoundedCornerShape(6.dp)).background(Color.White))
    }
}

@Composable
fun SmallAction(label: String, onClick: () -> Unit) {
    val c = LocalWrist.current
    Text(label, color = c.text, fontSize = 17.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onClick() }.padding(6.dp))
}

@Composable
fun WToast(msg: String?) {
    val ctx = LocalContext.current
    LaunchedEffect(msg) { msg?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() } }
}

@Composable
fun WConfirm(title: String, message: String, okText: String = "确定", cancelText: String = "取消",
             countdown: Int = 0, onOk: () -> Unit, onCancel: () -> Unit = {}) {
    var remain by remember(countdown) { mutableStateOf(countdown) }
    LaunchedEffect(countdown) { if (countdown > 0) while (remain > 0) { kotlinx.coroutines.delay(1000); remain-- } }
    val c = LocalWrist.current
    CompactDialog(title = title, onDismiss = onCancel,
        confirmText = okText, confirmEnabled = remain <= 0,
        onConfirm = onOk, dismissText = cancelText) {
        Text(if (remain > 0) "$message\n(确定在 ${remain}s 后可用)" else message,
            color = c.text, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))
    }
}

@Composable
fun WChoice(title: String, items: List<String>, checked: Int, onPick: (Int) -> Unit, onCancel: () -> Unit = {}) {
    CompactDialog(title = title, onDismiss = onCancel, dismissText = "取消") {
        CompactRows(items, checked, onPick)
    }
}

@Composable
fun WInput(title: String, initial: String, password: Boolean = false, multiline: Boolean = false,
           okText: String = "保存", onOk: (String) -> Unit, onCancel: () -> Unit = {}) {
    var v by remember(initial, title) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initial)) }
    CompactDialog(title = title, onDismiss = onCancel,
        confirmText = okText, onConfirm = { onOk(v.text) }, dismissText = "取消") {
        androidx.compose.material3.OutlinedTextField(
            value = v,
            onValueChange = { v = it },
            singleLine = !multiline,
            modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
            visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        )
    }
}

fun roundInset(containerW: Dp, containerH: Dp, yCenterFromTop: Dp): Dp {
    val w = containerW.value; val h = containerH.value
    val r = minOf(w, h) / 2f
    val dy = abs(yCenterFromTop.value - r)
    if (dy >= r) return Dp(r)
    val halfW = sqrt(r * r - dy * dy)
    return Dp((r - halfW).coerceAtLeast(0f))
}

/** 列表滚动进度条 */
fun Modifier.scrollBar(state: LazyListState): Modifier = this.then(
    Modifier.drawWithContent {
        drawContent()
        val info = state.layoutInfo
        if (info.totalItemsCount > 0) {
            val first = info.visibleItemsInfo.firstOrNull()
            val perItem = (first?.size ?: 0).toFloat()
            if (perItem <= 0f) return@drawWithContent
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            val contentH = info.totalItemsCount * perItem
            if (contentH <= viewport) return@drawWithContent
            val scrolled = state.firstVisibleItemIndex * perItem + info.viewportStartOffset
            val progress = (scrolled / (contentH - viewport)).coerceIn(0f, 1f)
            val barH = size.height * (viewport / contentH)
            val top = (size.height - barH) * progress
            drawRoundRect(
                color = Color.White.copy(alpha = 0.5f),
                topLeft = androidx.compose.ui.geometry.Offset(size.width - 6.dp.toPx(), top),
                size = androidx.compose.ui.geometry.Size(3.dp.toPx(), barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
            )
        }
    }
)

/** 弧形滚动进度:贴屏幕圆边(全屏中心),进度线性(仿微思侧边弧形) */
@Composable
fun Modifier.scrollArc(state: androidx.compose.foundation.lazy.LazyListState): Modifier {
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val den = androidx.compose.ui.platform.LocalDensity.current
    val wPx = with(den) { cfg.screenWidthDp.dp.toPx() }
    val hPx = with(den) { cfg.screenHeightDp.dp.toPx() }
    val dot = with(den) { 1.dp.toPx() }
    return this.then(Modifier.drawWithContent {
        drawContent()
        val info = state.layoutInfo
        if (info.totalItemsCount == 0) return@drawWithContent
        val first = info.visibleItemsInfo.firstOrNull()
        val perItem = (first?.size ?: 0).toFloat()
        if (perItem <= 0f) return@drawWithContent
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        val contentH = info.totalItemsCount * perItem
        if (contentH <= viewport) return@drawWithContent
        val scrolled = state.firstVisibleItemIndex * perItem + info.viewportStartOffset
        val progress = (scrolled / (contentH - viewport)).coerceIn(0f, 1f)
        val radius = minOf(wPx, hPx) / 2f - 10f * den.density
        val cx = wPx / 2f
        val cy = hPx / 2f
        val sweepTotal = 140f
        val start = -70f  // 右上起,顺时针扫向右侧
        drawArc(
            color = Color.White.copy(alpha = 0.10f),
            startAngle = start, sweepAngle = sweepTotal, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(cx - radius, cy - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f * den.density, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
        drawArc(
            color = Color.White.copy(alpha = 0.7f),
            startAngle = start, sweepAngle = sweepTotal * progress, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(cx - radius, cy - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f * den.density, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
        // 进度从顶部到右:起点跟随进度更直观(微思式:拇指沿弧走)
        drawCircle(
            color = Color.White,
            radius = 3.5f * den.density,
            center = androidx.compose.ui.geometry.Offset(
                cx + radius * kotlin.math.cos(Math.toRadians((start + sweepTotal * progress).toDouble())).toFloat(),
                cy + radius * kotlin.math.sin(Math.toRadians((start + sweepTotal * progress).toDouble())).toFloat(),
            ),
        )
    })
}
