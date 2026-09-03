package io.github.xhr666.wristchat.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xhr666.wristchat.WristChatApp
import io.github.xhr666.wristchat.data.SettingsStore
import io.github.xhr666.wristchat.ui.LockGate
import io.github.xhr666.wristchat.ui.LocalWrist
import java.security.MessageDigest

@Composable
fun LockScreen(settings: SettingsStore, onUnlocked: () -> Unit) {
    val c = LocalWrist.current
    var input by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("请输入密码") }
    var lockedUntil by remember { mutableStateOf(0L) }

    fun check(pin: String) {
        val s = settings
        if (s.pendingReset0000 && pin == "0000") {
            s.passwordEnabled = false; s.passwordHash = ""; s.passwordSalt = ""
            s.pendingReset0000 = false; s.failCount = 0; s.lastFiveInputs = ""
            LockGate.unlocked = true
            onUnlocked(); return
        }
        if (s.passwordHash.isNotEmpty() && hash(pin, s.passwordSalt) == s.passwordHash) {
            s.failCount = 0; s.lastFiveInputs = ""; s.pendingReset0000 = false
            LockGate.unlocked = true
            onUnlocked(); return
        }
        s.failCount += 1
        // 仅存 PIN 的哈希,避免明文落盘;0000 重置判定用明文常量(哈希可比较)
        val h = hashPinPlain(pin)
        val last = (s.lastFiveInputs.split(",").filter { it.isNotEmpty() } + h).takeLast(5)
        s.lastFiveInputs = last.joinToString(",")
        val h0 = hashPinPlain("0000")
        if (last.size >= 5 && last.all { it == h0 }) s.pendingReset0000 = true
        if (s.failCount >= 5) {
            lockedUntil = System.currentTimeMillis() + 30_000
            s.failCount = 0
            hint = "错误 5 次,锁定 30 秒"
        } else hint = "密码错误,还可尝试 ${5 - s.failCount} 次"
    }

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockedUntil) {
        while (lockedUntil > System.currentTimeMillis()) {
            kotlinx.coroutines.delay(1000)
            now = System.currentTimeMillis()
        }
        if (lockedUntil != 0L && lockedUntil <= now) {
            hint = if (settings.pendingReset0000) "提示:重置通道就绪" else "请输入密码"
        }
    }
    val locked = now < lockedUntil

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("WristChat", color = c.text, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text(input.padEnd(4).replace(" ", "●").take(4), color = c.text, fontSize = 30.sp, letterSpacing = androidx.compose.ui.unit.TextUnit(6f, androidx.compose.ui.unit.TextUnitType.Sp))
        Spacer(Modifier.height(8.dp))
        Text(hint, color = c.hint, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))
        val keys = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
        keys.chunked(3).forEach { rowKeys ->
            Row {
                rowKeys.forEach { k ->
                    Box(
                        Modifier
                            .size(64.dp)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(c.surface)
                            .clickable(enabled = !locked && k.isNotEmpty()) {
                                if (k == "⌫") {
                                    if (input.isNotEmpty()) input = input.dropLast(1)
                                } else {
                                    if (input.length < 4) {
                                        input += k
                                        if (input.length == 4) { val p = input; input = ""; check(p) }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(k, color = c.text, fontSize = 22.sp) }
                }
            }
        }
    }
}

private fun hash(pin: String, salt: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest("$salt:$pin".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

private fun hashPinPlain(pin: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest(pin.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
