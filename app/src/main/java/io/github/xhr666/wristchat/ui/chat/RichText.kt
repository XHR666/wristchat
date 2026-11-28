package io.github.xhr666.wristchat.ui.chat

import android.os.Build
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ImageSpan
import android.util.LruCache
import android.widget.TextView
import io.github.xhr666.wristchat.data.FormulaText
import io.github.xhr666.wristchat.data.LatexBodies
import io.github.xhr666.wristchat.data.StreamRender
import io.noties.markwon.Markwon
import ru.noties.jlatexmath.JLatexMathAndroid
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * 气泡文字:Markwon 排 Markdown,JLatexMath 把闭合公式画成图嵌进去。
 * 流式输出时未闭合的符号留在末尾,原样显示。
 */
object RichText {
    private val images = LruCache<String, JLatexMathDrawable>(24)
    @Volatile private var inited = false

    fun bind(tv: TextView, markwon: Markwon, source: String, color: Int) {
        val text = LatexBodies.normalize(source)
        val parts = StreamRender.split(text)
        val spanned = SpannableStringBuilder()
        for (seg in segments(parts.ready)) {
            when (seg) {
                is Seg.Md -> if (seg.text.isNotEmpty()) spanned.append(markwon.toMarkdown(seg.text))
                is Seg.Math -> appendFormula(tv, spanned, seg.body, color)
            }
        }
        if (parts.pending.isNotEmpty()) {
            if (spanned.isNotEmpty() && spanned.last() != '\n') spanned.append('\n')
            spanned.append(parts.pending)
        }
        tv.setTextColor(color)
        tv.text = if (spanned.isEmpty()) source else spanned
        tv.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun appendFormula(tv: TextView, out: SpannableStringBuilder, body: String, color: Int) {
        val drawable = if (FormulaText.unsupported(body)) null else formula(tv, body, color)
        if (drawable == null) {
            if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
            out.append(FormulaText.fallback(body))
            return
        }
        val at = out.length
        out.append('\uFFFC')
        val align = if (Build.VERSION.SDK_INT >= 29) ImageSpan.ALIGN_CENTER else ImageSpan.ALIGN_BASELINE
        out.setSpan(ImageSpan(drawable, align), at, at + 1, 33)
    }

    private sealed class Seg {
        data class Md(val text: String) : Seg()
        data class Math(val body: String) : Seg()
    }

    private fun segments(src: String): List<Seg> {
        if (src.isEmpty()) return emptyList()
        val out = ArrayList<Seg>()
        var i = 0
        for ((range, body) in LatexBodies.ranges(src)) {
            if (range.first > i) out += Seg.Md(src.substring(i, range.first))
            out += Seg.Math(body)
            i = range.last + 1
        }
        if (i < src.length) out += Seg.Md(src.substring(i))
        return out
    }

    private fun formula(tv: TextView, latex: String, color: Int): JLatexMathDrawable? {
        val body = latex.trim()
        if (body.isEmpty() || body.length > 4096) return null
        val soft = LatexBodies.soften(body)
        return build(tv, body, color) ?: if (soft != body) build(tv, soft, color) else null
    }

    private fun build(tv: TextView, latex: String, color: Int): JLatexMathDrawable? {
        val key = latex + "#" + color + "#" + tv.textSize.toInt()
        images.get(key)?.let { return it }
        return try {
            if (!inited) {
                JLatexMathAndroid.init(tv.context.applicationContext)
                inited = true
            }
            val drawable = JLatexMathDrawable.builder(latex)
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
