package io.github.xhr666.wristchat.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.channels.BufferOverflow
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
    // 无收集器(如锁定/翻页中)时事件直接丢弃,避免解锁后积压事件一次性注入导致列表跳飞
    val flow = MutableSharedFlow<Int>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    fun emit(delta: Int) {
        if (flow.subscriptionCount.value > 0) flow.tryEmit(delta.coerceIn(-32, 32))
    }
}

@Composable
fun RotaryList(listState: LazyListState, enabled: Boolean) {
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        RotaryBus.flow.collectLatest { listState.dispatchRawDelta(it.toFloat()) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScreenScaffold(
    title: String,
    showTimeAlways: Boolean = false,
    showTimeAtTop: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    onHeaderSwipeBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = LocalWrist.current
    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        val w = maxWidth; val h = maxHeight
        Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
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
                if (showTimeAlways || showTimeAtTop) {
                    Text(TextTime.now(), color = c.hint, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 6.dp))
                }
                Text(title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, modifier = Modifier.weight(1f))
                actions()
            }
            content()
        }
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
        .fillMaxWidth()
        .padding(vertical = 3.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(c.surface)
        .border(1.dp, c.border, RoundedCornerShape(14.dp))
        .padding(horizontal = 14.dp, vertical = 10.dp)
    m = if (onLongClick != null) m.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
    else if (onClick != null) m.clickable { onClick() } else m
    Column(m) {
        Text(title, color = c.text, fontSize = 14.sp)
        if (value.isNotEmpty()) Text(value, color = c.hint, fontSize = 11.sp, lineHeight = 14.sp,
            modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun WToggle(title: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val c = LocalWrist.current
    var on by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) { on = checked }   // 外部状态变化(如同步)时刷新开关
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = c.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        SmallToggle(on, onChange = { v -> on = v; onChange(v) })
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
    var v by remember(initial, title) { mutableStateOf(initial) }
    CompactDialog(title = title, onDismiss = onCancel,
        confirmText = okText, onConfirm = { onOk(v) }, dismissText = "取消") {
        androidx.compose.material3.OutlinedTextField(
            value = v,
            onValueChange = { if (it.length <= 8000) v = it },
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
