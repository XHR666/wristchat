package io.github.xhr666.wristchat.ui.common

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.xhr666.wristchat.R
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 自绘圆屏安全弹窗:替换原生 AlertDialog(原生弹窗圆屏上被切边)。
 * - 尺寸:宽 = min(圆内接正方形宽 - 边距, 200dp);高上限 70% 屏高
 * - 内容超高时在内容区滚动,**按钮行固定在卡片底部**
 * - 支持确认倒计时(防误触,用于"清理全部数据")
 */
class WristDialog private constructor(context: Context) {

    private val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
    private lateinit var title: TextView
    private lateinit var contentHost: FrameLayout
    private lateinit var btnPositive: Button
    private lateinit var btnNegative: Button
    private lateinit var btnRow: LinearLayout

    private var onPositive: (() -> Unit)? = null
    private var onNegative: (() -> Unit)? = null
    private var countdownTimer: CountDownTimer? = null
    private var countdownSeconds = 0
    private val positiveText = StringBuilder()

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
        btnRow = root.findViewById(R.id.btnRow)

        // 尺寸:宽 = min(圆内接正方形 - 2×12dp, 200dp);由 dialog 窗口宽高决定
        val dm = context.resources.displayMetrics
        val density = dm.density
        val screenMin = min(dm.widthPixels, dm.heightPixels)
        val inscribed = (screenMin * (sqrt(2.0) / 2.0)).toInt() // 内接正方形
        val maxCardW = min(inscribed - (24 * density).toInt(), (200 * density).toInt()).coerceAtLeast((140 * density).toInt())

        val card = root.findViewById<View>(R.id.card)
        card.post {
            val lp = card.layoutParams
            lp.width = maxCardW
            card.layoutParams = lp
            // 高上限:70% 屏高
            val maxH = (dm.heightPixels * 0.7f).toInt()
            if (card.height > maxH) {
                card.layoutParams = FrameLayout.LayoutParams(maxCardW, maxH)
            }
        }

        btnPositive.setOnClickListener { onPositive?.invoke(); dismissInternal() }
        btnNegative.setOnClickListener { onNegative?.invoke(); dismissInternal() }
    }

    fun setTitle(t: String): WristDialog { title.text = t; return this }
    fun setTitleVisible(v: Boolean): WristDialog { title.visibility = if (v) View.VISIBLE else View.GONE; return this }
    fun setCancelable(c: Boolean): WristDialog {
        dialog.setCancelable(c); dialog.setCanceledOnTouchOutside(c); return this
    }
    fun setPositive(text: String, onClick: (() -> Unit)?): WristDialog {
        positiveText.setLength(0); positiveText.append(text)
        btnPositive.text = text; onPositive = onClick; return this
    }
    fun setNegative(text: String, onClick: (() -> Unit)?): WristDialog {
        btnNegative.text = text; onNegative = onClick; return this
    }
    fun hidePositive(): WristDialog { btnPositive.visibility = View.GONE; return this }
    fun hideNegative(): WristDialog { btnNegative.visibility = View.GONE; return this }

    /** 确认倒计时:秒数内「确定」禁用并显示剩余秒数 */
    fun withPositiveCountdown(seconds: Int): WristDialog {
        countdownSeconds = seconds
        return this
    }

    private fun startCountdownIfNeeded() {
        if (countdownSeconds <= 0) return
        btnPositive.isEnabled = false
        var remain = countdownSeconds
        btnPositive.text = "${positiveText}($remain)"
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(countdownSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remain = (millisUntilFinished / 1000 + 1).toInt()
                btnPositive.text = "${positiveText}($remain)"
            }
            override fun onFinish() {
                btnPositive.isEnabled = true
                btnPositive.text = positiveText.toString()
            }
        }.start()
    }

    private fun textColor(): Int {
        val tv = android.util.TypedValue()
        dialog.context.theme.resolveAttribute(R.attr.wristText, tv, true)
        return tv.data
    }

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
            isHorizontalScrollBarEnabled = false
            addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        contentHost.addView(scroll, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return this
    }

    fun show() {
        startCountdownIfNeeded()
        dialog.show()
    }

    private fun dismissInternal() {
        countdownTimer?.cancel()
        dialog.dismiss()
    }

    fun dismiss() {
        countdownTimer?.cancel()
        dialog.dismiss()
    }

    companion object {
        fun build(context: Context): WristDialog = WristDialog(context)

        fun singleChoice(context: Context, title: String, items: List<String>, checked: Int, onPick: (Int) -> Unit) {
            val d = build(context).setTitle(title).hideNegative().setPositive("取消", null)
            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            items.forEachIndexed { i, s ->
                val row = TextView(context).apply {
                    text = (if (i == checked) "● " else "○ ") + s
                    setTextColor(if (i == checked) context.getColor(R.color.accent) else d.textColor())
                    textSize = 14f
                    setPadding(8, 12, 8, 12)
                    isClickable = true
                    setOnClickListener { d.dismiss(); onPick(i) }
                }
                column.addView(row)
            }
            d.setView(column)
            d.show()
        }

        fun items(context: Context, title: String, items: List<String>, onPick: (Int) -> Unit, neutralText: String? = null, onNeutral: (() -> Unit)? = null) {
            val d = build(context).setTitle(title).setPositive("关闭", null)
            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            items.forEachIndexed { i, s ->
                val row = TextView(context).apply {
                    text = s
                    setTextColor(d.textColor())
                    textSize = 14f
                    setPadding(8, 12, 8, 12)
                    isClickable = true
                    setOnClickListener { d.dismiss(); onPick(i) }
                }
                column.addView(row)
            }
            if (neutralText != null) {
                val neutral = Button(context)
                neutral.text = neutralText
                neutral.setTextColor(context.getColor(R.color.accent))
                neutral.setOnClickListener { d.dismiss(); onNeutral?.invoke() }
                column.addView(neutral)
            }
            d.setView(column)
            d.show()
        }
    }
}
