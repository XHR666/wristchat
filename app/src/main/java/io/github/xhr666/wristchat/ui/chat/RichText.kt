package io.github.xhr666.wristchat.ui.chat

import android.os.Build
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ImageSpan
import android.util.LruCache
import android.widget.TextView
import io.github.xhr666.wristchat.data.StreamRender
import io.noties.markwon.Markwon
import ru.noties.jlatexmath.JLatexMathAndroid
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * 气泡文字:Markwon 排 Markdown,JLatexMath 把闭合公式画成图嵌进去。
 * 流式输出时未闭合的符号留在末尾,原样显示,不交给 KaTeX/WebView。
 */
object RichText {
    private val math = Regex(
        """\$\$[\s\S]+?\$\$|\\\[[\s\S]+?\\\]|\\\([\s\S]+?\\\)|(?<!\$)\$(?!\$)[^\$\n]+?\$(?!\$)"""
    )
    private val images = LruCache<String, JLatexMathDrawable>(24)
    @Volatile private var inited = false

    fun bind(tv: TextView, markwon: Markwon, source: String, color: Int) {
        val parts = StreamRender.split(source)
        if (parts.ready.isEmpty()) {
            tv.text = parts.pending
            tv.setTextColor(color)
            return
        }
        val formulas = ArrayList<String>()
        val withHoles = math.replace(parts.ready) { m ->
            val i = formulas.size
            formulas += unwrap(m.value)
            "§M${i}§"
        }
        val spanned = SpannableStringBuilder(markwon.toMarkdown(withHoles))
        for (i in formulas.indices) {
            val token = "§M${i}§"
            val at = spanned.indexOf(token)
            if (at < 0) continue
            val drawable = formula(tv, formulas[i], color) ?: continue
            spanned.replace(at, at + token.length, "\uFFFC")
            val align = if (Build.VERSION.SDK_INT >= 29) ImageSpan.ALIGN_CENTER else ImageSpan.ALIGN_BASELINE
            spanned.setSpan(ImageSpan(drawable, align), at, at + 1, 33)
        }
        if (parts.pending.isNotEmpty()) {
            if (spanned.isNotEmpty() && spanned.last() != '\n') spanned.append('\n')
            spanned.append(parts.pending)
        }
        tv.setTextColor(color)
        tv.text = spanned
        tv.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun unwrap(raw: String): String = when {
        raw.startsWith("$$") && raw.endsWith("$$") && raw.length >= 4 -> raw.substring(2, raw.length - 2)
        raw.startsWith("\\[") && raw.endsWith("\\]") && raw.length >= 4 -> raw.substring(2, raw.length - 2)
        raw.startsWith("\\(") && raw.endsWith("\\)") && raw.length >= 4 -> raw.substring(2, raw.length - 2)
        raw.startsWith("$") && raw.endsWith("$") && raw.length >= 2 -> raw.substring(1, raw.length - 1)
        else -> raw
    }

    private fun formula(tv: TextView, latex: String, color: Int): JLatexMathDrawable? {
        val body = latex.trim()
        if (body.isEmpty() || body.length > 4096) return null
        val key = body + "#" + color + "#" + tv.textSize.toInt()
        images.get(key)?.let { return it }
        return try {
            if (!inited) {
                JLatexMathAndroid.init(tv.context.applicationContext)
                inited = true
            }
            val drawable = JLatexMathDrawable.builder(body)
                .textSize(tv.textSize)
                .color(color)
                .padding(1, 0, 1, 0)
                .build()
            val maxW = (tv.resources.displayMetrics.widthPixels * 0.62f).toInt().coerceAtLeast(1)
            val iw = drawable.intrinsicWidth.coerceAtLeast(1)
            val ih = drawable.intrinsicHeight.coerceAtLeast(1)
            val scale = minOf(1f, maxW.toFloat() / iw)
            val w = (iw * scale).toInt().coerceAtLeast(1)
            val h = (ih * scale).toInt().coerceAtLeast(1)
            drawable.setBounds(0, 0, w, h)
            images.put(key, drawable)
            drawable
        } catch (_: Throwable) {
            null
        }
    }
}
