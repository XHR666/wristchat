package io.github.xhr666.wristchat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xhr666.wristchat.data.SettingsStore

/** 对话框规格(统一由 DialogController 驱动,Compose 原生居中) */
sealed class WSpec {
    data class Confirm(val title: String, val message: String, val ok: String = "确定",
                       val cancel: String = "取消", val countdown: Int = 0,
                       val onOk: () -> Unit, val onCancel: () -> Unit = {}) : WSpec()
    data class Choice(val title: String, val items: List<String>, val checked: Int,
                      val onPick: (Int) -> Unit) : WSpec()
    data class Input(val title: String, val initial: String, val password: Boolean = false,
                     val multiline: Boolean = false, val ok: String = "保存",
                     val onOk: (String) -> Unit) : WSpec()
    data class Items(val title: String, val items: List<String>, val onPick: (Int) -> Unit,
                     val neutral: String? = null, val onNeutral: (() -> Unit)? = null) : WSpec()
    data class Text(val title: String, val body: String) : WSpec()
}

class DialogController {
    var spec by mutableStateOf<WSpec?>(null)
    fun confirm(t: String, m: String, ok: String = "确定", countdown: Int = 0, onCancel: () -> Unit = {}, onOk: () -> Unit) { spec = WSpec.Confirm(t, m, ok, countdown = countdown, onOk = onOk, onCancel = onCancel) }
    fun choice(t: String, items: List<String>, checked: Int, onPick: (Int) -> Unit) { spec = WSpec.Choice(t, items, checked, onPick) }
    fun input(t: String, init: String, pw: Boolean = false, ml: Boolean = false, ok: String = "保存", onOk: (String) -> Unit) { spec = WSpec.Input(t, init, pw, ml, ok, onOk) }
    fun items(t: String, list: List<String>, onPick: (Int) -> Unit, neutral: String? = null, onNeutral: (() -> Unit)? = null) { spec = WSpec.Items(t, list, onPick, neutral, onNeutral) }
    fun text(t: String, body: String) { spec = WSpec.Text(t, body) }
    fun close() { spec = null }
    fun num(t: String, init: Float, min: Float, max: Float, onOk: (Float) -> Unit) {
        val pretty = if (init % 1f == 0f) init.toInt().toString() else init.toString()
        spec = WSpec.Input(t, pretty, ok = "保存", onOk = { s ->
            val v = s.replace(',', '.').toFloatOrNull()
            if (v == null || v < min || v > max) { spec = WSpec.Text("提示", "无效输入($min-$max)"); return@Input }
            onOk(v)
        })
    }
}

@Composable
fun WDialogHost(d: DialogController) {
    val s = d.spec ?: return
    when (s) {
        is WSpec.Confirm -> {
            var remain by remember { mutableStateOf(s.countdown) }
            LaunchedEffect(s.countdown) { if (s.countdown > 0) while (remain > 0) { kotlinx.coroutines.delay(1000); remain-- } }
            AlertDialog(
                onDismissRequest = { d.close(); s.onCancel() },
                title = { Text(s.title, fontSize = 15.sp) },
                text = { Text(s.message, fontSize = 13.sp) },
                confirmButton = { TextButton(onClick = { d.close(); s.onOk() }, enabled = remain <= 0) { Text(if (remain > 0) "${s.ok}($remain)" else s.ok) } },
                dismissButton = { TextButton(onClick = { d.close(); s.onCancel() }) { Text(s.cancel) } },
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
            )
        }
        is WSpec.Choice -> {
            val c = LocalWrist.current
            AlertDialog(
                onDismissRequest = { d.close() },
                title = { Text(s.title, fontSize = 15.sp) },
                text = {
                    Column {
                        s.items.forEachIndexed { i, it ->
                            Text((if (i == s.checked) "● " else "○ ") + it,
                                color = if (i == s.checked) c.accent else c.text, fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth().clickable { d.close(); s.onPick(i) }.padding(vertical = 8.dp))
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { d.close() }) { Text("取消") } },
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
            )
        }
        is WSpec.Input -> {
            var v by remember { mutableStateOf(s.initial) }
            AlertDialog(
                onDismissRequest = { d.close() },
                title = { Text(s.title, fontSize = 15.sp) },
                text = {
                    OutlinedTextField(
                        value = v, onValueChange = { if (it.length <= 8000) v = it },
                        singleLine = !s.multiline,
                        visualTransformation = if (s.password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(keyboardType = if (s.password) KeyboardType.Password else KeyboardType.Text),
                    )
                },
                confirmButton = { TextButton(onClick = { d.close(); s.onOk(v) }) { Text(s.ok) } },
                dismissButton = { TextButton(onClick = { d.close() }) { Text("取消") } },
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
            )
        }
        is WSpec.Items -> {
            val c = LocalWrist.current
            AlertDialog(
                onDismissRequest = { d.close() },
                title = { Text(s.title, fontSize = 15.sp) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        s.items.forEachIndexed { i, it ->
                            Text(it, color = c.text, fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth().clickable { d.close(); s.onPick(i) }.padding(vertical = 8.dp))
                        }
                        s.neutral?.let { n ->
                            Text(n, color = c.accent, fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth().clickable { d.close(); s.onNeutral?.invoke() }.padding(vertical = 8.dp))
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { d.close() }) { Text("关闭") } },
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
            )
        }
        is WSpec.Text -> {
            AlertDialog(
                onDismissRequest = { d.close() },
                title = { Text(s.title, fontSize = 15.sp) },
                text = { Text(s.body, fontSize = 12.sp, modifier = Modifier.verticalScroll(rememberScrollState())) },
                confirmButton = { TextButton(onClick = { d.close() }) { Text("关闭") } },
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
            )
        }
    }
}
