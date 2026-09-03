package io.github.xhr666.wristchat.ui.common

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * 表冠滚动:拦截 ACTION_SCROLL,路由给**当前可见页**的可滚动视图。
 * - 支持 RecyclerView 与 NestedScrollView/ScrollView(余额页等)
 * - 单次限幅 + 平滑减速(仿微思手感:一格一小段,连滚连续)
 * - 灵敏度可调
 */
object CrownScroll {

    var sensitivity: Float = 1.0f

    /** 单次事件最大滚动像素(限幅,手感调优:24px ≈ 1-2 行) */
    private const val MAX_STEP_PX = 24

    /** 每 1.0 delta 对应像素(调小 → 更细腻) */
    private const val PX_PER_UNIT = 16f

    fun handleGenericMotion(rootScope: View, ev: MotionEvent): Boolean {
        if (ev.action != MotionEvent.ACTION_SCROLL) return false
        // 方向:表冠"向下" ≈ 手指向下滑 = 内容向上移动
        val raw = -ev.getAxisValue(MotionEvent.AXIS_VSCROLL) * sensitivity
        if (abs(raw) < 0.05f) return false
        val target = findScrollable(rootScope)
        if (target == null) return false
        val px = (raw * PX_PER_UNIT).toInt().coerceIn(-MAX_STEP_PX, MAX_STEP_PX)
        scrollBy(target, px)
        return true
    }

    fun handleKey(rootScope: View, keyCode: Int): Boolean {
        val delta = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> MAX_STEP_PX
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_DPAD_UP -> -MAX_STEP_PX
            else -> return false
        }
        val target = findScrollable(rootScope) ?: return false
        scrollBy(target, delta)
        return true
    }

    /** 平滑滚动:带减速动画,手感接近微思;方向保证不越界 */
    private fun scrollBy(target: View, px: Int) {
        when (target) {
            is RecyclerView -> {
                val maxDy = if (px > 0) {
                    // 向下滚(内容上移):可滚距离
                    val total = target.computeVerticalScrollRange() - target.computeVerticalScrollExtent()
                    val can = target.computeVerticalScrollOffset()
                    (total - can).coerceAtLeast(0)
                } else {
                    target.computeVerticalScrollOffset()
                }
                val dy = if (px > 0) px.coerceAtMost(maxDy) else px.coerceAtLeast(-maxDy)
                if (dy != 0) target.smoothScrollBy(0, dy)
            }
            is ScrollView -> {
                val maxScroll = target.getChildAt(0)?.height?.minus(target.height) ?: 0
                val newY = (target.scrollY + px).coerceIn(0, maxScroll.coerceAtLeast(0))
                target.smoothScrollTo(0, newY)
            }
            is NestedScrollView -> {
                val maxScroll = target.getChildAt(0)?.height?.minus(target.height) ?: 0
                val newY = (target.scrollY + px).coerceIn(0, maxScroll.coerceAtLeast(0))
                target.smoothScrollTo(0, newY)
            }
        }
    }

    /** 找到给定范围内当前可滚动的视图(只搜当前页,避免滚错页) */
    private fun findScrollable(rootScope: View): View? {
        val list = ArrayList<View>()
        collectViews(rootScope, list)
        for (i in list.indices.reversed()) {
            val v = list[i]
            when (v) {
                is RecyclerView -> if (v.canScrollVertically(1) || v.canScrollVertically(-1)) return v
                is ScrollView, is NestedScrollView ->
                    if (v.scrollY > 0 || (v.getChildAt(0)?.height ?: 0) > v.height) return v
            }
        }
        return null
    }

    private fun collectViews(view: View, out: MutableList<View>) {
        if (view.visibility != View.VISIBLE) return
        out.add(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collectViews(view.getChildAt(i), out)
        }
    }
}
