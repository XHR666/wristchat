package io.github.xhr666.wristchat.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

object RotaryBus {
    val flow = MutableSharedFlow<Int>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    fun emit(delta: Int) { flow.tryEmit(delta) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RotaryList(listState: LazyListState, enabled: Boolean) {
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        RotaryBus.flow.collectLatest { listState.dispatchRawDelta(it.toFloat()) }
    }
}

@Composable
fun ScreenScaffold(
    title: String,
    showTimeAlways: Boolean = false,
    showTimeAtTop: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val c = LocalWrist.current
    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        val w = maxWidth; val h = maxHeight
        Column(Modifier.fillMaxSize()) {
            val inset = roundInset(w, h, 24.dp + 21.dp)
            Row(
                Modifier.fillMaxWidth().height(42.dp).padding(start = inset + 6.dp, end = inset + 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showTimeAlways || showTimeAtTop) {
                    Text(TextTime.now(), color = c.hint, fontSize = 11.sp, fontWeight = FontWeight.Bold,
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
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(14.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = c.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
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
    var remain by remember { mutableStateOf(countdown) }
    LaunchedEffect(countdown) { if (countdown > 0) while (remain > 0) { kotlinx.coroutines.delay(1000); remain-- } }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title, fontSize = 15.sp) },
        text = { Text(message, fontSize = 13.sp) },
        confirmButton = { TextButton(onClick = onOk, enabled = remain <= 0) { Text(if (remain > 0) "$okText($remain)" else okText) } },
        dismissButton = { if (cancelText.isNotEmpty()) TextButton(onClick = onCancel) { Text(cancelText) } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
fun WChoice(title: String, items: List<String>, checked: Int, onPick: (Int) -> Unit, onCancel: () -> Unit = {}) {
    val c = LocalWrist.current
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title, fontSize = 15.sp) },
        text = {
            Column {
                items.forEachIndexed { i, s ->
                    Text((if (i == checked) "● " else "○ ") + s,
                        color = if (i == checked) c.accent else c.text, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(i) }.padding(vertical = 8.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
fun WInput(title: String, initial: String, password: Boolean = false, multiline: Boolean = false,
           okText: String = "保存", onOk: (String) -> Unit, onCancel: () -> Unit = {}) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title, fontSize = 15.sp) },
        text = {
            OutlinedTextField(
                value = v, onValueChange = { if (it.length <= 8000) v = it },
                singleLine = !multiline,
                visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            )
        },
        confirmButton = { TextButton(onClick = { onOk(v) }) { Text(okText) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

fun roundInset(containerW: Dp, containerH: Dp, yCenterFromTop: Dp): Dp {
    val w = containerW.value; val h = containerH.value
    val r = minOf(w, h) / 2f
    val dy = abs(yCenterFromTop.value - r)
    if (dy >= r) return Dp(r)
    val halfW = sqrt(r * r - dy * dy)
    return Dp((r - halfW).coerceAtLeast(0f))
}
