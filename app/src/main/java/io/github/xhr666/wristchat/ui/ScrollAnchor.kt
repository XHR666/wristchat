package io.github.xhr666.wristchat.ui

/** 列表项变矮之后，把滚动偏移夹在这项里面，避免跳到别的消息。 */
object ScrollAnchor {
    fun clamp(offset: Int, itemSize: Int): Int {
        if (itemSize <= 1) return 0
        return offset.coerceIn(0, itemSize - 1)
    }
}
