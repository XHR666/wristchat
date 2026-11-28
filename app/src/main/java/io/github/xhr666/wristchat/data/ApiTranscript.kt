package io.github.xhr666.wristchat.data

/**
 * 发给对话接口的历史必须是 user/assistant 交替,且第一条是 user。
 * 官方会拒绝「助手消息没有紧跟用户消息」,本地提示、空占位、连续两条助手都会触发。
 */
object ApiTranscript {
    fun prepare(history: List<ChatMessage>): List<ChatMessage> {
        val out = ArrayList<ChatMessage>()
        for (m in history) {
            if (m.role == "user") {
                if (m.content.isBlank() && m.imgs.isEmpty()) continue
                if (out.isNotEmpty() && out.last().role == "user") {
                    val prev = out.removeAt(out.lastIndex)
                    out.add(merge(prev, m))
                } else {
                    out.add(m)
                }
            } else if (m.role == "assistant") {
                if (isLocalError(m)) continue
                if (m.content.isBlank() && m.reasoning.isBlank() && m.serverToolCalls.isBlank()) continue
                if (out.isEmpty() || out.last().role != "user") continue
                out.add(m)
            }
        }
        return out
    }

    /** 本轮提问并进历史。上一条如果也是用户(中间的报错气泡已被丢掉),合成一条,避免连续两条 user。 */
    fun build(history: List<ChatMessage>, user: ChatMessage): List<ChatMessage> {
        val out = prepare(history).toMutableList()
        if (user.content.isBlank() && user.imgs.isEmpty()) return out
        if (out.lastOrNull()?.role == "user") {
            val prev = out.removeAt(out.lastIndex)
            out.add(merge(prev, user))
        } else {
            out.add(user)
        }
        return out
    }

    /** 压缩摘要,以及被丢掉的、出现在第一条用户消息之前的助手回复。放进 system,不单独占一条。 */
    fun preface(history: List<ChatMessage>): String {
        val bits = ArrayList<String>()
        var seenUser = false
        for (m in history) {
            when (m.role) {
                "system" -> if (m.content.isNotBlank()) bits.add(m.content)
                "assistant" -> if (!seenUser && !isLocalError(m) && m.content.isNotBlank()) bits.add(m.content)
                "user" -> if (m.content.isNotBlank() || m.imgs.isNotEmpty()) seenUser = true
            }
        }
        return bits.joinToString("\n\n")
    }

    private fun isLocalError(m: ChatMessage) =
        m.content.startsWith("⚠️") || m.content.startsWith("错误:")

    private fun merge(prev: ChatMessage, next: ChatMessage) = prev.copy(
        content = listOf(prev.content, next.content).filter { it.isNotBlank() }.joinToString("\n"),
        imgs = prev.imgs + next.imgs,
    )
}
