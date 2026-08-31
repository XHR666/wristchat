package io.github.xhr666.wristchat.ui.common

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * 表冠滚动:拦截 ACTION_SCROLL 通用触控事件,路由给**当前可见页**的可滚动列表。
 * - 单次事件限幅(OPPO 表冠一格可能产生很大的 delta,不限幅会一跳到底)
 * - 灵敏度可调
 * - 兜底:音量键/DPAD 上下键映射为滚动
 */
object CrownScroll {

    /** 灵敏度系数(默认 1.0,设置里可调) */
    var sensitivity: Float = 1.0f

    /** 单次事件最大滚动像素(限幅) */
    private const val MAX_STEP_PX = 36

    /** 每 1.0 delta 对应像素 */
    private const val PX_PER_UNIT = 30f

    fun handleGenericMotion(rootScope: View, ev: MotionEvent): Boolean {
        if (ev.action != MotionEvent.ACTION_SCROLL) return false
        val raw = ev.getAxisValue(MotionEvent.AXIS_VSCROLL) * sensitivity
        if (abs(raw) < 0.05f) return false
        val target = findScrollable(rootScope)
        if (target == null) return false
        val px = (raw * PX_PER_UNIT).toInt().coerceIn(-MAX_STEP_PX, MAX_STEP_PX)
        target.scrollBy(0, px)
        return true
    }

    fun handleKey(rootScope: View, keyCode: Int): Boolean {
        val delta = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> MAX_STEP_PX
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_DPAD_UP -> -MAX_STEP_PX
            else -> return false
        }
        val target = findScrollable(rootScope) ?: return false
        target.scrollBy(0, delta)
        return true
    }

    /** 在给定视图范围内找到当前可滚动的 RecyclerView(只搜当前页,避免滚错页) */
    fun findScrollable(rootScope: View): RecyclerView? {
        val list = ArrayList<View>()
        collectViews(rootScope, list)
        // 逆序遍历:后添加的(视觉上层)优先
        for (i in list.indices.reversed()) {
            val v = list[i]
            if (v is RecyclerView &&
                (v.canScrollVertically(1) || v.canScrollVertically(-1))
            ) {
                return v
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
