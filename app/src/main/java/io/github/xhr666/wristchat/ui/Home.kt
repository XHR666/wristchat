package io.github.xhr666.wristchat.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
fun WristAppRoot(settings: SettingsStore) {
    val palette = when (settings.theme) { "light" -> Light; "dark" -> Dark; else -> Amoled }
    val scheme = if (settings.theme == "light")
        lightColorScheme(primary = palette.accent, background = palette.bg, surface = palette.surface)
    else darkColorScheme(primary = palette.accent, background = palette.bg, surface = palette.surface)

    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalWrist provides palette) {
            var locked by remember { mutableStateOf(needLock(settings)) }
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
    if (s.graceLeft > 0) { s.graceLeft -= 1; return false }
    return true
}

val LocalCurrentPage = staticCompositionLocalOf { 0 }
val LocalPageBack = staticCompositionLocalOf<() -> Unit> { {} }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePager(settings: SettingsStore) {
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 4 })
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
        HorizontalPager(state = pagerState, userScrollEnabled = !PagerLock.locked, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> SessionsScreen(settings, chatVm, onOpenChat = { scope.launch { pagerState.animateScrollToPage(1) } })
                1 -> ChatScreen(settings, chatVm)
                2 -> BalanceScreen(settings)
                else -> SettingsMenuScreen(settings)
            }
        }
    }
}
