package io.github.xhr666.wristchat

import io.github.xhr666.wristchat.data.Pricing
import io.github.xhr666.wristchat.ui.chat.Latex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * 工程判据(R1:每条都能失败;R5:双向断言)。
 * 跑法: `./check.sh` 或 `./gradlew :app:testDebugUnitTest`
 */
class EngineeringRulesTest {

    // ---------- LaTeX 管线 ----------

    @Test fun `A1 sqrt 转成可读根号`() {
        assertEquals("√(2)", Latex.pretty("\\sqrt{2}"))
    }

    @Test fun `A2 frac 转成分数`() {
        assertEquals("(1)/(2)", Latex.pretty("\\frac{1}{2}"))
    }

    @Test fun `A3 上标转 unicode`() {
        assertEquals("x²", Latex.pretty("x^2"))
    }

    @Test fun `A4 希腊字母与运算符`() {
        val out = Latex.pretty("\\pi \\le \\infty")
        assertTrue("缺 π: $out", out.contains("π"))
        assertTrue("缺 ≤: $out", out.contains("≤"))
        assertTrue("缺 ∞: $out", out.contains("∞"))
    }

    @Test fun `A5 整份 LaTeX 文档 脚手架必须消失`() {
        val doc = """
            ```latex
            \documentclass{article}
            \usepackage{amsmath}
            \begin{document}
            **欧拉公式**
            ${'$'}${'$'}e^{i\pi} + 1 = 0${'$'}${'$'}
            \end{document}
            ```
        """.trimIndent()
        val out = Latex.pretty(doc)
        // 双向:该出现的出现
        assertTrue("正文丢了: $out", out.contains("欧拉公式"))
        assertTrue("公式内容丢了: $out", out.contains("1 = 0"))
        // 双向:不该出现的必须不出现(这条能失败:脚手架没删净就会红)
        listOf("documentclass", "usepackage", "begin{document}", "end{document}", "**", "```", "article", "amsmath")
            .forEach { bad -> assertFalse("残留脚手架 '$bad': $out", out.contains(bad)) }
    }

    @Test fun `A6 公式源码不得原样出现`() {
        val out = Latex.pretty("${'$'}\\sqrt{2}${'$'} 和 ${'$'}\\frac{1}{2}${'$'}")
        assertFalse("仍显示 \\sqrt 源码: $out", out.contains("\\sqrt"))
        assertFalse("仍显示 \\frac 源码: $out", out.contains("\\frac"))
        assertFalse("分隔符未去除: $out", out.contains("${'$'}"))
    }

    @Test fun `A7 hasMath 双向`() {
        assertTrue(Latex.hasMath("${'$'}x^2${'$'}"))
        assertTrue(Latex.hasMath("\\sqrt{2}"))
        assertTrue(Latex.hasMath("\\begin{document}"))
        assertFalse(Latex.hasMath("普通中文消息,没有公式"))
    }

    // ---------- 逐行从右往左(显示变换) ----------

    @Test fun `A8 逐行倒序 中文`() {
        assertEquals("?啥的发你给我", io.github.xhr666.wristchat.ui.chat.TextTransform.reversePerLine("我给你发的啥?"))
    }

    @Test fun `A9 逐行倒序 行序不变`() {
        assertEquals("cba\ndef", io.github.xhr666.wristchat.ui.chat.TextTransform.reversePerLine("abc\nfed"))
    }

    @Test fun `A10 含公式时不做倒序`() {
        val t = io.github.xhr666.wristchat.ui.chat.TextTransform
        assertFalse(t.shouldReverse(true, "\\sqrt{2}"))
        assertTrue(t.shouldReverse(true, "普通文字"))
        assertFalse(t.shouldReverse(false, "普通文字"))
    }

    // ---------- 计价(对照官方表折算 CNY) ----------

    @Test fun `P5 单片价格与峰谷倍数`() {
        val offSec = bjSec(2026, 10, 14, 13)   // 工作日 13:00 = 谷
        val peakSec = bjSec(2026, 10, 14, 15)  // 工作日 15:00 = 峰
        val off = Pricing.cost("deepseek-flash", offSec, 1_000_000, 1_000_000, 1_000_000)
        val peak = Pricing.cost("deepseek-flash", peakSec, 1_000_000, 1_000_000, 1_000_000)
        // 谷:0.02+1+4=5.02 元;峰:0.04+2+8=10.04 元(官方 $0.003/0.006、$0.15/0.30、$0.6/1.2 × 汇率≈7)
        assertEquals(5.02, off, 0.001)
        assertEquals(10.04, peak, 0.001)
        assertEquals(2.0, peak / off, 0.0001)   // 峰价应为谷价 2 倍(回归:索引写反会红)
    }

    // ---------- 计费时段(北京时间) ----------

    private fun bjSec(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long {
        val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        c.clear()
        c.set(y, m - 1, d, h, min, 0)
        return c.timeInMillis / 1000
    }

    @Test fun `P1 工作日高峰时段是 peak`() {
        assertTrue("2026-10-14 周三 10:00 应为峰", Pricing.isPeak(bjSec(2026, 10, 14, 10)))
        assertTrue("2026-10-14 周三 15:00 应为峰", Pricing.isPeak(bjSec(2026, 10, 14, 15)))
    }

    @Test fun `P2 工作日非高峰时段不是 peak`() {
        assertFalse("同日 08:00 应为谷", Pricing.isPeak(bjSec(2026, 10, 14, 8)))
        assertFalse("同日 13:00 应为谷", Pricing.isPeak(bjSec(2026, 10, 14, 13)))
        assertFalse("同日 19:00 应为谷", Pricing.isPeak(bjSec(2026, 10, 14, 19)))
    }

    @Test fun `P3 法定节假日全天不是 peak`() {
        assertFalse("国庆 2026-10-01 10:00", Pricing.isPeak(bjSec(2026, 10, 1, 10)))
        assertFalse("国庆 2026-10-07 15:00", Pricing.isPeak(bjSec(2026, 10, 7, 15)))
        assertFalse("春节 2026-02-18 10:00", Pricing.isPeak(bjSec(2026, 2, 18, 10)))
        assertTrue("2026-10-01 应被识别为假期", Pricing.isHoliday(bjSec(2026, 10, 1, 10)))
    }

    @Test fun `P4 周末(含调休上班的周末)全天不是 peak`() {
        assertFalse("2026-10-17 周六 10:00", Pricing.isPeak(bjSec(2026, 10, 17, 10)))
        assertFalse("调休上班日 2026-02-14 周六 10:00 仍按空闲", Pricing.isPeak(bjSec(2026, 2, 14, 10)))
    }
}
