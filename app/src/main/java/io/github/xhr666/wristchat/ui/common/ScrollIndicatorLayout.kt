package io.github.xhr666.wristchat.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max

/**
 * 侧边滚动指示条(微思同款):滚动时出现,停止约 1.2s 自动淡出。
 * 用法:包住 RecyclerView/NestedScrollView,自动接管其滚动事件绘制细条。
 */
class ScrollIndicatorLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : android.widget.FrameLayout(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var barRect = RectF()
    private var progress = 0f     // 0..1 已滚比例
    private var extent = 1f       // 视口/内容(0..1),越小条越短
    private var alpha = 0f
    private var hideRunnable = Runnable { animateAlpha(0f) }
    private var fadeAnimator: ValueAnimator? = null

    init {
        paint.color = Color.WHITE
        paint.alpha = 0
        setWillNotDraw(false)
    }

    /** 从自身直接寻找并绑定子滚动视图 */
    fun bindChild() {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            when (child) {
                is RecyclerView -> child.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) { updateFromRecycler(rv) }
                })
                is NestedScrollView -> child.setOnScrollChangeListener { _, _, _, _, _ -> updateFromScrollView(child) }
                is ScrollView -> child.setOnScrollChangeListener { _, _, _, _, _ -> updateFromScrollView(child) }
            }
        }
    }



    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidate()
    }

    private fun updateFromRecycler(rv: RecyclerView) {
        val range = max(rv.computeVerticalScrollRange() - rv.computeVerticalScrollExtent(), 1)
        progress = (rv.computeVerticalScrollOffset() / range.toFloat()).coerceIn(0f, 1f)
        extent = (rv.computeVerticalScrollExtent() / rv.computeVerticalScrollRange().toFloat()).coerceIn(0.1f, 1f)
        showTemporarily()
    }

    private fun updateFromScrollView(sv: View) {
        val content = (sv as? ViewGroup)?.getChildAt(0)?.height ?: return
        val range = max(content - sv.height, 1)
        progress = (sv.scrollY / range.toFloat()).coerceIn(0f, 1f)
        extent = (sv.height / content.toFloat()).coerceIn(0.1f, 1f)
        showTemporarily()
    }

    private fun showTemporarily() {
        removeCallbacks(hideRunnable)
        animateAlpha(1f)
        postDelayed(hideRunnable, 1200)
    }

    private fun animateAlpha(target: Float) {
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(alpha, target).apply {
            duration = 200
            addUpdateListener { a ->
                alpha = a.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (alpha < 0.02f) return
        val w = width.toFloat()
        val h = height.toFloat()
        val barW = (2.5f * resources.displayMetrics.density)
        val margin = (2f * resources.displayMetrics.density)
        val barH = (h * extent).coerceAtLeast(18f)
        val top = progress * (h - barH)
        barRect.set(w - barW - margin, top, w - margin, top + barH)
        paint.color = Color.WHITE
        paint.alpha = (alpha * 110).toInt()
        canvas.drawRoundRect(barRect, barW / 2, barW / 2, paint)
    }
}
