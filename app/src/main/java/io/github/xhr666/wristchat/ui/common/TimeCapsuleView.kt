package io.github.xhr666.wristchat.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 顶部时间胶囊(微思同款):
 * - alwaysVisible=true:常驻顶部(聊天页)
 * - 否则:列表滚动到最顶端时从上方滑入,离开顶部滑出
 */
class TimeCapsuleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextView(context, attrs) {

    var alwaysVisible: Boolean = false
        set(v) {
            field = v
            if (v) show() else if (!atTop) hide()
        }

    private var atTop = true
    private var shown = false
    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val ticker = java.util.Timer()

    init {
        text = fmt.format(Date())
        setTextColor(Color.parseColor("#9AA4AF"))
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(2), dp(8), dp(2))
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(0x33FFFFFF.toInt())
        }
        alpha = 0f
        ticker.schedule(object : java.util.TimerTask() {
            override fun run() {
                post { text = fmt.format(Date()) }
            }
        }, 30_000, 30_000)
    }

    /** 绑定滚动容器:滚动到顶 → 显示;离开 → 隐藏 */
    fun bind(scrollable: View) {
        when (scrollable) {
            is RecyclerView -> scrollable.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    onScrollChanged(!rv.canScrollVertically(-1))
                }
            })
            is NestedScrollView, is ScrollView -> {
                scrollable.setOnScrollChangeListener { _, _, _, _, _ ->
                    onScrollChanged(scrollable.scrollY <= 0)
                }
            }
        }
    }

    private fun onScrollChanged(atTopNow: Boolean) {
        atTop = atTopNow
        if (alwaysVisible) return
        if (atTopNow) show() else hide()
    }

    private fun show() {
        if (shown) return
        shown = true
        visibility = View.VISIBLE
        animate().alpha(1f).translationY(0f).setDuration(220).start()
    }

    private fun hide() {
        if (!shown) return
        shown = false
        animate().alpha(0f).translationY(-dp(14).toFloat()).setDuration(220).withEndAction {
            visibility = View.GONE
        }.start()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ticker.cancel()
    }
}
