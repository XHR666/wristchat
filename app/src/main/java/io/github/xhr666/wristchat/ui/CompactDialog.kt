package io.github.xhr666.wristchat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cc.star0.wear.lib.cnwearoverlay.runtime.CnWearOverlay
import kotlinx.coroutines.launch

val LocalDialogScroll = staticCompositionLocalOf<androidx.compose.foundation.ScrollState?> { null }

/**
 * 统一紧凑弹窗:宽度贴合内容,标题居中,按钮居中,内容超高自动滚动。
 * 修复:原生 AlertDialog 过宽/按钮位置怪/内容显示太少等问题。
 */
@Composable
fun CompactDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    confirmEnabled: Boolean = true,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = null,
    onDismissButton: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = LocalWrist.current
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    // 圆屏安全尺寸:矩形中心在屏幕中心时,四角必须落在圆内。
    // 22dp 圆角会让"可见角"向内缩约 6.5dp,所以用 rc = r + 6.5 做补偿,可以在不被切的前提下把弹窗做大。
    val sw = cfg.screenWidthDp.toFloat()
    val sh = cfg.screenHeightDp.toFloat()
    val radius = minOf(sw, sh) / 2f
    // 允许四角略微越出圆(约 11dp),22dp 圆角会把这部分藏进不可见区,视觉上不会被切
    val rc = radius + 11f
    val maxW = minOf(sw * 0.82f, 190f)
    val halfW = maxW / 2f
    val maxHByCircle = 2f * kotlin.math.sqrt((rc * rc - halfW * halfW).coerceAtLeast(1f))
    val maxH = minOf(sh - 20f, 170f, maxHByCircle).dp
    // 改用系统 Dialog 窗口承载:自绘全屏覆盖层在部分手表上不显示/被页面布局影响;
    // 系统窗口天然置顶、自带遮罩(点击外部关闭),不再需要自绘 mask。
    val dialogView = androidx.compose.ui.platform.LocalView.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DisposableEffect(scroll) {
            val listener = android.view.View.OnGenericMotionListener { _, ev ->
                val mapped = CnWearOverlay.rotaryEvent(ev)
                try {
                    if (mapped.action == android.view.MotionEvent.ACTION_SCROLL) {
                        val axis = mapped.getAxisValue(android.view.MotionEvent.AXIS_SCROLL)
                        val v = if (kotlin.math.abs(axis) > 0.001f) axis
                        else -mapped.getAxisValue(android.view.MotionEvent.AXIS_VSCROLL)
                        if (kotlin.math.abs(v) > 0.001f) {
                            // 弹窗行很矮，不用系统的大滚动系数，一格大约一行。
                            scope.launch { scroll.scrollBy(-v * 36f) }
                            return@OnGenericMotionListener true
                        }
                    }
                    false
                } finally {
                    CnWearOverlay.recycleRotaryEvent(ev, mapped)
                }
            }
            dialogView.setOnGenericMotionListener(listener)
            onDispose { dialogView.setOnGenericMotionListener(null) }
        }
        SideEffect {
            (dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setWindowAnimations(0)
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = c.surface,
                shadowElevation = 0.dp,
                modifier = Modifier.widthIn(min = 150.dp, max = maxW.dp).heightIn(max = maxH),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = (maxH - 96.dp).coerceAtLeast(28.dp)),
                    ) {
                        CompositionLocalProvider(LocalDialogScroll provides scroll) { content() }
                        if (scroll.maxValue > 0) {
                            Canvas(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(4.dp)) {
                                val track = size.height
                                val thumb = (track * 0.28f).coerceIn(16f, track)
                                val y = (scroll.value / scroll.maxValue.toFloat()) * (track - thumb)
                                drawRoundRect(
                                    color = Color(0xFFD7DCE1),
                                    topLeft = Offset(0f, y),
                                    size = Size(size.width, thumb),
                                    cornerRadius = CornerRadius(size.width, size.width),
                                )
                            }
                        }
                    }
                    if (confirmText != null || dismissText != null) {
                        Box(Modifier.fillMaxWidth().padding(top = 2.dp), contentAlignment = Alignment.Center) {
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            if (dismissText != null) {
                                TextButton(onClick = onDismissButton ?: onDismiss) { Text(dismissText, color = c.hint, fontSize = 13.sp) }
                            }
                            if (confirmText != null) {
                                TextButton(onClick = onConfirm ?: {}, enabled = confirmEnabled) {
                                    Text(confirmText, color = if (confirmEnabled) c.accent else c.hint, fontSize = 13.sp)
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompactRows(items: List<String>, checked: Int? = null, onPick: (Int) -> Unit) {
    val c = LocalWrist.current
    val scroll = LocalDialogScroll.current ?: rememberScrollState()
    Column(Modifier.verticalScroll(scroll)) {
        items.forEachIndexed { i, s ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Text(
                (if (checked != null && i == checked) "● " else if (checked != null) "○ " else "") + s,
                color = if (checked != null && i == checked) c.accent else c.text,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(i) }
                    .padding(vertical = 7.dp),
            )
        }
    }
}
