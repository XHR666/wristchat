package io.github.xhr666.wristchat.data

object ThinkLabel {
    fun streaming(seconds: Long): String = "思考中(用时${seconds.coerceAtLeast(0)}秒)"
    fun done(seconds: Long): String =
        if (seconds > 0) "已思考(用时${seconds}秒)" else "已思考"
}
