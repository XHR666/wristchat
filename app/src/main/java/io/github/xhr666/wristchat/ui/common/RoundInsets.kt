package io.github.xhr666.wristchat.ui.common

import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 圆屏安全区计算(233dp 圆,density 2.0):
 * 圆方程 x² + y² = r²,在离圆心 dy 处,可见半宽 = sqrt(r² - dy²),
 * 水平安全缩进 = r - halfW。同一高度的内容缩进 inset 后即不被圆边裁切。
 */
object RoundInsets {

    /** 圆屏内容顶边:直径的 12%(heybox Lite 的圆屏页边)。 */
    fun topPad(diameter: Float): Float = diameter * 0.12f

    /** 圆屏内容底边:直径的 8%。 */
    fun bottomPad(diameter: Float): Float = diameter * 0.08f

    /**
     * 在距顶部 y 处,内容左右要收进圆里的距离。
     * 半径按宽高里较小的一边,和手表的圆屏一致。
     */
    fun chordInset(containerW: Float, containerH: Float, yFromTop: Float): Float {
        val r = min(containerW, containerH) / 2f
        if (r <= 0f) return 0f
        val dy = abs(yFromTop - containerH / 2f)
        if (dy >= r) return r
        val halfW = sqrt(r * r - dy * dy)
        return (r - halfW).coerceAtLeast(0f)
    }

    /** 计算某个垂直位置(距容器顶部的像素)所需的水平缩进(px) */
    fun horizontalInsetPx(container: View, centerYFromTopPx: Float): Int {
        val r = min(container.width, container.height) / 2f
        if (r <= 0f) return 0
        val dy = abs(centerYFromTopPx - container.height / 2f)
        if (dy >= r) return r.toInt()
        val halfW = sqrt(r * r - dy * dy)
        return (r - halfW).toInt()
    }
}
