package io.github.xhr666.wristchat.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** 极简 HTTP POST/GET(零第三方依赖) */
object Http {
    const val CONNECT_TIMEOUT = 15000
    const val READ_TIMEOUT_CHAT = 90000
    const val READ_TIMEOUT_QUICK = 20000

    fun postJson(
        url: String,
        body: String,
        bearer: String? = null,
        timeoutMs: Int = READ_TIMEOUT_CHAT,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.readAllBytesSafe() ?: ""
        conn.disconnect()
        return code to text
    }

    fun get(
        url: String,
        bearer: String? = null,
        timeoutMs: Int = READ_TIMEOUT_QUICK,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = timeoutMs
            bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.readAllBytesSafe() ?: ""
        conn.disconnect()
        return code to text
    }

    fun getBytes(url: String, bearer: String? = null): Pair<Int, ByteArray> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT_QUICK
            bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        val code = conn.responseCode
        val data = (if (code in 200..299) conn.inputStream else conn.errorStream)?.readAllBytes() ?: ByteArray(0)
        conn.disconnect()
        return code to data
    }

    private fun InputStream.readAllBytesSafe(): String {
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var n: Int
        while (read(buf).also { n = it } != -1) bos.write(buf, 0, n)
        return String(bos.toByteArray(), Charsets.UTF_8)
    }

    fun jsonObject(s: String): JSONObject = JSONObject(s)
    fun jsonArray(s: String): JSONArray = JSONArray(s)
}
