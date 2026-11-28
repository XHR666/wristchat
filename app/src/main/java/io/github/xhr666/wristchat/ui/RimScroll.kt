package io.github.xhr666.wristchat.ui

/** 右侧短弧进度条的角度。0° 在 3 点钟,正方向为顺时针。轨道只有 44°,贴着圆边。 */
object RimScroll {
    const val TRACK_START = -22f
    const val TRACK_SWEEP = 44f

    fun thumb(progress: Float, visibleRatio: Float): Pair<Float, Float> {
        val p = progress.coerceIn(0f, 1f)
        val len = (visibleRatio.coerceIn(0f, 1f) * TRACK_SWEEP).coerceIn(7f, TRACK_SWEEP)
        val start = (TRACK_SWEEP - len) * p + TRACK_START
        return start to len
    }
}
