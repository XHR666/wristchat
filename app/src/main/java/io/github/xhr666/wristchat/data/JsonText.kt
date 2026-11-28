package io.github.xhr666.wristchat.data

import org.json.JSONObject

/**
 * Android 的 JSONObject.optString 在值为 JSON null 时会返回字符串 "null"。
 * 流式增量里 content / reasoning_content 经常一空一有,直接 optString 会把 "null" 写进气泡。
 */
object JsonText {
    fun of(o: JSONObject?, key: String): String {
        if (o == null || !o.has(key) || o.isNull(key)) return ""
        return fromRaw(o.opt(key))
    }

    fun fromRaw(raw: Any?): String = when {
        raw == null || raw === JSONObject.NULL -> ""
        raw is String -> raw
        else -> ""
    }
}
