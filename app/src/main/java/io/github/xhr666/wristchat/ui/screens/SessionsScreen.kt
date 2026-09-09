package io.github.xhr666.wristchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xhr666.wristchat.data.SessionStore
import io.github.xhr666.wristchat.data.SettingsStore
import io.github.xhr666.wristchat.ui.*
import io.github.xhr666.wristchat.ui.chat.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 负一屏:会话列表(AI 标题),点击打开聊天;只持摘要,不占内存 */
@Composable
fun SessionsScreen(settings: SettingsStore, vm: ChatViewModel, onOpenChat: () -> Unit) {
    val sessions by vm.sessions.observeAsState(emptyList())
    val currentPage = LocalCurrentPage.current
    val listState = rememberLazyListState()
    val c = LocalWrist.current

    // 每次进入负一屏都刷新(IO 线程解析,避免卡顿)
    LaunchedEffect(currentPage) {
        if (currentPage == 0) { kotlinx.coroutines.delay(400); vm.refreshSessions() }
    }
    var deleteTarget by remember { mutableStateOf<SessionStore.SessionBrief?>(null) }

    ScreenScaffold(
        title = if (sessions.isEmpty()) "会话" else "会话(${sessions.size})",
        showTimeAlways = true,
        actions = {},
    ) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp)
                    ,
                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
            ) {
                itemsIndexed(sessions) { _, s ->
                    WCard(
                        title = s.title.ifBlank { "新会话" },
                        value = sub(s),
                        onClick = {
                            vm.setCurrentSession(s.id)
                            onOpenChat()
                        },
                        onLongClick = { deleteTarget = s },
                    )
                }
                item { Spacer(Modifier.height(4.dp)) }
            }
            // 提示(居中于列表区,避开底部圆边)
            if (sessions.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("暂无会话\n去聊天页开始第一段对话", color = c.hint, fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(bottom = 30.dp))
                }
            }
        }
        RotaryList(listState, enabled = currentPage == 0)
    }

    deleteTarget?.let { s ->
        WConfirm("删除会话", "删除「${s.title.take(14)}」?不可恢复",
            okText = "删除", onOk = { vm.deleteSession(s.id); deleteTarget = null },
            onCancel = { deleteTarget = null })
    }
}

private fun sub(s: SessionStore.SessionBrief): String {
    val cost = if (s.totalCost > 0) " · ¥%.3f".format(s.totalCost) else ""
    return "${s.msgCount} 条 · ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(s.updatedAt))}$cost"
}
