package io.github.xhr666.wristchat.ui

/** 收起思考后，把标题拉回原来的屏幕位置，而不是跳到消息末尾。 */
object ScrollAnchor {
    fun hold(yBefore: Float, yAfter: Float): Float {
        if (yBefore.isNaN() || yAfter.isNaN()) return 0f
        val dy = yAfter - yBefore
        return if (kotlin.math.abs(dy) < 1f) 0f else dy
    }
}
