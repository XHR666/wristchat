package io.github.xhr666.wristchat.data

import java.util.Calendar
import java.util.TimeZone

/**
 * DeepSeek 定价表(官方 2026-08 核对):元/百万 tokens, [谷价, 峰价]。
 * 高峰 = 北京时间周一至周五 9:00-12:00、14:00-18:00;其余空闲;2026-08-23 起周末全天谷价。
 */
object Pricing {

    data class Price(val hit: DoubleArray, val miss: DoubleArray, val out: DoubleArray)

    // 谷/峰 顺序。官方 2026-09(deepseek-flash = V4.1-Flash)核对:
    // 命中 0.02/0.04 · 未命中 1/2 · 输出 4/8(元/百万 tokens);v4-pro 保持原价
    private val BASE = Price(doubleArrayOf(0.02, 0.04), doubleArrayOf(1.0, 2.0), doubleArrayOf(4.0, 8.0))
    private val PRO = Price(doubleArrayOf(0.15, 0.30), doubleArrayOf(4.5, 9.0), doubleArrayOf(13.5, 27.0))

    fun priceFor(model: String): Price {
        val m = model.lowercase()
        return if ("pro" in m) PRO else BASE
    }

    /**
     * 官方规则(api-docs.deepseek.com/zh-cn/quick_start/pricing):
     * 高峰 = 北京时间周一至周五 9:00-12:00、14:00-18:00;其余(含整个周末、夜间)全为空闲。
     */
    fun isPeak(timeSec: Long): Boolean {
        val bj = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        bj.timeInMillis = timeSec * 1000
        val hour = bj.get(Calendar.HOUR_OF_DAY)
        val dow = bj.get(Calendar.DAY_OF_WEEK) // 1=Sun ... 7=Sat
        if (dow == Calendar.SUNDAY || dow == Calendar.SATURDAY) return false // 周末全天谷价
        return (hour in 9 until 12) || (hour in 14 until 18)
    }

    /** 计算单次请求费用(元) */
    fun cost(model: String, timeSec: Long, hitTokens: Long, missTokens: Long, outTokens: Long): Double {
        val p = priceFor(model)
        val pi = if (isPeak(timeSec)) 1 else 0
        return (hitTokens / 1e6) * p.hit[pi] +
            (missTokens / 1e6) * p.miss[pi] +
            (outTokens / 1e6) * p.out[pi]
    }

    /** 仅调试/内部使用;界面不再展示价格(价格常变) */
    fun priceLabel(model: String): String {
        val p = priceFor(model)
        val m = "%.2f/%.2f".format(p.hit[0], p.hit[1])
        val miss = "%.1f/%.1f".format(p.miss[0], p.miss[1])
        val out = "%.1f/%.1f".format(p.out[0], p.out[1])
        val isPro = p == PRO
        val name = if (isPro) "v4-pro" else "v4-flash 系"
        return "$name: 命中 $m · 未命中 $miss · 输出 $out (谷/峰,元/百万)"
    }
}
