package io.github.xhr666.wristchat.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ReleaseInfo(
    val tagName: String,
    val versionCode: Long,
    val apkUrl: String?,
    val apkName: String?,
    val sha256: String?,
    val body: String,
)

sealed class UpdateResult {
    data class Found(val info: ReleaseInfo) : UpdateResult()
    data class UpToDate(val latest: String) : UpdateResult()
    data class NoApk(val latest: String) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

/** GitHub Releases 检查/下载(公开仓库,无需 token) */
class UpdateRepository(private val settings: SettingsStore) {

    suspend fun check(): UpdateResult {
        val owner = settings.repoOwner
        val repo = settings.repoName
        if (owner.isBlank() || repo.isBlank()) return UpdateResult.Error("未配置仓库")
        // 拉 releases 列表(含 prerelease,测试版也能检测),取语义版本最高的
        val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=20"
        val (code, text) = try {
            Http.get(url, timeoutMs = 20000)
        } catch (e: Exception) {
            return UpdateResult.Error("网络错误:${e.message}")
        }
        if (code == 404) return UpdateResult.Error("仓库或 Release 不存在")
        if (code !in 200..299) return UpdateResult.Error("检查失败(HTTP $code)")
        val arr = try { JSONArray(text) } catch (e: Exception) { return UpdateResult.Error("响应解析失败") }
        if (arr.length() == 0) return UpdateResult.UpToDate("无发布")

        val currentKey = versionKey(settings.versionName)
        var best: JSONObject? = null
        var bestKey = 0L
        for (i in 0 until arr.length()) {
            val rel = arr.optJSONObject(i) ?: continue
            val key = versionKey(rel.optString("tag_name", "").removePrefix("v"))
            if (key > bestKey && key > currentKey) { bestKey = key; best = rel }
        }
        if (best == null) return UpdateResult.UpToDate("已是最新")

        val root = best!!
        val tag = root.optString("tag_name", "").removePrefix("v")

        // 必须有 APK 资产才算真更新
        val assets = root.optJSONArray("assets")
        var apkUrl: String? = null
        var apkName: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name")
                if (name.endsWith(".apk")) {
                    apkUrl = a.optString("browser_download_url")
                    apkName = name
                    break
                }
            }
        }
        if (apkUrl == null) return UpdateResult.NoApk(tag)

        // sha256 资产(可选)
        var sha: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (a.optString("name").endsWith(".sha256")) {
                    sha = a.optString("browser_download_url").takeIf { it.isNotBlank() }
                    break
                }
            }
        }

        return UpdateResult.Found(
            ReleaseInfo(
                tagName = root.optString("tag_name", tag),
                versionCode = bestKey,
                apkUrl = apkUrl,
                apkName = apkName,
                sha256 = sha,
                body = root.optString("body", ""),
            )
        )
    }

    /**
     * 断点续传下载:
     * - 已有部分文件 → 带 Range 续传(服务端不支持则整段重下)
     * - 中途失败(切网/断网/读超时)→ 保留部分文件,自动重试一次(续传)
     * - 协程取消 → 保留部分文件,下次点击从断点续传(不误删)
     * - 全部失败 → 保留部分文件供下次续传,返回 false
     */
    suspend fun download(url: String, target: File, onProgress: (Long, Long) -> Unit): Boolean {
        // 最多两次尝试:第一次(可能续传),失败后自动再试一次
        for (attempt in 0..1) {
            if (tryDownloadOnce(url, target, attempt == 1, onProgress)) return true
        }
        return false
    }

    private suspend fun tryDownloadOnce(url: String, target: File, resume: Boolean, onProgress: (Long, Long) -> Unit): Boolean {
        var conn: java.net.HttpURLConnection? = null
        var input: java.io.InputStream? = null
        var output: java.io.OutputStream? = null
        return try {
            val existing = if (resume) target.length() else 0L
            conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("Accept", "application/octet-stream")
                if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
            }
            val code = conn.responseCode
            if (code !in 200..299 && code != 206) return false
            input = conn.inputStream
            if (code == 206 && existing > 0) {
                output = java.io.FileOutputStream(target, true) // append 续传
            } else {
                // 200(服务端不支持断点):清空重下
                target.outputStream().close()
                output = target.outputStream()
            }
            val total = if (conn.contentLengthLong > 0) existing + conn.contentLengthLong else existing
            val buf = ByteArray(64 * 1024)
            var done = existing
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                output.write(buf, 0, read)
                done += read
                onProgress(done, total)
            }
            output.flush()
            output.close(); output = null
            input.close(); input = null
            conn.disconnect(); conn = null
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 主动取消:保留部分文件,交给上层语义(下次续传)
            try { output?.close() } catch (_: Exception) {}
            try { input?.close() } catch (_: Exception) {}
            throw e
        } catch (e: Exception) {
            try { output?.close() } catch (_: Exception) {}
            try { input?.close() } catch (_: Exception) {}
            try { conn?.disconnect() } catch (_: Exception) {}
            false // 保留 partial,下次自动续传
        }
    }

    /** 若 Release 附带 .sha256 资产,下载并校验 APK 哈希(防更新投毒) */
    suspend fun verifySha256(sha256Url: String?, apkFile: File): Boolean {
        if (sha256Url.isNullOrBlank()) return true // 无哈希资产,跳过(单用户自用场景可接受)
        return try {
            val (code, text) = Http.get(sha256Url, timeoutMs = 20000)
            if (code !in 200..299) return false
            val expected = text.trim().split(Regex("\\s+")).firstOrNull()?.lowercase() ?: return false
            val actual = java.security.MessageDigest.getInstance("SHA-256")
                .digest(apkFile.readBytes())
                .joinToString("") { "%02x".format(it) }
            expected == actual
        } catch (e: Exception) {
            false
        }
    }

    /** 语义版本比较键:主.次.修 ×1000 + 预发布(正式版+1000,-rcN 按序号) */
    private fun versionKey(v: String): Long {
        val core = v.substringBefore('-').split(".").mapNotNull { it.toIntOrNull() }
        val major = core.getOrElse(0) { 0 }
        val minor = core.getOrElse(1) { 0 }
        val patch = core.getOrElse(2) { 0 }
        val base = major * 1_000_000_000L + minor * 1_000_000L + patch * 1_000L
        val pre = v.substringAfter('-', "").takeIf { it.isNotBlank() } ?: return base + 1_000L // 正式 > 同版 rc
        val n = Regex("rc(\\d+)").find(pre)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return base + n.coerceIn(0, 999)
    }
}
