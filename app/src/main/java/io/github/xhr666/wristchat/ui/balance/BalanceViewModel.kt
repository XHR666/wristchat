package io.github.xhr666.wristchat.ui.balance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.github.xhr666.wristchat.WristChatApp
import io.github.xhr666.wristchat.data.BalanceRepository
import io.github.xhr666.wristchat.data.BalanceResult
import io.github.xhr666.wristchat.data.Pricing
import io.github.xhr666.wristchat.data.SessionStore
import io.github.xhr666.wristchat.data.SettingsStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class BalanceUi(
    val peak: Boolean,
    val peakLabel: String,
    val total: String,
    val available: String,
    val toppedUp: String,
    val granted: String,
    val currency: String,
    val todayUsage: String,
    val todayTokens: String,
    val appTotalCost: String,
    val usageSource: String,
    val hint: String,
    val supportsBalance: Boolean,
)

class BalanceViewModel(app: Application) : AndroidViewModel(app) {

    val settings: SettingsStore = (app as WristChatApp).settings
    private val repo = BalanceRepository(settings)
    private val sessionStore = SessionStore(app)

    private val _ui = MutableLiveData<BalanceUi?>(null)
    val ui: LiveData<BalanceUi?> = _ui

    private val _updatedAt = MutableLiveData("")
    val updatedAt: LiveData<String> = _updatedAt

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = repo.fetch()
            val supports = settings.providerId == SettingsStore.PROVIDER_DEEPSEEK
            val peak = Pricing.isPeak(System.currentTimeMillis() / 1000)
            val peakLabel = if (peak) {
                "🌞 高峰时段(北京时间)\n距高峰结束:${fmtRemain(nextSwitchSeconds())}"
            } else {
                "🌙 空闲时段(北京时间)\n距下一高峰开始:${fmtRemain(nextSwitchSeconds())}"
            }

            val appTotal = sessionStore.briefs().sumOf { it.totalCost }

            val ui = BalanceUi(
                peak = peak,
                peakLabel = peakLabel,
                total = result.balance?.totalBalance ?: "--",
                available = if (result.balance?.isAvailable == true) "可用 ✓" else if (result.balance != null) "余额不足 ⚠" else "",
                toppedUp = "充值余额:${result.balance?.toppedUpBalance ?: "--"}",
                granted = "赠送余额:${result.balance?.grantedBalance ?: "--"}",
                currency = "币种:${result.balance?.currency ?: "--"}",
                todayUsage = "今日已用:¥${"%.4f".format(result.usage?.amount ?: 0.0)}",
                todayTokens = if ((result.usage?.tokens ?: 0L) > 0) "今日 tokens:${result.usage!!.tokens}" else "",
                appTotalCost = "本应用累计消费:¥${"%.4f".format(appTotal)}",
                usageSource = when (result.usageSource) {
                    "platform" -> "来源:平台实时用量"
                    "ledger" -> "来源:本地记账(余额差值)"
                    else -> "来源:无(未配置平台 Token)"
                },
                hint = buildString {
                    if (!supports) append("⚠ 仅支持 DeepSeek 余额查询\n")
                    append("高峰:周一至五 9-12、14-18(北京时间),其余含周末全空闲")
                    result.error?.let { append("\n上次错误:$it") }
                },
                supportsBalance = supports,
            )
            _ui.postValue(ui)
            _updatedAt.postValue(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
        }
    }

    /**
     * 距当前时段结束(北京时间)。官方:高峰 = 周一至五 9:00-12:00、14:00-18:00,其余(含周末)空闲。
     * 注意:必须用"当天已过秒数"计算,原来只减了分秒、漏了小时,导致倒计时偏大好几个小时。
     */
    private fun nextSwitchSeconds(): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val secOfDay = (cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND)).toLong()
        val weekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        if (weekend) {
            // 周末全空闲 → 下一个高峰是周一 9:00
            val daysToMon = if (dow == Calendar.SATURDAY) 2L else 1L
            return daysToMon * 86400L + 9 * 3600 - secOfDay
        }
        return when {
            secOfDay < 9 * 3600 -> 9 * 3600 - secOfDay            // 凌晨/早间 → 9:00(高峰开始)
            secOfDay < 12 * 3600 -> 12 * 3600 - secOfDay          // 高峰 → 12:00(高峰结束)
            secOfDay < 14 * 3600 -> 14 * 3600 - secOfDay          // 午休空闲 → 14:00(高峰开始)
            secOfDay < 18 * 3600 -> 18 * 3600 - secOfDay          // 高峰 → 18:00(高峰结束)
            else -> (24 * 3600 - secOfDay) + 9 * 3600             // 18:00 后 → 次日 9:00
        }
    }

    private fun fmtRemain(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return if (h > 0) "${h}小时${m}分" else "${m}分${sec % 60}秒"
    }
}
