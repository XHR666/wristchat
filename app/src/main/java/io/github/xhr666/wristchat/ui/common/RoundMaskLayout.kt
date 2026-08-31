package io.github.xhr666.wristchat.ui.common

import android.content.Context
import android.graphics.Outline
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout

/**
 * 圆屏遮罩容器:
 * - 圆屏(isScreenRound):裁剪为圆
 * - 方屏:裁剪为圆角矩形(12dp)
 */
class RoundMaskLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val isRound = context.resources.configuration.isScreenRound
    private val cornerRadius = (12 * context.resources.displayMetrics.density).toInt()

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val w = view.width
                val h = view.height
                if (w <= 0 || h <= 0) return
                if (isRound) {
                    val radius = minOf(w, h) / 2f
                    outline.setRoundRect(0, 0, w, h, radius)
                } else {
                    outline.setRoundRect(0, 0, w, h, cornerRadius.toFloat())
                }
            }
        }
        clipToOutline = true
    }

    /** 圆屏安全区:内接正方形留白(px) */
    fun circleInsetPx(): Int {
        if (!isRound) return 0
        val size = minOf(width, height)
        val ratio = (1.0 - Math.sqrt(2.0) / 2.0) / 2.0
        return (size * ratio).toInt()
    }
}
