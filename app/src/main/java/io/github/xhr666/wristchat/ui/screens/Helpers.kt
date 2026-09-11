package io.github.xhr666.wristchat.ui.screens

import android.content.Context
import android.net.Uri
import io.github.xhr666.wristchat.data.Installer
import io.github.xhr666.wristchat.data.InstallerResult

private const val LICENSE_TEXT = """
本项目:MIT License
第三方组件:
- ZXing core (Apache-2.0)
- Markwon (Apache-2.0)
- KaTeX (MIT) / marked.js (MIT)
- AndroidX / Jetpack Compose (Apache-2.0)
- kotlinx-coroutines (Apache-2.0)
"""

suspend fun installRelease(
    ctx: Context, repo: io.github.xhr666.wristchat.data.UpdateRepository, info: io.github.xhr666.wristchat.data.ReleaseInfo,
    onProgress: (Long, Long) -> Unit = { _, _ -> },
): String {
    val dir = java.io.File(ctx.cacheDir, "updates").apply { mkdirs() }
    val target = java.io.File(dir, info.apkName ?: "update.apk")
    if (target.exists() && target.length() > 0 && repo.verifySha256(info.sha256, target)) {
        return doInstall(ctx, target)
    }
    val note = if (target.exists() && target.length() > 0) "(有 ${target.length() / 1024}KB 部分,将续传)" else ""
    if (!repo.download(info.apkUrl ?: "", target, onProgress)) {
        return if (target.exists() && target.length() > 0)
            "下载中断,已保留 ${target.length() / 1024}KB\n恢复网络后重新下载会断点续传$note"
        else "下载失败,请重试"
    }
    if (!repo.verifySha256(info.sha256, target)) { target.delete(); return "下载校验失败,已取消" }
    return doInstall(ctx, target)
}

private suspend fun doInstall(ctx: Context, target: java.io.File): String {
    if (!Installer.canInstall(ctx)) {
        io.github.xhr666.wristchat.data.AppLog.i("install", "need unknown-sources permission")
        Installer.openManageUnknownSources(ctx)
        return "需要允许安装:已打开系统设置,请开启“允许安装未知应用”后返回重试"
    }
    return when (val r = Installer.submit(ctx, target)) {
        is InstallerResult.NeedPermission ->
            "请先允许 WristChat 安装未知应用,再点一次更新"
        is InstallerResult.Submitted -> {
            io.github.xhr666.wristchat.data.AppLog.i("install", "submitted ${r.id}")
            "已提交安装,请留意系统安装确认"
        }
        is InstallerResult.Fail -> "安装失败:${r.msg}"
    }
}

fun themeName(t: String) = when (t) { "light" -> "亮色"; "dark" -> "暗色"; else -> "AMOLED 纯黑" }
fun hashPin(pin: String, salt: String): String {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    return md.digest("$salt:$pin".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
fun randomSalt(): String {
    val b = ByteArray(16); java.security.SecureRandom().nextBytes(b)
    return b.joinToString("") { "%02x".format(it) }
}
fun queryName(ctx: Context, uri: Uri): String? = runCatching {
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && c.moveToFirst()) c.getString(i) else null
    }
}.getOrNull()

fun licenseText(): String = LICENSE_TEXT

/** 导出三个日志到公共 Download/WristChat/(数据线/文件管理器可取),返回结果文案 */
fun exportLogs(ctx: Context): String {
    val app = ctx.applicationContext as? io.github.xhr666.wristchat.WristChatApp ?: return "导出失败:上下文异常"
    val items = listOf(
        "crash.log" to app.crashLogText(),
        "anr.log" to app.anrLogText(),
        "app.log" to io.github.xhr666.wristchat.data.AppLog.read(),
    )
    return try {
        val resolver = ctx.contentResolver
        var n = 0
        for ((name, text) in items) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/WristChat")
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: continue
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            n++
        }
        "已导出 $n 个日志到 Download/WristChat/"
    } catch (e: Exception) { "导出失败:${e.message}" }
}
