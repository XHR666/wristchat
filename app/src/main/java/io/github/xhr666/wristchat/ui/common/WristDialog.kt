package io.github.xhr666.wristchat.ui.common

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.xhr666.wristchat.R

/**
 * 自绘圆屏安全弹窗:替换原生 AlertDialog(原生弹窗在圆屏上会被切边)。
 * - 半透明遮罩 + 居中圆角卡片,卡片宽度按圆屏安全区动态收窄
 * - 高度受限,内容超高时自动滚动
 */
class WristDialog private constructor(context: Context) {

    private val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
    private lateinit var title: TextView
    private lateinit var contentHost: FrameLayout
    private lateinit var btnPositive: Button
    private lateinit var btnNegative: Button
    private var positiveVisible = true
    private var negativeVisible = true

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(false)
        val root = View.inflate(context, R.layout.dialog_wrist, null) as ViewGroup
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        title = root.findViewById(R.id.tvTitle)
        contentHost = root.findViewById(R.id.contentHost)
        btnPositive = root.findViewById(R.id.btnPositive)
        btnNegative = root.findViewById(R.id.btnNegative)

        // 圆屏安全宽度:卡片不超过圆内接宽度(取中心高度,留 8dp 余量)
        val dm = context.resources.displayMetrics
        val screenMin = minOf(dm.widthPixels, dm.heightPixels)
        val density = dm.density
        val r = screenMin / 2f
        val maxCardW = ((r - 8 * density) * 2f * 0.92f).toInt() // 略小于内接宽度
        val card = root.findViewById<View>(R.id.card)
        card.post {
            val lp = card.layoutParams
            lp.width = maxCardW.coerceAtMost(card.width.coerceAtLeast(maxCardW))
            card.layoutParams = lp
        }

        btnPositive.setOnClickListener { onPositive?.invoke(); dialog.dismiss() }
        btnNegative.setOnClickListener { onNegative?.invoke(); dialog.dismiss() }
    }

    private var onPositive: (() -> Unit)? = null
    private var onNegative: (() -> Unit)? = null

    fun setTitle(t: String): WristDialog { title.text = t; return this }
    fun setTitleVisible(v: Boolean): WristDialog { title.visibility = if (v) View.VISIBLE else View.GONE; return this }
    fun setCancelable(c: Boolean): WristDialog { dialog.setCancelable(c); dialog.setCanceledOnTouchOutside(c); return this }
    fun setPositive(text: String, onClick: (() -> Unit)?): WristDialog {
        btnPositive.text = text; onPositive = onClick; return this
    }
    fun setNegative(text: String, onClick: (() -> Unit)?): WristDialog {
        btnNegative.text = text; onNegative = onClick; return this
    }
    fun hidePositive(): WristDialog { btnPositive.visibility = View.GONE; return this }
    fun hideNegative(): WristDialog { btnNegative.visibility = View.GONE; return this }

    /** 设置内容:消息文本或自定义 View(自动包滚动) */
    fun setMessage(msg: String): WristDialog {
        val tv = TextView(dialog.context).apply {
            text = msg
            setTextColor(textColor())
            textSize = 13f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(4, 2, 4, 2)
        }
        return setView(tv)
    }

    fun setView(view: View): WristDialog {
        val scroll = ScrollView(dialog.context).apply {
            isVerticalScrollBarEnabled = false
            addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        contentHost.addView(scroll, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // 高度限制
        val dm = dialog.context.resources.displayMetrics
        contentHost.post {
            val maxH = (dm.heightPixels * 0.55f).toInt()
            if (contentHost.height > maxH) {
                contentHost.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxH)
            }
        }
        return this
    }

    fun setViewFixedHeight(view: View, heightPx: Int): WristDialog {
        val scroll = ScrollView(dialog.context).apply {
            isVerticalScrollBarEnabled = false
            addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        contentHost.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx))
        return this
    }

    fun show() { dialog.show() }

    private fun textColor(): Int {
        val tv = android.util.TypedValue()
        dialog.context.theme.resolveAttribute(R.attr.wristText, tv, true)
        return tv.data
    }

    fun dismiss() { dialog.dismiss() }

    companion object {
        fun build(context: Context): WristDialog = WristDialog(context)

        /** 单选项 */
        fun singleChoice(context: Context, title: String, items: List<String>, checked: Int, onPick: (Int) -> Unit) {
            val d = build(context).setTitle(title).hideNegative().setPositive("取消", null)
            val column = android.widget.LinearLayout(context).apply { orientation = android.widget.LinearLayout.VERTICAL }
            items.forEachIndexed { i, s ->
                val row = TextView(context).apply {
                    text = (if (i == checked) "● " else "○ ") + s
                    setTextColor(if (i == checked) context.getColor(R.color.accent) else d.textColor())
                    textSize = 14f
                    setPadding(8, 10, 8, 10)
                    isClickable = true
                    setOnClickListener { d.dismiss(); onPick(i) }
                }
                column.addView(row)
            }
            d.setView(column)
            d.show()
        }

        /** 条目列表(点击一项回调;可选中立按钮) */
        fun items(context: Context, title: String, items: List<String>, onPick: (Int) -> Unit, neutralText: String? = null, onNeutral: (() -> Unit)? = null) {
            val d = build(context).setTitle(title).setPositive("关闭", null)
            val column = android.widget.LinearLayout(context).apply { orientation = android.widget.LinearLayout.VERTICAL }
            items.forEachIndexed { i, s ->
                val row = TextView(context).apply {
                    text = s
                    setTextColor(d.textColor())
                    textSize = 14f
                    setPadding(8, 10, 8, 10)
                    isClickable = true
                    setOnClickListener { d.dismiss(); onPick(i) }
                }
                column.addView(row)
            }
            d.setView(column)
            if (neutralText != null) {
                val neutral = Button(context)
                neutral.text = neutralText
                neutral.setTextColor(context.getColor(R.color.accent))
                neutral.setOnClickListener { d.dismiss(); onNeutral?.invoke() }
                column.addView(neutral)
            }
            d.show()
        }
    }
}
