package io.github.xhr666.wristchat.ui.common

import android.app.Activity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * 表冠滚动:拦截 ACTION_SCROLL 通用触控事件,路由给目标 RecyclerView。
 * 兜底:音量键/DPAD 上下键映射为滚动。
 * 用法:Activity 重写 dispatchGenericMotionEvent/dispatchKeyEvent 调用本类。
 */
object CrownScroll {

    /** 灵敏度系数(默认 1.0) */
    var sensitivity: Float = 1.0f

    fun handleGenericMotion(activity: Activity, ev: MotionEvent): Boolean {
        if (ev.action != MotionEvent.ACTION_SCROLL) return false
        val delta = ev.getAxisValue(MotionEvent.AXIS_VSCROLL) * sensitivity
        if (delta == 0f) return false
        val target = findScrollable(activity)
        if (target == null) return false
        val px = (delta * 48).toInt() // 一格约 48px
        target.scrollBy(0, px)
        return true
    }

    fun handleKey(activity: Activity, keyCode: Int): Boolean {
        val delta = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> 64
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_DPAD_UP -> -64
            else -> return false
        }
        val target = findScrollable(activity) ?: return false
        target.smoothScrollBy(0, delta)
        return true
    }

    /** 找到当前可滚动的 RecyclerView(遍历可见 fragment 根视图) */
    private fun findScrollable(activity: Activity): RecyclerView? {
        val root = activity.window.decorView
        val list = ArrayList<View>()
        collectViews(root, list)
        // 优先最后一个(最上层/当前可见)
        for (i in list.indices.reversed()) {
            val v = list[i]
            if (v is RecyclerView && v.canScrollVertically(1) || v is RecyclerView && v.canScrollVertically(-1)) {
                return v
            }
        }
        return null
    }

    private fun collectViews(view: View, out: MutableList<View>) {
        out.add(view)
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) collectViews(view.getChildAt(i), out)
        }
    }
}
