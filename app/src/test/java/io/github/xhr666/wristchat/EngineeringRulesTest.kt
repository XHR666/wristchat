package io.github.xhr666.wristchat

import io.github.xhr666.wristchat.data.FormulaText
import io.github.xhr666.wristchat.data.JsonText
import io.github.xhr666.wristchat.data.Pricing
import io.github.xhr666.wristchat.data.SessionAccounts
import io.github.xhr666.wristchat.data.StreamRender
import io.github.xhr666.wristchat.data.ThinkLabel
import io.github.xhr666.wristchat.data.ToolTrace
import io.github.xhr666.wristchat.data.WebTools
import io.github.xhr666.wristchat.ui.chat.Latex
import io.github.xhr666.wristchat.ui.RimScroll
import io.github.xhr666.wristchat.ui.common.RoundInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.xhr666.wristchat.data.ApiTranscript
import io.github.xhr666.wristchat.data.ChatMessage
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

    // ---------- 原生兜底:向量/三角/空上标 ----------

    @Test fun `A11 向量与三角`() {
        assertTrue("向量内容丢失", Latex.pretty("\\vec{AB}").contains("A"))
        assertTrue("缺组合箭头", Latex.pretty("\\vec{AB}").contains("\u20D7"))
        assertTrue("三角丢失", Latex.pretty("\\triangle").contains("△"))
    }

    @Test fun `A12 空上标不得残留空括号`() {
        val out = Latex.pretty("H^{\\ominus}")
        assertFalse("出现空括号: $out", out.contains("()"))
        assertTrue("圆圈运算符丢失: $out", out.contains("⊖"))
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

    // ---------- 流式渲染:未闭合符号先原样,闭合后才进 ready ----------

    @Test fun `S1 纯文本全部可渲染`() {
        val p = StreamRender.split("你好")
        assertEquals("你好", p.ready)
        assertEquals("", p.pending)
    }

    @Test fun `S2 未闭合加粗留在末尾`() {
        val p = StreamRender.split("先说 **没写完")
        assertEquals("先说 ", p.ready)
        assertEquals("**没写完", p.pending)
    }

    @Test fun `S3 闭合加粗全部可渲染`() {
        val p = StreamRender.split("**好**了")
        assertEquals("**好**了", p.ready)
        assertEquals("", p.pending)
    }

    @Test fun `S4 未闭合美元公式留在末尾`() {
        val p = StreamRender.split("式子 ${'$'}x^2")
        assertEquals("式子 ", p.ready)
        assertEquals("${'$'}x^2", p.pending)
    }

    @Test fun `S5 闭合美元公式全部可渲染`() {
        val p = StreamRender.split("式子 ${'$'}x^2${'$'} 完")
        assertEquals("式子 ${'$'}x^2${'$'} 完", p.ready)
        assertEquals("", p.pending)
    }

    @Test fun `S6 未闭合代码围栏留在末尾`() {
        val p = StreamRender.split("看\n```kt\nval a")
        assertEquals("看\n", p.ready)
        assertTrue(p.pending.startsWith("```"))
    }

    @Test fun `S7 未闭合方括号公式留在末尾`() {
        val p = StreamRender.split("前\\[a+b")
        assertEquals("前", p.ready)
        assertEquals("\\[a+b", p.pending)
    }

    // ---------- 会话金额 / 缓存命中率 ----------

    @Test fun `C1 消息花费大于会话头时用消息合计`() {
        // 会话头只记了标题请求 0.0424,消息上才是对话本身
        val shown = SessionAccounts.displayCost(0.0424, listOf(1.20, 3.04))
        assertEquals(4.24, shown, 0.0001)
    }

    @Test fun `C2 会话头已含标题附加费时不丢掉`() {
        val shown = SessionAccounts.displayCost(4.30, listOf(4.24))
        assertEquals(4.30, shown, 0.0001)
    }

    @Test fun `C3 缓存命中率读消息上的 hit miss`() {
        val rate = SessionAccounts.cacheHitRate(80, 20, 0, 0)
        assertEquals(0.8f, rate, 0.0001f)
    }

    @Test fun `C4 消息没有用量时命中率是 0`() {
        val rate = SessionAccounts.cacheHitRate(0, 0, 0, 0)
        assertEquals(0f, rate, 0.0001f)
    }

    @Test fun `C5 占用刚到 50MB 不提示,超过才提示`() {
        val limit = SessionAccounts.WARN_BYTES
        assertFalse(SessionAccounts.shouldWarn(limit))
        assertTrue(SessionAccounts.shouldWarn(limit + 1))
        assertFalse(SessionAccounts.shouldWarn(0))
    }

    // ---------- 圆屏页边 ----------

    @Test fun `R1 顶 12% 底 8%`() {
        assertEquals(27.96f, RoundInsets.topPad(233f), 0.01f)
        assertEquals(18.64f, RoundInsets.bottomPad(233f), 0.01f)
    }

    @Test fun `F1 pgfplots 不走绘图并留下可读文本`() {
        val src = "\\begin{tikzpicture}\\begin{axis}\\addplot{x};\\end{axis}\\end{tikzpicture}"
        assertTrue(FormulaText.unsupported(src))
        val shown = FormulaText.fallback(src)
        assertTrue("回退文本是空的", shown.isNotBlank())
    }

    @Test fun `F2 普通公式不算作图`() {
        assertFalse(FormulaText.unsupported("\\frac{1}{2}"))
    }

    @Test fun `T1 思考标题`() {
        assertEquals("思考中(用时3秒)", ThinkLabel.streaming(3))
        assertEquals("已思考(用时8秒)", ThinkLabel.done(8))
        assertEquals("已思考", ThinkLabel.done(0))
    }

    @Test fun `W1 联网工具不和记忆函数同时声明`() {
        val types = WebTools.toolTypes(search = true, read = true, memory = true)
        assertEquals(listOf(WebTools.SEARCH, WebTools.READ_URL), types)
        assertFalse(types.contains("memory_tool"))
        assertEquals(listOf(WebTools.SEARCH), WebTools.toolTypes(search = true, read = false, memory = true))
        assertEquals(listOf("memory_tool"), WebTools.toolTypes(search = false, read = false, memory = true))
    }

    @Test fun `W2 搜索过程能写成短句`() {
        assertEquals("正在搜索 今日新闻", ToolTrace.line("search", listOf("今日新闻"), "", false))
        assertEquals("已阅读 https://example.com", ToolTrace.line("read_link", emptyList(), "https://example.com", true))
        assertEquals("", ToolTrace.line("nope", emptyList(), "", false))
    }

    @Test fun `U1 设置根目录有分组标题且不含颜色模式`() {
        val titles = io.github.xhr666.wristchat.ui.SettingsCatalog.root.map { it.title }
        assertEquals("通用设置", titles.first())
        assertTrue(titles.contains("搜索服务"))
        assertTrue(titles.contains("数据备份"))
        assertFalse(titles.any { it.contains("颜色") })
        assertTrue(titles.indexOf("提供商") < titles.indexOf("搜索服务"))
    }

    @Test fun `N2 连接失败才会重试`() {
        assertTrue(io.github.xhr666.wristchat.data.NetRetry.again(500, 0, 2))
        assertTrue(io.github.xhr666.wristchat.data.NetRetry.again(0, 0, 2))
        assertFalse(io.github.xhr666.wristchat.data.NetRetry.again(400, 0, 2))
        assertFalse(io.github.xhr666.wristchat.data.NetRetry.again(500, 2, 2))
    }

    @Test fun `L4 两个反斜杠的命令要收成一个`() {
        val src = "\\\\frac{1}{2}"
        val out = io.github.xhr666.wristchat.data.LatexBodies.normalize(src)
        assertEquals("\\frac{1}{2}", out)
        assertFalse(out.startsWith("\\\\"))
    }

    @Test fun `E1 流式两段要拼起来，不能只留最后一段`() {
        val lines = listOf(
            """data: {"choices":[{"delta":{"content":"你","reasoning_content":null}}]}""",
            """data: {"choices":[{"delta":{"content":"好","reasoning_content":"先"}}]}""",
        )
        val (content, reasoning) = io.github.xhr666.wristchat.data.SseChunk.fold(lines)
        assertEquals("你好", content)
        assertEquals("先", reasoning)
        val onlyFirst = io.github.xhr666.wristchat.data.SseChunk.fold(lines.take(1)).first
        assertEquals("你", onlyFirst)
        assertFalse(onlyFirst == content)
    }

    @Test fun `L3 公式环境能被抽出来`() {
        val bodies = io.github.xhr666.wristchat.data.LatexBodies.extract("见 \\[a+b\\] 和 \$x\$")
        assertEquals(2, bodies.size)
        assertEquals("a+b", bodies[0])
        val soft = io.github.xhr666.wristchat.data.LatexBodies.soften("\\begin{align*} a &= b \\end{align*}")
        assertTrue(soft.contains("aligned"))
        assertFalse(soft.contains("align*"))
    }

    @Test fun `K1 有密钥的服务商名字带标记`() {
        assertEquals("DeepSeek（有 API Key）", io.github.xhr666.wristchat.data.ProviderLabel.of("DeepSeek", true))
        assertEquals("通义千问", io.github.xhr666.wristchat.data.ProviderLabel.of("通义千问", false))
    }

    @Test fun `U2 比已安装版本新的包才保留`() {
        assertTrue(io.github.xhr666.wristchat.data.UpdateApk.newerThanInstalled(53, 52))
        assertFalse(io.github.xhr666.wristchat.data.UpdateApk.newerThanInstalled(52, 52))
        assertFalse(io.github.xhr666.wristchat.data.UpdateApk.newerThanInstalled(51, 52))
    }

    @Test fun `S2 收起后把标题拉回原位，不跳到消息末尾`() {
        assertEquals(0f, io.github.xhr666.wristchat.ui.ScrollAnchor.hold(100f, 100f), 0.01f)
        assertEquals(36f, io.github.xhr666.wristchat.ui.ScrollAnchor.hold(80f, 116f), 0.01f)
        assertEquals(-20f, io.github.xhr666.wristchat.ui.ScrollAnchor.hold(100f, 80f), 0.01f)
        assertFalse(io.github.xhr666.wristchat.ui.ScrollAnchor.hold(10f, 80f) == 79f)
    }

    @Test fun `N1 JSON null 不会变成字符串 null`() {
        assertEquals("", JsonText.fromRaw(null))
        assertEquals("", JsonText.fromRaw(org.json.JSONObject.NULL))
        assertEquals("先想", JsonText.fromRaw("先想"))
        assertEquals("", JsonText.fromRaw(3))
    }

    @Test fun `F1 图表公式回退成说明而不是空白`() {
        val out = FormulaText.fallback("\\begin{axis}\\addplot{x};\\end{axis}")
        assertTrue(out.contains("图表"))
        assertFalse(out.isBlank())
    }

    @Test fun `F2 普通公式画失败时仍有可读文本`() {
        val out = FormulaText.fallback("\\sqrt{2}")
        assertEquals("√(2)", out)
    }

    @Test fun `T1 思考中和已思考文案`() {
        assertTrue(ThinkLabel.streaming(3).startsWith("思考中"))
        assertTrue(ThinkLabel.done(3).startsWith("已思考"))
        assertFalse(ThinkLabel.done(3).contains("思考中"))
    }

    @Test fun `H1 开头的助手消息不会发给接口`() {
        val out = ApiTranscript.prepare(listOf(
            ChatMessage(role = "assistant", content = "本地提示"),
            ChatMessage(role = "user", content = "现在可以吗"),
        ))
        assertEquals(1, out.size)
        assertEquals("user", out[0].role)
    }

    @Test fun `H2 连续两条助手只保留紧跟用户的那条`() {
        val out = ApiTranscript.prepare(listOf(
            ChatMessage(role = "user", content = "问"),
            ChatMessage(role = "assistant", content = "答"),
            ChatMessage(role = "assistant", content = "⚠️ 错误"),
        ))
        assertEquals(2, out.size)
        assertEquals("答", out[1].content)
    }

    @Test fun `H3 错误气泡不会再送回去`() {
        val out = ApiTranscript.prepare(listOf(
            ChatMessage(role = "user", content = "问"),
            ChatMessage(role = "assistant", content = "⚠️ an assistant message must follow a user message"),
            ChatMessage(role = "user", content = "再问"),
        ))
        assertEquals(listOf("问\n再问"), out.map { it.content })
    }

    @Test fun `H4 报错气泡丢掉之后新提问并进上一条用户消息`() {
        val out = ApiTranscript.build(
            listOf(
                ChatMessage(role = "user", content = "问"),
                ChatMessage(role = "assistant", content = "⚠️ an assistant message must follow a user message"),
            ),
            ChatMessage(role = "user", content = "现在可以吗"),
        )
        assertEquals(listOf("user"), out.map { it.role })
        assertEquals("问\n现在可以吗", out[0].content)
    }

    @Test fun `H5 压缩摘要放进前言,不单独占一条消息`() {
        val history = listOf(
            ChatMessage(role = "system", content = "对话摘要(自动压缩):\n之前在谈环保"),
            ChatMessage(role = "assistant", content = "先说结论"),
            ChatMessage(role = "user", content = "继续"),
        )
        assertEquals("对话摘要(自动压缩):\n之前在谈环保\n\n先说结论", ApiTranscript.preface(history))
        val out = ApiTranscript.prepare(history)
        assertEquals(listOf("user"), out.map { it.role })
    }

    @Test fun `S1 滑块停在右侧 44 度轨道里`() {
        val (start, len) = RimScroll.thumb(0f, 0.2f)
        assertTrue(start >= RimScroll.TRACK_START - 0.01f)
        assertTrue(start + len <= RimScroll.TRACK_START + RimScroll.TRACK_SWEEP + 0.01f)
        assertTrue(len <= RimScroll.TRACK_SWEEP)
        val (atEnd, lenEnd) = RimScroll.thumb(1f, 0.2f)
        assertTrue(atEnd > start)
        assertEquals(len, lenEnd, 0.01f)
    }

    @Test fun `R2 靠近圆边的左右留白大于圆心`() {
        val atTop = RoundInsets.chordInset(233f, 233f, RoundInsets.topPad(233f))
        val atCenter = RoundInsets.chordInset(233f, 233f, 116.5f)
        assertTrue("顶边留白应明显大于 0,实际 $atTop", atTop > 20f)
        assertTrue("圆心留白应接近 0,实际 $atCenter", atCenter < 1f)
        assertTrue(atTop > atCenter)
    }
}
