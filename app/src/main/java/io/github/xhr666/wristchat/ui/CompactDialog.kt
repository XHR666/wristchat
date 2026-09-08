package io.github.xhr666.wristchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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
    content: @Composable () -> Unit,
) {
    val c = LocalWrist.current
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val maxH = (cfg.screenHeightDp - 12).dp  // 弹窗不超屏,按钮永在屏内
    // 自绘覆盖层:无平台窗口动画,内容真正居中;整体限高,内容超高滚动,按钮固定可见
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xAA000000)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = c.surface,
            shadowElevation = 8.dp,
            modifier = Modifier.widthIn(min = 176.dp, max = 224.dp).heightIn(max = maxH),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                // 内容区:占剩余高度、超高滚动
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 20.dp),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) { content() }
                }
                if (confirmText != null || dismissText != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        if (dismissText != null) {
                            TextButton(onClick = onDismiss) { Text(dismissText, color = c.hint, fontSize = 13.sp) }
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

@Composable
fun CompactRows(items: List<String>, checked: Int? = null, onPick: (Int) -> Unit) {
    val c = LocalWrist.current
    Column(Modifier.verticalScroll(rememberScrollState())) {
        items.forEachIndexed { i, s ->
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
