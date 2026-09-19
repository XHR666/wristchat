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
                // 用公开 API(API 21+)提交会话。
                // 之前反射 PackageInstaller.commitSession(...) 是隐藏方法:API 34 stub 没有、
                // 设备上反射也失败(日志里那条 commitSession [int, class PendingIntent]),
                // 所以"安装失败"。Session.commit(IntentSender) 是公开接口,直接调用即可。
                session.commit(pi.intentSender)
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

    /**
     * 用系统安装器界面安装(与商店类应用一致的做法):
     * ACTION_VIEW + FileProvider 的 content:// URI → 系统弹出安装确认界面。
     * 失败再退到 ACTION_INSTALL_PACKAGE,最后才是 PackageInstaller 静默会话。
     * @return null 表示已成功拉起安装器;否则返回错误文案
     */
    fun launchSystemInstaller(ctx: Context, apk: File): String? {
        return try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                ctx, "${ctx.packageName}.fileprovider", apk)
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { ctx.startActivity(view); return null } catch (_: Exception) {}
            val legacy = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { ctx.startActivity(legacy); return null } catch (_: Exception) {}
            // 兜底:会话安装(部分机型没有安装器 Activity)
            when (val r = submit(ctx, apk)) {
                is InstallerResult.Submitted -> "已通过系统安装服务提交;若没有弹窗,请到设置里再点一次"
                is InstallerResult.Fail -> "安装失败:${r.msg}"
                is InstallerResult.NeedPermission -> "需要先允许安装未知应用"
            }
        } catch (e: Exception) { "安装失败:${e.message}" }
    }
}
