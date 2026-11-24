package io.github.xhr666.wristchat.data

/**
 * 流式输出时,未闭合的 Markdown / LaTeX 分隔符先原样显示,闭合后再交给渲染器。
 * 已闭合的前缀可以排版;从第一个未闭合符号起到末尾都留在 pending。
 */
object StreamRender {
    data class Parts(val ready: String, val pending: String)

    fun split(text: String): Parts {
        val cut = holdFrom(text)
        if (cut >= text.length) return Parts(text, "")
        return Parts(text.substring(0, cut), text.substring(cut))
    }

    private fun holdFrom(text: String): Int {
        var i = 0
        val n = text.length
        while (i < n) {
            if (text.startsWith("```", i)) {
                val close = text.indexOf("```", i + 3)
                if (close < 0) return i
                i = close + 3
                continue
            }
            if (text.startsWith("$$", i)) {
                val close = text.indexOf("$$", i + 2)
                if (close < 0) return i
                i = close + 2
                continue
            }
            if (text.startsWith("\\[", i)) {
                val close = text.indexOf("\\]", i + 2)
                if (close < 0) return i
                i = close + 2
                continue
            }
            if (text.startsWith("\\(", i)) {
                val close = text.indexOf("\\)", i + 2)
                if (close < 0) return i
                i = close + 2
                continue
            }
            if (text[i] == '$') {
                val close = text.indexOf('$', i + 1)
                if (close < 0) return i
                i = close + 1
                continue
            }
            if (text.startsWith("**", i)) {
                val close = text.indexOf("**", i + 2)
                if (close < 0) return i
                i = close + 2
                continue
            }
            if (text[i] == '`') {
                val close = text.indexOf('`', i + 1)
                if (close < 0) return i
                i = close + 1
                continue
            }
            i++
        }
        return n
    }
}
