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
        installAnrWatchdog()
        io.github.xhr666.wristchat.data.AppLog.init(this)
    }

    /** 主线程心跳监视:卡死超过阈值时把主线程栈写入 filesDir/anr.log(诊断整表卡死) */
    @Volatile
    private var mainTick = System.currentTimeMillis()

    private fun installAnrWatchdog() {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val t = Thread {
            while (true) {
                try {
                    Thread.sleep(4000)
                    val stuckMs = System.currentTimeMillis() - mainTick
                    if (stuckMs > 8000) {
                        mainTick = System.currentTimeMillis() // 防重复刷
                        val sb = StringBuilder()
                        sb.append("=== ANR suspected, main stuck ~").append(stuckMs).append("ms | ")
                            .append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                            .append(" | v").append(BuildConfig.VERSION_NAME).append('\n')
                        Thread.getAllStackTraces().forEach { (thread, st) ->
                            if (thread.name == "main") {
                                sb.append("main stack:\n")
                                st.take(40).forEach { sb.append("    at ").append(it).append('\n') }
                            }
                        }
                        val anr = File(filesDir, "anr.log")
                        anr.appendText(sb.toString())
                        if (anr.length() > 100_000) anr.writeText(anr.readText().takeLast(90_000))
                    }
                } catch (e: InterruptedException) {
                    return@Thread
                } catch (e: Exception) {
                    // 忽略
                }
            }
        }
        t.isDaemon = true
        t.start()
        // 主线程每 2 秒打一次心跳;卡死则心跳停止,看门狗可感知
        val beat = object : Runnable {
            override fun run() {
                mainTick = System.currentTimeMillis()
                mainHandler.postDelayed(this, 2000)
            }
        }
        mainHandler.post(beat)
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

    /** ANR/卡死堆栈(看门狗写入);查看崩溃日志卡死时可看这个定位主线程 */
    fun anrLogText(): String {
        val f = File(filesDir, "anr.log")
        return if (f.exists()) f.readText().ifBlank { "(空)" } else "(无 ANR 记录)"
    }
}
