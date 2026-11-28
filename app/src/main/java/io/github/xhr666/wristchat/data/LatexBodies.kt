package io.github.xhr666.wristchat.data

/** 从回复里抽出公式正文，并把 JLatexMath 容易整段失败的命令收成它能画的形式。 */
object LatexBodies {
    private val math = Regex(
        """\\begin\{(?:tikzpicture|equation\*?|align\*?|aligned|gather\*?|cases|matrix|pmatrix|bmatrix)\}[\s\S]*?\\end\{(?:tikzpicture|equation\*?|align\*?|aligned|gather\*?|cases|matrix|pmatrix|bmatrix)\}|\$\$[\s\S]+?\$\$|\\\[[\s\S]+?\\\]|\\\([\s\S]+?\\\)|(?<!\$)\$(?!\$)[^\$\n]+?\$(?!\$)"""
    )

    fun extract(src: String): List<String> = ranges(src).map { it.second }

    fun ranges(src: String): List<Pair<IntRange, String>> {
        val text = normalize(src)
        val found = math.findAll(text).map { it.range to prepare(unwrap(it.value)) }.toList()
        if (found.isNotEmpty()) return found
        return bareLines(text)
    }

    /** 模型有时把命令写成两个反斜杠。腕上星语在画之前会收成一个。 */
    fun normalize(src: String): String {
        val sb = StringBuilder(src.length)
        var i = 0
        while (i < src.length) {
            if (src[i] == '\\') {
                var j = i
                while (j < src.length && src[j] == '\\') j++
                val next = src.getOrNull(j)
                if (next != null && (next.isLetter() || next == '[' || next == ']' || next == '(' || next == ')')) {
                    sb.append('\\')
                } else {
                    repeat(j - i) { sb.append('\\') }
                }
                i = j
            } else {
                sb.append(src[i])
                i++
            }
        }
        return sb.toString().replace("\\dfrac", "\\frac").replace("\\tfrac", "\\frac")
    }

    fun prepare(body: String): String {
        val soft = soften(body).replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        return soft
    }

    private fun bareLines(text: String): List<Pair<IntRange, String>> {
        val out = ArrayList<Pair<IntRange, String>>()
        var at = 0
        for (line in text.split('\n')) {
            val t = line.trim()
            if (isBare(t)) out += (at until at + line.length) to prepare(t)
            at += line.length + 1
        }
        return out
    }

    private fun isBare(line: String): Boolean {
        if (line.length < 3 || line.any { it.code in 0x4E00..0x9FFF }) return false
        if (!line.contains('\\')) return false
        return line.contains('=') || line.contains('^') || line.contains('{') || line.contains('_')
    }

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
