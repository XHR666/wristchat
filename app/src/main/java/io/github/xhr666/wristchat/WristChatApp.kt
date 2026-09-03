package io.github.xhr666.wristchat

import android.app.Application
import io.github.xhr666.wristchat.data.SettingsStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WristChatApp : Application() {
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore.newInstance(this)
        settings.versionName = BuildConfig.VERSION_NAME
        installCrashLog()
    }

    /** 未捕获异常写入 filesDir/crash.log(设置→关于→查看崩溃日志) */
    private fun installCrashLog() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val log = File(filesDir, "crash.log")
                val entry = buildString {
                    append("=== ")
                    append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                    append(" | thread=").append(thread.name).append(" | v").append(BuildConfig.VERSION_NAME).append('\n')
                    append(sw.toString()).append('\n')
                }
                log.appendText(entry)
                if (log.length() > 100_000) {
                    log.writeText(log.readText().takeLast(90_000))
                }
            } catch (e: Exception) {
            }
            // 交给系统默认处理(保证闪退提示仍在)
            val prev = Thread.getDefaultUncaughtExceptionHandler()
            prev?.uncaughtException(thread, throwable)
        }
    }

    fun crashLogText(): String {
        val f = File(filesDir, "crash.log")
        return if (f.exists()) f.readText().ifBlank { "(空)" } else "(无崩溃记录)"
    }
}
