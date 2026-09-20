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

/** 安装结果:需要授权时把已下载的包带回去,授权返回后可自动继续安装 */
sealed class InstallOutcome {
    data class Done(val text: String) : InstallOutcome()
    data class NeedPermission(val apk: java.io.File) : InstallOutcome()
    data class Failed(val text: String) : InstallOutcome()
}

suspend fun installRelease(
    ctx: Context, repo: io.github.xhr666.wristchat.data.UpdateRepository, info: io.github.xhr666.wristchat.data.ReleaseInfo,
    onProgress: (Long, Long) -> Unit = { _, _ -> },
): InstallOutcome {
    val dir = java.io.File(ctx.cacheDir, "updates").apply { mkdirs() }
    val target = java.io.File(dir, info.apkName ?: "update.apk")
    // 已下载完整且哈希通过 → 直接安装
    if (target.exists() && target.length() > 0 && repo.verifySha256(info.sha256, target)) {
        return doInstall(ctx, target)
    }
    val note = if (target.exists() && target.length() > 0) "(有 ${target.length() / 1024}KB 部分,将续传)" else ""
    if (!repo.download(info.apkUrl ?: "", target, onProgress)) {
        return InstallOutcome.Failed(
            if (target.exists() && target.length() > 0)
                "下载中断,已保留 ${target.length() / 1024}KB\n恢复网络后重新下载会断点续传$note"
            else "下载失败,请重试"
        )
    }
    if (!repo.verifySha256(info.sha256, target)) {
        // 多见于续传拼接出的坏包:删掉整包,干净重下一次(只重试一次)
        target.delete()
        val ok = repo.download(info.apkUrl ?: "", target, onProgress) && repo.verifySha256(info.sha256, target)
        if (!ok) {
            target.delete()
            return InstallOutcome.Failed("下载校验失败(已重新下载一次),请稍后再试")
        }
    }
    return doInstall(ctx, target)
}

suspend fun doInstall(ctx: Context, target: java.io.File): InstallOutcome {
    if (!Installer.canInstall(ctx)) return InstallOutcome.NeedPermission(target)
    // 走系统安装器界面(商店应用同款),用户能看到安装确认框
    val err = Installer.launchSystemInstaller(ctx, target)
    return if (err == null) InstallOutcome.Done("已打开系统安装器,请在手表上确认安装")
    else InstallOutcome.Failed(err)
}

/** 更新弹窗里只显示简短说明:去 Markdown 标记,取前 3 行非空内容 */
fun shortNote(body: String): String =
    body.lineSequence()
        .map { it.trim().trimStart('#', '-', '*', ' ').replace("**", "").replace("`", "") }
        .filter { it.isNotBlank() }
        .take(3)
        .joinToString("\n")
        .take(160)

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

/**
 * 导出三个日志到公共 Download/WristChat/<当天日期>/<时间>-*.log
 * 上限 50 个文件:超出后删除最久的一个
 */
fun exportLogs(ctx: Context): String {
    val app = ctx.applicationContext as? io.github.xhr666.wristchat.WristChatApp ?: return "导出失败:上下文异常"
    val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    val stamp = java.text.SimpleDateFormat("HHmmss", java.util.Locale.US).format(java.util.Date())
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
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$stamp-$name.txt")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/WristChat/$day")
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: continue
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            n++
        }
        val removed = trimOldExports(ctx, keep = 50)
        "已导出 $n 个日志到 Download/WristChat/$day/" + if (removed > 0) "(清理了 $removed 个旧日志)" else ""
    } catch (e: Exception) { "导出失败:${e.message}" }
}

/** 保留最近 keep 个导出文件,删除更早的 */
private fun trimOldExports(ctx: Context, keep: Int): Int {
    return try {
        val col = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val proj = arrayOf(android.provider.MediaStore.MediaColumns._ID, android.provider.MediaStore.MediaColumns.DATE_ADDED)
        val sel = "${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("Download/WristChat%")
        val rows = mutableListOf<Pair<Long, Long>>()
        ctx.contentResolver.query(col, proj, sel, args, "${android.provider.MediaStore.MediaColumns.DATE_ADDED} ASC")?.use { cur ->
            val idCol = cur.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
            val dCol = cur.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATE_ADDED)
            while (cur.moveToNext()) rows.add(cur.getLong(idCol) to cur.getLong(dCol))
        }
        var removed = 0
        val excess = rows.size - keep
        if (excess > 0) {
            for (i in 0 until excess) {
                runCatching {
                    ctx.contentResolver.delete(col,
                        "${android.provider.MediaStore.MediaColumns._ID}=?",
                        arrayOf(rows[i].first.toString()))
                }
                removed++
            }
        }
        removed
    } catch (e: Exception) { 0 }
}

/** 会话自检:磁盘上到底有哪些会话文件、能不能解析(排查"会话不显示") */
fun sessionSelfCheck(ctx: Context): String {
    val dir = java.io.File(ctx.filesDir, "sessions")
    val sb = StringBuilder()
    sb.append("目录: ").append(dir.absolutePath).append('\n')
    sb.append("存在: ").append(dir.exists()).append('\n')
    val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    sb.append("文件数: ").append(files.size).append("\n\n")
    var ok = 0
    files.take(30).forEach { f ->
        val parsed = try {
            val o = org.json.JSONObject(f.readText())
            val id = o.optString("id")
            val title = o.optString("title")
            val n = org.json.JSONArray(o.optString("messages", "[]")).length()
            ok++
            "OK  id=$id  title=$title  msgs=$n"
        } catch (e: Exception) {
            "BAD ${e.message}"
        }
        sb.append(f.name).append("  ").append(f.length()).append("B  ").append(parsed).append('\n')
    }
    sb.append("\n可解析: ").append(ok).append(" / ").append(files.size)
    return sb.toString()
}
