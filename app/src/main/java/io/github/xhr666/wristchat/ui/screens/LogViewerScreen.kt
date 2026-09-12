package io.github.xhr666.wristchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xhr666.wristchat.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 全屏日志查看页:不用弹窗(弹窗在个别机型上有卡死问题),直接整页滚动显示。
 * 崩溃日志/ANR 日志/运行日志 共用。整段读取放 IO,界面只渲染行。
 */
@Composable
fun LogViewerScreen(title: String, load: suspend () -> String, onBack: () -> Unit) {
    val c = LocalWrist.current
    val listState = rememberLazyListState()
    var text by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        text = withContext(Dispatchers.IO) { load() }
    }

    ScreenScaffold(title = title,
        actions = { SmallAction("‹") { onBack() } },
        onHeaderSwipeBack = onBack) {
        val t = text
        when {
            t == null -> Text("读取中…", color = c.hint, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
            t.isBlank() -> Text("(空)", color = c.hint, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
            ) {
                // 分行渲染:只取最近 600 行,并倒序 —— 最新的在最上面,不用往下翻
                val lines = t.lines().takeLast(600).reversed()
                items(lines.size) { i ->
                    Text(lines[i].ifBlank { " " }, color = c.text, fontSize = 11.sp,
                        lineHeight = 14.sp, modifier = Modifier.padding(vertical = 1.dp))
                }
            }
        }
        RotaryList(listState, enabled = LocalCurrentPage.current == 3)
    }
}
