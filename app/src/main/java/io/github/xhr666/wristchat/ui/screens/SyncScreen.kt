package io.github.xhr666.wristchat.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xhr666.wristchat.WristChatApp
import io.github.xhr666.wristchat.data.SettingsStore
import io.github.xhr666.wristchat.data.SkillStore
import io.github.xhr666.wristchat.data.SyncServer
import io.github.xhr666.wristchat.ui.LocalWrist
import io.github.xhr666.wristchat.ui.SmallAction
import io.github.xhr666.wristchat.ui.common.QrUtil
import io.github.xhr666.wristchat.ui.settings.SettingsViewModel
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** 手机同步全屏页:二维码 + 地址 + 密钥;离开即停 */
@Composable
fun SyncOverlay(settings: SettingsStore, vm: SettingsViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalWrist.current
    var server by remember { mutableStateOf<SyncServer?>(null) }
    var url by remember { mutableStateOf("") }
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    var started by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { server?.stop() }
    }

    LaunchedEffect(Unit) {
        val s = settings
        if (s.syncPin.length != 4) s.syncPin = (1000..9999).random().toString()
        val svc = SyncServer(
            pin = s.syncPin,
            readConfig = { configJson(settings) },
            applyConfig = { body -> applyConfigJson(settings, vm, body) },
        )
        if (svc.start()) {
            val ip = svc.localIp() ?: "(未获取到 IP,请检查网络)"
            url = "http://$ip:${svc.port}"
            qr = QrUtil.generate(url, (150 * ctx.resources.displayMetrics.density).toInt())
            server = svc
            started = true
        } else {
            url = "启动失败"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallAction("‹") { onClose() }
            Text("手机同步", color = c.text, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Spacer(Modifier.width(40.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text("用手机相机/微信扫一扫", color = c.hint, fontSize = 12.sp)
        qr?.let {
            Image(
                bitmap = it.asImageBitmap(), contentDescription = "二维码",
                modifier = Modifier
                    .size(170.dp)
                    .padding(6.dp)
                    .background(Color.White),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(url, color = c.text, fontSize = 11.sp, textAlign = TextAlign.Center)
        Text("密钥:${settings.syncPin}", color = c.text, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text("手机需与手表同一网络;打开网页先输入密钥", color = c.hint, fontSize = 10.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Row {
            androidx.compose.material3.TextButton(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("sync", "$url\nPIN:${settings.syncPin}"))
            }) { Text("复制地址", color = c.accent, fontSize = 12.sp) }
            androidx.compose.material3.TextButton(onClick = { onClose() }) { Text("停止并返回", color = c.accent, fontSize = 12.sp) }
        }
    }
}

private fun configJson(s: SettingsStore): String = org.json.JSONObject().apply {
    put("temperature", s.temperature); put("topP", s.topP)
    put("maxTokens", s.maxTokens); put("thinking", s.thinkingEnabled)
    put("effort", s.reasoningEffort); put("model", s.model)
    put("provider", s.providerId); put("baseUrl", s.baseUrl); put("apiPath", s.apiPath)
    put("customPrompt", s.customPrompt)
    put("quickInputs", org.json.JSONArray(s.getQuickInputs()))
}.toString()

private fun applyConfigJson(s: SettingsStore, vm: SettingsViewModel, body: String): String {
    return try {
        val o = org.json.JSONObject(body)
        s.temperature = o.optDouble("temperature", 1.0).toFloat().coerceIn(0f, 2f)
        s.topP = o.optDouble("topP", 1.0).toFloat().coerceIn(0f, 1f)
        s.maxTokens = o.optInt("maxTokens", 4096).coerceIn(256, 65536)
        s.thinkingEnabled = o.optBoolean("thinking", true)
        s.reasoningEffort = o.optString("effort", "low")
        s.model = o.optString("model", s.model)
        s.providerId = o.optString("provider", s.providerId)
        s.baseUrl = o.optString("baseUrl", s.baseUrl)
        s.apiPath = o.optString("apiPath", s.apiPath)
        s.customPrompt = o.optString("customPrompt", s.customPrompt)
        o.optJSONArray("quickInputs")?.let { arr -> s.setQuickInputs((0 until arr.length()).map { arr.optString(it) }) }
        o.optString("apiKey").takeIf { it.isNotBlank() }?.let { s.apiKey = it }
        o.optString("skillContent").takeIf { it.isNotBlank() }?.let { c ->
            SkillStore(vm.appContext).importFile(o.optString("skillName").ifBlank { "web_import.md" }, c)
        }
        o.optString("chatImport").takeIf { it.isNotBlank() }?.let { c ->
            val r = vm.importFromText(c, "web_chat.json")
            return if (r.startsWith("已导入")) "已保存;$r" else "已保存(聊天导入失败:$r)"
        }
        "已保存"
    } catch (e: Exception) { "保存失败:${e.message}" }
}
