package io.github.xhr666.wristchat.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

/** 系统 PackageInstaller 会话安装(比 ACTION_VIEW 在手表上更可靠,可拿到真实结果) */
sealed class InstallerResult {
    object NeedPermission : InstallerResult()
    data class Submitted(val id: Int) : InstallerResult()
    data class Fail(val msg: String) : InstallerResult()
}

object Installer {

    const val ACTION_RESULT = "io.github.xhr666.wristchat.INSTALL_RESULT"

    fun canInstall(ctx: Context): Boolean =
        ctx.packageManager.canRequestPackageInstalls()

    fun openManageUnknownSources(ctx: Context) {
        val intent = Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            android.net.Uri.parse("package:${ctx.packageName}")
        )
        try { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
    }

    fun submit(ctx: Context, apk: File): InstallerResult {
        return try {
            val pm = ctx.packageManager
            val info = pm.getPackageArchiveInfo(apk.absolutePath, 0) ?: return InstallerResult.Fail("APK 解析失败")
            val pkg = info.packageName
            val installer = pm.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(pkg)
            }
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            var committed = false
            try {
                // 传实际大小而非 -1:个别 OEM PackageInstaller 不支持未知长度(H18)
                val out = session.openWrite("pkg", 0, apk.length())
                apk.inputStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) out.write(buf, 0, n)
                    out.flush()
                    session.fsync(out)   // 必须在 close 之前 fsync
                    out.close()
                }
                val pi = PendingIntent.getBroadcast(
                    ctx, sessionId,
                    Intent(ACTION_RESULT).setPackage(ctx.packageName),
                    if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT,
                )
                // API 34 stub 未公开 commitSession,设备(API30)存在 → 反射调用
                try {
                    val m = PackageInstaller::class.java.getMethod(
                        "commitSession", Int::class.javaPrimitiveType, android.content.IntentSender::class.java)
                    m.invoke(installer, sessionId, pi.intentSender)
                } catch (nsme: NoSuchMethodException) {
                    val m2 = PackageInstaller::class.java.getMethod(
                        "commitSession", Int::class.javaPrimitiveType, PendingIntent::class.java)
                    m2.invoke(installer, sessionId, pi)
                }
                committed = true
                InstallerResult.Submitted(sessionId)
            } finally {
                try { session.close() } catch (_: Exception) {}
                // 未提交则放弃会话,避免残留占用系统 session(H17)
                if (!committed) { try { session.abandon() } catch (_: Exception) {} }
            }
        } catch (e: Exception) {
            InstallerResult.Fail(e.message ?: "安装失败")
        }
    }
}
