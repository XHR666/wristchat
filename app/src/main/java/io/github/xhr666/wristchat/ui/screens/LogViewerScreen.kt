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

    SwipeBackContainer(onBack = onBack) {
    ScreenScaffold(title = title,
        actions = { SmallAction("‹") { onBack() } },
        onHeaderSwipeBack = onBack,
        scrollIndicator = listState) {
        val t = text
        when {
            t == null -> Text("读取中…", color = c.hint, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
            t.isBlank() -> Text("(空)", color = c.hint, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
            else -> {
                // 按 "=== ..." 头切分条目(ANR/崩溃一次一条),整条为单位、最新的条目排最上面
                val entries = remember(t) { splitLogEntries(t) }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = LocalRoundBottom.current),
                ) {
                    items(entries.size) { i ->
                        val e = entries[i]
                        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Text(e.first(), color = c.accent, fontSize = 11.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                lineHeight = 14.sp)
                            e.drop(1).take(60).forEach { line ->
                                Text(line.ifBlank { " " }, color = c.text, fontSize = 11.sp, lineHeight = 14.sp)
                            }
                            if (e.size > 61) Text("…(本条还有 ${e.size - 61} 行)", color = c.hint, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        RotaryList(listState, enabled = LocalCurrentPage.current == 3)
    }
    }
}

/** 把日志切成"条目":以 === 开头的行为一条的标题;没有 === 的按行处理。最新的排最前。 */
private fun splitLogEntries(text: String): List<List<String>> {
    val raw = text.lines()
    val entries = mutableListOf<MutableList<String>>()
    var cur: MutableList<String>? = null
    for (line in raw) {
        if (line.startsWith("===")) {
            cur = mutableListOf(line)
            entries.add(cur)
        } else {
            if (cur == null) { cur = mutableListOf(line); entries.add(cur) } else cur.add(line)
        }
    }
    return entries.takeLast(80).reversed()
}
