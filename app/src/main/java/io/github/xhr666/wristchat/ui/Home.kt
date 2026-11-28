package io.github.xhr666.wristchat.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.github.xhr666.wristchat.WristChatApp
import io.github.xhr666.wristchat.data.SettingsStore
import io.github.xhr666.wristchat.ui.lock.LockScreen
import io.github.xhr666.wristchat.ui.screens.*

/** 进程级解锁状态 */
object LockGate {
    @Volatile
    var unlocked: Boolean = false
}

@Composable
fun WristAppRoot(settings: SettingsStore, foregroundEpoch: Int = 0, covered: Boolean = false) {
    val palette = when (settings.theme) { "light" -> Light; "dark" -> Dark; else -> Amoled }
    val scheme = if (settings.theme == "light")
        lightColorScheme(primary = palette.accent, background = palette.bg, surface = palette.surface)
    else darkColorScheme(primary = palette.accent, background = palette.bg, surface = palette.surface)

    MaterialTheme(colorScheme = scheme) {
        Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalWrist provides palette) {
            // 每次真正回到前台重新判定(免密次数按"进入次数"扣)
            var locked by remember(foregroundEpoch) { mutableStateOf(needLock(settings)) }
            if (locked) {
                LockScreen(settings) { locked = false }
            } else {
                HomePager(settings)
            }
        }
        if (covered) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black))
        }
    }
}

private fun needLock(s: SettingsStore): Boolean {
    if (!s.passwordEnabled) return false
    if (LockGate.unlocked) return false
    if (s.graceLeft > 0) {
        // 免密次数按"回到前台"的次数扣(次数用完即要求输密码)
        s.graceLeft -= 1
        return false
    }
    return true
}

val LocalCurrentPage = staticCompositionLocalOf { 0 }
val LocalPageBack = staticCompositionLocalOf<() -> Unit> { {} }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePager(settings: SettingsStore) {
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 4 })
    // 必须用 currentPage:settledPage 在某些情况下不更新,
    // 会导致"进入负一屏即刷新 / 表冠启用判断 / 余额刷新"全部失效(会话列表一直是空的根因)
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }
    val scope = rememberCoroutineScope()
    val app = LocalContext.current.applicationContext as WristChatApp
    val chatVm: io.github.xhr666.wristchat.ui.chat.ChatViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = io.github.xhr666.wristchat.ui.chat.ChatViewModelFactory(app))

    CompositionLocalProvider(
        LocalCurrentPage provides currentPage,
        LocalPageBack provides {
            if (currentPage > 0) scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
        },
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !PagerLock.locked,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            Box(Modifier.fillMaxSize()) {
                when (page) {
                    0 -> SessionsScreen(settings, chatVm, onOpenChat = { scope.launch { pagerState.animateScrollToPage(1) } })
                    1 -> ChatScreen(settings, chatVm)
                    2 -> BalanceScreen(settings)
                    else -> SettingsMenuScreen(settings)
                }
            }
        }
        // 更新提醒只在空闲后检查,不挡冷启动滑动。通知里不带对话内容。
        LaunchedEffect(Unit) {
            if (!settings.notifyUpdate) return@LaunchedEffect
            kotlinx.coroutines.delay(8000)
            val now = System.currentTimeMillis()
            if (now - settings.lastUpdateCheck < settings.updateCooldownMin * 60_000L) return@LaunchedEffect
            val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val r = io.github.xhr666.wristchat.data.UpdateRepository(settings).check()
                    settings.lastUpdateCheck = System.currentTimeMillis()
                    (r as? io.github.xhr666.wristchat.data.UpdateResult.Found)?.info?.tagName
                }.getOrNull()
            }
            if (!found.isNullOrBlank()) io.github.xhr666.wristchat.data.Notices.updateReady(app, found)
        }
    }
}
