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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = c.surface,
            modifier = Modifier.widthIn(max = 220.dp, min = 180.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Box(Modifier.heightIn(max = 300.dp)) {
                    content()
                }
                if (confirmText != null || dismissText != null) {
                    Spacer(Modifier.height(10.dp))
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

/** 弹窗内可滚动列表行 */
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
