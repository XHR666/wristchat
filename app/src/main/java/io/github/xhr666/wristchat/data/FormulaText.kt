package io.github.xhr666.wristchat.data

import io.github.xhr666.wristchat.ui.chat.Latex

/** 公式画不出来时必须留下可读文本,不能变成空白。 */
object FormulaText {
    fun unsupported(body: String): Boolean {
        val s = body.lowercase()
        return s.contains("tikzpicture") || s.contains("pgfplots") ||
            s.contains("\\addplot") || s.contains("\\begin{axis}")
    }

    fun fallback(body: String): String {
        if (unsupported(body)) return "（图表，这里画不出来）"
        val pretty = Latex.pretty(body).trim()
        return if (pretty.isNotBlank()) pretty else body.trim()
    }
}
