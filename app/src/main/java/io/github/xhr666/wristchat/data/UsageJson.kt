package io.github.xhr666.wristchat.data

import org.json.JSONObject

/** DeepSeek usage 对象。缺 miss 时用 prompt - hit 补上,避免只按输出计费。 */
object UsageJson {
    fun parse(raw: JSONObject?): TokenUsage {
        if (raw == null) return TokenUsage()
        val prompt = raw.optInt("prompt_tokens")
        var hit = raw.optInt("prompt_cache_hit_tokens")
        if (hit == 0) {
            hit = raw.optJSONObject("prompt_tokens_details")?.optInt("cached_tokens") ?: 0
        }
        var miss = raw.optInt("prompt_cache_miss_tokens")
        if (miss == 0 && prompt > hit) miss = prompt - hit
        return TokenUsage(
            promptTokens = prompt,
            cacheHit = hit,
            cacheMiss = miss,
            completionTokens = raw.optInt("completion_tokens"),
            totalTokens = raw.optInt("total_tokens"),
            reasoningTokens = raw.optJSONObject("completion_tokens_details")?.optInt("reasoning_tokens") ?: 0,
        )
    }
}
