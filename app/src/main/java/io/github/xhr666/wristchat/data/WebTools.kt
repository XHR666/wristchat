package io.github.xhr666.wristchat.data

/**
 * DeepSeek Chat Completions 的服务端联网工具。
 * 官方限制:deepseek_web_search / deepseek_read_url 不能和客户端 function 工具同时声明。
 */
object WebTools {
    const val SEARCH = "deepseek_web_search"
    const val READ_URL = "deepseek_read_url"

    /** 搜索和阅读可以分开声明。只要声明了其中任何一个，就不能再带 memory_tool。 */
    fun toolTypes(search: Boolean, read: Boolean, memory: Boolean): List<String> = buildList {
        if (search) add(SEARCH)
        if (read) add(READ_URL)
        if (isEmpty() && memory) add("memory_tool")
    }
}
