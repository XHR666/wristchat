package io.github.xhr666.wristchat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
        private set

    /** 统一入口:弹出新规格;若替换掉一个尚未关闭的 Confirm,先通知其取消,避免外部状态悬挂 */
    private fun show(s: WSpec) {
        val old = spec
        if (old != null && old !== s) (old as? WSpec.Confirm)?.onCancel?.invoke()
        spec = s
        runCatching { io.github.xhr666.wristchat.data.AppLog.i("dlg", "open ${s::class.simpleName}: ${s.title().take(10)}") }
    }

    /** WSpec 标题(日志用) */
    private fun WSpec.title(): String = when (this) {
        is WSpec.Confirm -> title
        is WSpec.Choice -> title
        is WSpec.Input -> title
        is WSpec.Items -> title
        is WSpec.Text -> title
    }
    fun confirm(t: String, m: String, ok: String = "确定", countdown: Int = 0, onCancel: () -> Unit = {}, onOk: () -> Unit) { show(WSpec.Confirm(t, m, ok, countdown = countdown, onOk = onOk, onCancel = onCancel)) }
    fun choice(t: String, items: List<String>, checked: Int, onPick: (Int) -> Unit) { show(WSpec.Choice(t, items, checked, onPick)) }
    fun input(t: String, init: String, pw: Boolean = false, ml: Boolean = false, ok: String = "保存", onOk: (String) -> Unit) { show(WSpec.Input(t, init, pw, ml, ok, onOk)) }
    fun items(t: String, list: List<String>, onPick: (Int) -> Unit, neutral: String? = null, onNeutral: (() -> Unit)? = null) { show(WSpec.Items(t, list, onPick, neutral, onNeutral)) }
    fun text(t: String, body: String) { show(WSpec.Text(t, body)) }
    fun close() { spec = null }
    fun num(t: String, init: Float, min: Float, max: Float, onOk: (Float) -> Unit) {
        val pretty = if (init % 1f == 0f) init.toInt().toString() else init.toString()
        show(WSpec.Input(t, pretty, ok = "保存", onOk = { s ->
            val v = s.replace(',', '.').toFloatOrNull()
            if (v == null || v < min || v > max) { show(WSpec.Text("提示", "无效输入($min-$max)")); return@Input }
            onOk(v)
        }))
    }
}

@Composable
fun WDialogHost(d: DialogController) {
    val s = d.spec ?: return
    when (s) {
        is WSpec.Confirm -> {
            // remember 以 spec 为 key:连续弹两个 Confirm 时倒计时/提示从新规格开始
            var remain by remember(s) { mutableStateOf(s.countdown) }
            LaunchedEffect(s.countdown) { if (s.countdown > 0) while (remain > 0) { kotlinx.coroutines.delay(1000); remain-- } }
            val c = LocalWrist.current
            CompactDialog(title = s.title, onDismiss = { d.close(); s.onCancel() },
                confirmText = s.ok, confirmEnabled = remain <= 0,
                onConfirm = { d.close(); runCatching { s.onOk() }.onFailure { e -> io.github.xhr666.wristchat.data.AppLog.i("dlg", "err ${e}"); d.text("出错", e.message ?: "操作异常") } },
                dismissText = s.cancel.takeIf { it.isNotEmpty() }) {
                Text((if (remain > 0) "确定 ${remain}s 后可用\n\n" else "") + s.message,
                    color = c.text, fontSize = 13.sp, lineHeight = 18.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState()))
            }
        }
        is WSpec.Choice -> CompactDialog(title = s.title, onDismiss = { d.close() }, dismissText = "取消") {
            CompactRows(s.items, s.checked) { i -> d.close(); runCatching { s.onPick(i) }.onFailure { e -> io.github.xhr666.wristchat.data.AppLog.i("dlg", "err ${e}"); d.text("出错", e.message ?: "操作异常") } }
        }
        is WSpec.Input -> {
            // remember 以 spec 为 key:编辑 A 后再编辑 B 时输入框内容必须从 B 的 initial 重新开始
            var v by remember(s) { mutableStateOf(s.initial) }
            CompactDialog(title = s.title, onDismiss = { d.close() },
                confirmText = s.ok, onConfirm = { d.close(); runCatching { s.onOk(v) }.onFailure { e -> io.github.xhr666.wristchat.data.AppLog.i("dlg", "err ${e}"); d.text("出错", e.message ?: "操作异常") } }, dismissText = "取消") {
                androidx.compose.material3.OutlinedTextField(
                    value = v, onValueChange = { if (it.length <= 8000) v = it },
                    singleLine = !s.multiline,
                    visualTransformation = if (s.password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                )
            }
        }
        is WSpec.Items -> CompactDialog(title = s.title, onDismiss = { d.close() }, dismissText = "关闭") {
            val c = LocalWrist.current
            Column(Modifier.verticalScroll(rememberScrollState())) {
                s.items.forEachIndexed { i, it ->
                    Text(it, color = c.text, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable { d.close(); runCatching { s.onPick(i) }.onFailure { e -> io.github.xhr666.wristchat.data.AppLog.i("dlg", "err ${e}"); d.text("出错", e.message ?: "操作异常") } }.padding(vertical = 7.dp))
                }
                s.neutral?.let { n ->
                    Text(n, color = c.accent, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable { d.close(); runCatching { s.onNeutral?.invoke() }.onFailure { e -> io.github.xhr666.wristchat.data.AppLog.i("dlg", "err ${e}"); d.text("出错", e.message ?: "操作异常") } }.padding(vertical = 7.dp))
                }
            }
        }
        is WSpec.Text -> CompactDialog(title = s.title, onDismiss = { d.close() }, dismissText = "关闭") {
            Text(s.body, color = LocalWrist.current.text, fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(top = 4.dp))
        }
    }
}
