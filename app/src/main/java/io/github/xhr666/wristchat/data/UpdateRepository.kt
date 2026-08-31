package io.github.xhr666.wristchat.data

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
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val (code, text) = try {
            Http.get(url, timeoutMs = 20000)
        } catch (e: Exception) {
            return UpdateResult.Error("网络错误:${e.message}")
        }
        if (code == 404) return UpdateResult.Error("仓库或 Release 不存在")
        if (code !in 200..299) return UpdateResult.Error("检查失败(HTTP $code)")
        val root = try { JSONObject(text) } catch (e: Exception) { return UpdateResult.Error("响应解析失败") }
        val tag = root.optString("tag_name", "").removePrefix("v")
        val remoteVersion = parseVersion(tag)
        val current = parseVersion(settings.versionName)
        if (remoteVersion <= current) return UpdateResult.UpToDate(tag.ifBlank { "同版本" })

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
                versionCode = remoteVersion,
                apkUrl = apkUrl,
                apkName = apkName,
                sha256 = sha,
                body = root.optString("body", ""),
            )
        )
    }

    suspend fun download(url: String, target: File, onProgress: (Long, Long) -> Unit): Boolean {
        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.setRequestProperty("Accept", "application/octet-stream")
            val total = conn.contentLengthLong
            val input = conn.inputStream
            val output = target.outputStream()
            val buf = ByteArray(64 * 1024)
            var read: Int
            var done = 0L
            while (input.read(buf).also { read = it } != -1) {
                output.write(buf, 0, read)
                done += read
                onProgress(done, total)
            }
            output.close()
            input.close()
            conn.disconnect()
            true
        } catch (e: Exception) {
            target.delete()
            false
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

    private fun parseVersion(v: String): Long {
        // v0.1.0 -> 0.1.0 -> 000100000 (major*100000 + minor*1000 + patch)
        val parts = v.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return 0
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return major * 1_000_000L + minor * 1_000L + patch
    }
}
