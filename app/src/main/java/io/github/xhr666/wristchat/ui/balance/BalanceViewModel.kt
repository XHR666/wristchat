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
                    append("高峰:周一至五 9-12、14-18(北京时间),其余含周末全空闲\n")
                    append(Pricing.priceLabel(settings.model))
                    result.error?.let { append("\n上次错误:$it") }
                },
                supportsBalance = supports,
            )
            _ui.postValue(ui)
            _updatedAt.postValue(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
        }
    }

    /** 距当前时段结束(北京时间);周末全天谷价,下一个高峰=周一 9:00 */
    private fun nextSwitchSeconds(): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val sec = cal.get(Calendar.SECOND)
        val intoHour = min * 60 + sec
        val isWeekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        if (isWeekend) {
            // 到周一 9:00
            val daysToMon = if (dow == Calendar.SATURDAY) 2 else 1
            return (daysToMon * 86400L + (9 * 3600) - intoHour)
        }
        return when {
            hour in 9 until 12 -> (12 * 3600 - intoHour).toLong()
            hour in 12 until 14 -> (14 * 3600 - intoHour).toLong()
            hour in 14 until 18 -> (18 * 3600 - intoHour).toLong()
            hour in 18..23 -> ((24 + 9) * 3600 - intoHour).toLong()
            else -> (9 * 3600 - intoHour).toLong() // 0-8 点
        }
    }

    private fun fmtRemain(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return if (h > 0) "${h}小时${m}分" else "${m}分${sec % 60}秒"
    }
}
