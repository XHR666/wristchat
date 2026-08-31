package io.github.xhr666.wristchat.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class BalanceInfo(
    val isAvailable: Boolean,
    val currency: String,
    val totalBalance: String,
    val grantedBalance: String,
    val toppedUpBalance: String,
)

data class UsageToday(
    val amount: Double,
    val tokens: Long,
    val model: String,
)

data class BalanceResult(
    val balance: BalanceInfo?,
    val usage: UsageToday?,
    val usageSource: String, // "platform" / "ledger" / "none"
    val error: String? = null,
)

/** 余额 + 今日用量(平台 Token 实时 or 本地记账) */
class BalanceRepository(
    private val settings: SettingsStore,
) {

    suspend fun fetch(): BalanceResult {
        val key = settings.apiKey
        if (key.isBlank()) return BalanceResult(null, null, "none", "未配置 API Key")

        // ① 官方余额(仅 DeepSeek)
        var balance: BalanceInfo? = null
        var balanceError: String? = null
        if (Providers.byId(settings.providerId).supportsBalance) {
            try {
                val (code, text) = Http.get("https://api.deepseek.com/user/balance", key)
                if (code in 200..299) {
                    val root = JSONObject(text)
                    val infos = root.optJSONArray("balance_infos")?.optJSONObject(0)
                    balance = infos?.let {
                        BalanceInfo(
                            isAvailable = root.optBoolean("is_available", true),
                            currency = it.optString("currency", "CNY"),
                            totalBalance = it.optString("total_balance", "0"),
                            grantedBalance = it.optString("granted_balance", "0"),
                            toppedUpBalance = it.optString("topped_up_balance", "0"),
                        )
                    }
                } else balanceError = "余额接口 HTTP $code"
            } catch (e: Exception) {
                balanceError = e.message
            }
        }

        // ② 今日用量:平台 Token 实时
        val token = settings.platformToken
        if (token.isNotBlank()) {
            val usage = try { fetchPlatformUsage(token) } catch (e: Exception) { null }
            if (usage != null) return BalanceResult(balance, usage, "platform", balanceError)
        }

        // ③ 本地记账:balance 变化差值(与上次对比)
        val ledgerUsage = computeLedger(balance)
        return BalanceResult(balance, ledgerUsage, if (ledgerUsage != null) "ledger" else "none", balanceError)
    }

    private fun fetchPlatformUsage(token: String): UsageToday? {
        val now = System.currentTimeMillis()
        val tz = TimeZone.getDefault().getOffset(now) / 1000
        val start = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis / 1000
        val end = start + 86400
        val url = "https://platform.deepseek.com/api/v0/usage/by_api_key/amount?start=$start&end=$end&tz=$tz"
        val (code, text) = Http.get(url, token.replaceFirst("Bearer ", ""), timeoutMs = 20000)
        if (code !in 200..299) return null
        val root = JSONObject(text)
        val series = root.optJSONObject("data")?.optJSONObject("biz_data")?.optJSONArray("series")
            ?: root.optJSONObject("data")?.optJSONArray("series") ?: return null
        var cost = 0.0
        var tokens = 0L
        var found = false
        var modelName = ""
        for (i in 0 until series.length()) {
            val s = series.optJSONObject(i) ?: continue
            val model = s.optString("model")
            if (modelName.isEmpty()) modelName = model
            val buckets = s.optJSONArray("buckets") ?: continue
            for (j in 0 until buckets.length()) {
                val b = buckets.optJSONObject(j) ?: continue
                val u = b.optJSONObject("usage") ?: continue
                val hit = u.optLong("PROMPT_CACHE_HIT_TOKEN", 0)
                val miss = u.optLong("PROMPT_CACHE_MISS_TOKEN", 0)
                val out = u.optLong("RESPONSE_TOKEN", 0)
                if (hit + miss + out == 0L) continue
                found = true
                tokens += hit + miss + out
                cost += Pricing.cost(model, b.optLong("time", System.currentTimeMillis() / 1000), hit, miss, out)
            }
        }
        return if (found) UsageToday(cost, tokens, modelName) else null
    }

    private fun computeLedger(balance: BalanceInfo?): UsageToday? {
        if (balance == null) return null
        val last = settings.ledgerLastBalance
        val today = todayKey()
        // 跨天重置
        if (settings.ledgerDate != today) {
            settings.ledgerDate = today
            settings.ledgerTodayUsage = 0.0
        }
        val current = balance.totalBalance.toDoubleOrNull() ?: return null
        val todayUsage = settings.ledgerTodayUsage
        if (last != null) {
            val delta = last - current
            if (delta > 0.0001) {
                settings.ledgerTodayUsage = todayUsage + delta
            }
        }
        settings.ledgerLastBalance = current
        return UsageToday(settings.ledgerTodayUsage, 0, "本地记账")
    }

    private fun todayKey(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(Date())
    }
}
