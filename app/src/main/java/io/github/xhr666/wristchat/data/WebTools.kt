package io.github.xhr666.wristchat.data

/**
 * DeepSeek Chat Completions 的服务端联网工具。
 * 官方限制:deepseek_web_search / deepseek_read_url 不能和客户端 function 工具同时声明。
 */
object WebTools {
    const val SEARCH = "deepseek_web_search"
    const val READ_URL = "deepseek_read_url"

    fun toolTypes(web: Boolean, memory: Boolean): List<String> = when {
        web -> listOf(SEARCH, READ_URL)
        memory -> listOf("memory_tool")
        else -> emptyList()
    }
}
