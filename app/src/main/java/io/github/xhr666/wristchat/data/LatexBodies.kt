package io.github.xhr666.wristchat.data

/** 从回复里抽出公式正文，并把 JLatexMath 容易整段失败的命令收成它能画的形式。 */
object LatexBodies {
    private val math = Regex(
        """\\begin\{(?:tikzpicture|equation\*?|align\*?|aligned|gather\*?|cases|matrix|pmatrix|bmatrix)\}[\s\S]*?\\end\{(?:tikzpicture|equation\*?|align\*?|aligned|gather\*?|cases|matrix|pmatrix|bmatrix)\}|\$\$[\s\S]+?\$\$|\\\[[\s\S]+?\\\]|\\\([\s\S]+?\\\)|(?<!\$)\$(?!\$)[^\$\n]+?\$(?!\$)"""
    )

    fun extract(src: String): List<String> = math.findAll(src).map { unwrap(it.value) }.toList()

    fun ranges(src: String): List<Pair<IntRange, String>> =
        math.findAll(src).map { it.range to unwrap(it.value) }.toList()

    fun soften(body: String): String = body
        .replace(Regex("""\\operatorname\{([^}]*)\}"""), "\\\\mathrm{$1}")
        .replace(Regex("""\\text\{([^}]*)\}"""), "$1")
        .replace(Regex("""\\(?:label|tag|nonumber)\{[^}]*\}"""), "")
        .replace("\\begin{align*}", "\\begin{aligned}")
        .replace("\\end{align*}", "\\end{aligned}")
        .replace("\\begin{align}", "\\begin{aligned}")
        .replace("\\end{align}", "\\end{aligned}")
        .replace(Regex("""\\begin\{equation\*?\}"""), "")
        .replace(Regex("""\\end\{equation\*?\}"""), "")
        .trim()

    private fun unwrap(raw: String): String = when {
        raw.startsWith("$$") && raw.endsWith("$$") && raw.length >= 4 -> raw.substring(2, raw.length - 2)
        raw.startsWith("\\[") && raw.endsWith("\\]") && raw.length >= 4 -> raw.substring(2, raw.length - 2)
        raw.startsWith("\\(") && raw.endsWith("\\)") && raw.length >= 4 -> raw.substring(2, raw.length - 2)
        raw.startsWith("$") && raw.endsWith("$") -> raw.substring(1, raw.length - 1)
        else -> raw
    }
}
