package io.github.xhr666.wristchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xhr666.wristchat.WristChatApp
import io.github.xhr666.wristchat.data.SettingsStore
import io.github.xhr666.wristchat.ui.*
import io.github.xhr666.wristchat.ui.balance.BalanceViewModel
import io.github.xhr666.wristchat.ui.balance.BalanceViewModelFactory

@Composable
fun BalanceScreen(settings: SettingsStore) {
    val app = LocalContext.current.applicationContext as WristChatApp
    val vm: BalanceViewModel = viewModel(factory = BalanceViewModelFactory(app))
    val ui by vm.ui.observeAsState()
    val updated by vm.updatedAt.observeAsState("")
    val listState = rememberLazyListState()
    val c = LocalWrist.current

    LaunchedEffect(Unit) { vm.refresh() }

    ScreenScaffold(title = "余额", actions = { SmallAction("⟳") { vm.refresh() } }) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().scrollBar(listState),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 24.dp),
        ) {
            item {
                Text(ui?.peakLabel ?: "", color = c.text, fontSize = 12.sp, lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(16.dp)).padding(10.dp))
            }
            item {
                Text(ui?.total ?: "--", color = c.text, fontSize = 42.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp), textAlign = TextAlign.Center)
            }
            item {
                Text(ui?.available ?: "", color = if (ui?.available?.contains("✓") == true) c.accent else c.hint,
                    fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                WCard("充值余额", ui?.toppedUp ?: "")
                WCard("赠送余额", ui?.granted ?: "")
                WCard("币种", ui?.currency ?: "")
            }
            item { Spacer(Modifier.height(4.dp)) }
            item {
                WCard("今日已用", ui?.todayUsage ?: "")
                if (!(ui?.todayTokens.isNullOrEmpty())) WCard("今日 tokens", ui?.todayTokens ?: "")
                WCard("本应用累计消费", ui?.appTotalCost ?: "")
                WCard("用量来源", ui?.usageSource ?: "")
            }
            item {
                Text(ui?.hint ?: "", color = c.hint, fontSize = 10.sp, lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 8.dp))
                Text("更新于 $updated", color = c.hint, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        RotaryList(listState, enabled = LocalCurrentPage.current == 2)
    }
}
