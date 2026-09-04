package io.github.xhr666.wristchat.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 轻量运行日志(写 filesDir/app.log, 供诊断更新/崩溃类问题) */
object AppLog {
    @Volatile private var ctx: Context? = null
    fun init(context: Context) { ctx = context.applicationContext }
    fun i(tag: String, msg: String) {
        val c = ctx ?: return
        try {
            val f = File(c.filesDir, "app.log")
            val line = "${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())} [$tag] $msg\n"
            f.appendText(line)
            if (f.length() > 100_000) f.writeText(f.readText().takeLast(80_000))
        } catch (_: Exception) {}
    }
    fun read(): String {
        val c = ctx ?: return "(未初始化)"
        val f = File(c.filesDir, "app.log")
        return if (f.exists()) f.readText().takeLast(4000).ifBlank { "(空)" } else "(无日志)"
    }
}
