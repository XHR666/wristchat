package io.github.xhr666.wristchat.data

/** 连接失败后要不要再请求一次。4xx(除了 429)不重试。 */
object NetRetry {
    fun again(code: Int, attempt: Int, maxExtra: Int): Boolean {
        if (attempt >= maxExtra) return false
        if (code in 200..299) return false
        if (code in 400..499 && code != 429) return false
        return true
    }
}
