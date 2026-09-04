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
            try {
                val out = session.openWrite("pkg", 0, -1)
                apk.inputStream().use { input -> out.use { o ->
                    val buf = ByteArray(64 * 1024)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) o.write(buf, 0, n)
                } }
                session.fsync(out)
            } finally {
                session.close()
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
            InstallerResult.Submitted(sessionId)
        } catch (e: Exception) {
            InstallerResult.Fail(e.message ?: "安装失败")
        }
    }
}
