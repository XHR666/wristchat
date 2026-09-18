package io.github.xhr666.wristchat.data

import android.content.Context
import java.io.File

/** 未发送草稿:实时落盘,关后台再打开也在(容量上限 8000 字符) */
object DraftStore {
    private const val MAX = 8000
    private fun file(ctx: Context) = File(ctx.filesDir, "draft.txt")

    fun save(ctx: Context, text: String) {
        try {
            val t = if (text.length > MAX) text.takeLast(MAX) else text
            file(ctx).writeText(t)
        } catch (_: Exception) {}
    }

    fun load(ctx: Context): String = try {
        val f = file(ctx)
        if (f.exists()) f.readText().take(MAX) else ""
    } catch (_: Exception) { "" }

    fun clear(ctx: Context) { try { file(ctx).delete() } catch (_: Exception) {} }
}
