package io.github.xhr666.wristchat.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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
fun WristAppRoot(settings: SettingsStore, foregroundEpoch: Int = 0) {
    val palette = when (settings.theme) { "light" -> Light; "dark" -> Dark; else -> Amoled }
    val scheme = if (settings.theme == "light")
        lightColorScheme(primary = palette.accent, background = palette.bg, surface = palette.surface)
    else darkColorScheme(primary = palette.accent, background = palette.bg, surface = palette.surface)

    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalWrist provides palette) {
            // 每次真正回到前台重新判定(免密次数按"进入次数"扣)
            var locked by remember(foregroundEpoch) { mutableStateOf(needLock(settings)) }
            if (locked) {
                LockScreen(settings) { locked = false }
            } else {
                HomePager(settings)
            }
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
    // settledPage:只在页面真正落定时更新,滑动过程中不触发整树重组
    val currentPage by remember { derivedStateOf { pagerState.settledPage } }
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
            // 页面切换焦点效果:居中的整页放大,向两侧滑出时逐渐缩小淡出
            // graphicsLayer 块内读取滚动状态 = 绘制阶段读取,不触发逐帧重组
            Box(Modifier.fillMaxSize().graphicsLayer {
                val off = kotlin.math.abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                val f = off.coerceIn(0f, 1f)
                val sc = 1f - 0.12f * f
                scaleX = sc
                scaleY = sc
                alpha = 1f - 0.35f * f
            }) {
                when (page) {
                    0 -> SessionsScreen(settings, chatVm, onOpenChat = { scope.launch { pagerState.animateScrollToPage(1) } })
                    1 -> ChatScreen(settings, chatVm)
                    2 -> BalanceScreen(settings)
                    else -> SettingsMenuScreen(settings)
                }
            }
        }
    }
}
