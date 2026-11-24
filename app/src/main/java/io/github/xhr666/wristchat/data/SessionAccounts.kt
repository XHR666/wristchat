package io.github.xhr666.wristchat.data

/**
 * 会话金额与缓存命中率。
 * 历史文件里的 totalCost 经常只含标题请求(很小的正数),消息上的 cost 才是对话本身。
 * 展示取二者较大值,避免列表和余额页把已经记在消息上的花费丢掉。
 */
object SessionAccounts {
    const val WARN_BYTES = 50L * 1024 * 1024

    fun shouldWarn(bytes: Long): Boolean = bytes > WARN_BYTES

    fun displayCost(stored: Double, messageCosts: List<Double>): Double {
        var sum = 0.0
        for (c in messageCosts) if (c.isFinite() && c > 0.0) sum += c
        val base = if (stored.isFinite() && stored > 0.0) stored else 0.0
        return if (sum > base) sum else base
    }

    /** 消息上的 hit/miss 优先;都是 0 时才退回会话头里的旧计数。 */
    fun cacheHitRate(messageHit: Long, messageMiss: Long, storedHit: Long, storedMiss: Long): Float {
        val hit = if (messageHit + messageMiss > 0) messageHit else storedHit
        val miss = if (messageHit + messageMiss > 0) messageMiss else storedMiss
        val denom = hit + miss
        if (denom <= 0L) return 0f
        return hit.toFloat() / denom.toFloat()
    }
}
