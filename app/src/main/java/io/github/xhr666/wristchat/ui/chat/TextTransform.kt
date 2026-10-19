package io.github.xhr666.wristchat.ui.chat

/**
 * 显示用文本变换。
 * "每行从右往左读":把**每一行**的字符顺序整体倒过来(逐行处理,不影响行序)。
 * 例:"我给你发的啥?" → "?啥的发你给我"
 * 拉丁词按字符倒序,中文按字倒序;emoji/组合字符按码点整体处理。
 */
object TextTransform {

    fun reversePerLine(text: String): String =
        text.split("\n").joinToString("\n") { line -> reverseGraphemes(line) }

    private fun reverseGraphemes(line: String): String {
        // 用码点切分,避免把代理对(emoji)拆坏
        val cps = line.codePoints().toArray()
        val sb = StringBuilder(line.length)
        for (i in cps.indices.reversed()) sb.appendCodePoint(cps[i])
        return sb.toString()
    }

    /** 是否应用倒序:开启设置、且不含公式(公式必须保持 LaTeX 顺序) */
    fun shouldReverse(rtl: Boolean, text: String): Boolean =
        rtl && !Latex.hasMath(text)
}
