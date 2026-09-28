package io.github.xhr666.wristchat.ui.chat

/**
 * 原生 LaTeX → 可读文本渲染(不依赖 WebView/KaTeX/字库)。
 * 覆盖常用子集:根号、分数、上下标、希腊字母、常用运算符、\text{} 等。
 * 目的:任何情况下都能看见内容,不再出现"空白气泡"或"白条"。
 */
object Latex {

    private val greek = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε",
        "zeta" to "ζ", "eta" to "η", "theta" to "θ", "iota" to "ι", "kappa" to "κ",
        "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ", "pi" to "π", "rho" to "ρ",
        "sigma" to "σ", "tau" to "τ", "phi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ",
        "Pi" to "Π", "Sigma" to "Σ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
    )
    private val symbols = mapOf(
        "times" to "×", "cdot" to "·", "div" to "÷", "pm" to "±", "mp" to "∓",
        "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥", "ne" to "≠", "neq" to "≠",
        "approx" to "≈", "equiv" to "≡", "propto" to "∝", "infty" to "∞",
        "sum" to "∑", "prod" to "∏", "int" to "∫", "iint" to "∬", "oint" to "∮",
        "partial" to "∂", "nabla" to "∇", "forall" to "∀", "exists" to "∃",
        "in" to "∈", "notin" to "∉", "subset" to "⊂", "supset" to "⊃", "subseteq" to "⊆",
        "cup" to "∪", "cap" to "∩", "emptyset" to "∅", "therefore" to "∴", "because" to "∵",
        "to" to "→", "rightarrow" to "→", "leftarrow" to "←", "Rightarrow" to "⇒",
        "Leftarrow" to "⇐", "leftrightarrow" to "↔", "mapsto" to "↦",
        "angle" to "∠", "degree" to "°", "perp" to "⊥", "parallel" to "∥", "sim" to "∼",
        "star" to "⋆", "circ" to "∘", "bullet" to "•", "ldots" to "…", "dots" to "…", "cdots" to "⋯",
    )
    private val sup = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶',
        '7' to '⁷', '8' to '⁸', '9' to '⁹', 'n' to 'ⁿ', 'i' to 'ⁱ', '+' to '⁺', '-' to '⁻',
    )
    private val sub = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆',
        '7' to '₇', '8' to '₈', '9' to '₉', 'n' to 'ₙ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
    )

    /** 是否包含公式(含分隔符或反斜杠命令) */
    fun hasMath(s: String): Boolean =
        s.contains("$") || s.contains("\\(") || s.contains("\\[") || s.contains("\\begin") ||
            Regex("\\\\[a-zA-Z]+").containsMatchIn(s)

    /** 把 LaTeX 转成可读文本 */
    fun pretty(input: String): String {
        var s = input
        // 去除数学分隔符
        s = s.replace("$$", "").replace("\\[", "").replace("\\]", "")
            .replace("\\(", "").replace("\\)", "")
        s = Regex("(?<!\\\\)\\$").replace(s, "")
        // 结构命令
        s = replaceBraced(s, "sqrt") { args -> if (args.size >= 2) args[0] + "√(" + args[1] + ")" else "√(" + args[0] + ")" }
        s = replaceBraced(s, "frac") { args -> if (args.size >= 2) "(" + args[0] + ")/(" + args[1] + ")" else "/" }
        s = replaceBraced(s, "dfrac") { args -> if (args.size >= 2) "(" + args[0] + ")/(" + args[1] + ")" else "/" }
        s = replaceBraced(s, "tfrac") { args -> if (args.size >= 2) "(" + args[0] + ")/(" + args[1] + ")" else "/" }
        for (cmd in listOf("text", "mathrm", "mathbf", "mathit", "operatorname", "textbf", "textit")) {
            s = replaceBraced(s, cmd) { args -> args.firstOrNull() ?: "" }
        }
        // 上下标:花括号 → unicode,否则原样
        s = applyScript(s, '^', sup)
        s = applyScript(s, '_', sub)
        // 希腊字母与符号
        for ((k, v) in greek) s = s.replace("\\$k", v)
        for ((k, v) in symbols) s = Regex("\\\\" + Regex.escape(k) + "(?![a-zA-Z])").replace(s, v)
        // 清理剩余命令与空白
        s = s.replace("\\left", "").replace("\\right", "")
            .replace("\\,", " ").replace("\\;", " ").replace("\\!", "").replace("\\ ", " ")
            .replace("\\quad", "  ").replace("\\qquad", "    ")
            .replace("\\\\", "\n")
        s = Regex("\\\\[a-zA-Z]+").replace(s, "")   // 未知命令直接去掉,避免显示源码
        s = s.replace("{", "").replace("}", "")
        s = s.replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()
        return s
    }

    /** 处理 \cmd{a}{b}:从最外层开始剥离花括号参数 */
    private fun replaceBraced(src: String, cmd: String, build: (List<String>) -> String): String {
        var s = src
        while (true) {
            val idx = s.indexOf("\\$cmd{")
            if (idx < 0) return s
            var i = idx + cmd.length + 2
            val args = mutableListOf<String>()
            var end = i
            while (i < s.length && s[i] == '{') {
                var depth = 0
                val sb = StringBuilder()
                var j = i
                while (j < s.length) {
                    val ch = s[j]
                    if (ch == '{') { depth++; if (depth > 1) sb.append(ch) }
                    else if (ch == '}') { depth--; if (depth == 0) { j++; break } else sb.append(ch) }
                    else sb.append(ch)
                    j++
                }
                args.add(sb.toString())
                end = j
                i = j
            }
            if (args.isEmpty()) { s = s.replaceFirst("\\$cmd", build(listOf(""))) ; continue }
            s = s.substring(0, idx) + build(args) + s.substring(end)
        }
    }

    /** ^{...} / _{...} → unicode 上下标(可转换时) */
    private fun applyScript(src: String, marker: Char, table: Map<Char, Char>): String {
        var s = src
        val re = Regex(Regex.escape(marker.toString()) + "\\{([^{}]*)\\}")
        s = re.replace(s) { m ->
            val body = m.groupValues[1]
            val conv = body.map { table[it] }
            if (conv.all { it != null }) conv.joinToString("") { it.toString() } else "$marker($body)"
        }
        val re2 = Regex(Regex.escape(marker.toString()) + "([0-9a-zA-Z+\\-])")
        s = re2.replace(s) { m ->
            val ch = m.groupValues[1][0]
            table[ch]?.toString() ?: "$marker${m.groupValues[1]}"
        }
        return s
    }
}
