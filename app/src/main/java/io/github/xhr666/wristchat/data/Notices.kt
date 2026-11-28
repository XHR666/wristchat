package io.github.xhr666.wristchat.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** 通知只写状态，不写回复正文，也不写密钥。 */
object Notices {
    private const val CH = "wristchat"

    fun replyReady(ctx: Context) {
        notify(ctx, 21, "回复已完成", "回到应用即可查看")
    }

    fun updateReady(ctx: Context, tag: String) {
        val label = tag.trim().take(40).ifBlank { "有新版本" }
        notify(ctx, 22, "有可更新版本", label)
    }

    private fun notify(ctx: Context, id: Int, title: String, text: String) {
        val app = ctx.applicationContext
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = app.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CH) == null) {
                nm.createNotificationChannel(NotificationChannel(CH, "WristChat", NotificationManager.IMPORTANCE_DEFAULT))
            }
        }
        val n = NotificationCompat.Builder(app, CH)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(id, n) }
    }
}
