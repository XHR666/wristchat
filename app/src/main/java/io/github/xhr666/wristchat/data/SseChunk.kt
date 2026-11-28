package io.github.xhr666.wristchat.data

/**
 * 从一行 SSE 里取出 delta 的文本。
 * 不走 Android 的 JSONObject:单测里那个类是空壳,opt 读不出内容,会把没解析也判成通过。
 */
object SseChunk {
    fun deltaField(payload: String, key: String): String {
        val deltaAt = payload.indexOf("\"delta\"")
        val slice = if (deltaAt >= 0) payload.substring(deltaAt) else payload
        return stringField(slice, key).orEmpty()
    }

    fun fold(lines: List<String>): Pair<String, String> {
        val content = StringBuilder()
        val reasoning = StringBuilder()
        for (line in lines) {
            val payload = line.trim().removePrefix("data:").trim()
            if (payload.isEmpty() || payload == "[DONE]") continue
            content.append(deltaField(payload, "content"))
            reasoning.append(deltaField(payload, "reasoning_content"))
        }
        return content.toString() to reasoning.toString()
    }

    private fun stringField(json: String, key: String): String? {
        val mark = "\"$key\""
        var i = 0
        while (true) {
            val at = json.indexOf(mark, i)
            if (at < 0) return null
            var p = at + mark.length
            while (p < json.length && json[p].isWhitespace()) p++
            if (p >= json.length || json[p] != ':') { i = at + mark.length; continue }
            p++
            while (p < json.length && json[p].isWhitespace()) p++
            if (json.startsWith("null", p)) return null
            if (p >= json.length || json[p] != '"') { i = at + mark.length; continue }
            return unescape(readString(json, p + 1))
        }
    }

    private fun readString(json: String, start: Int): String {
        val out = StringBuilder()
        var i = start
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                out.append(c).append(json[i + 1])
                i += 2
                continue
            }
            if (c == '"') break
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun unescape(raw: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < raw.length) {
            if (raw[i] == '\\' && i + 1 < raw.length) {
                when (val n = raw[i + 1]) {
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'u' -> if (i + 5 < raw.length) {
                        val hex = raw.substring(i + 2, i + 6)
                        out.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                        i += 6
                        continue
                    } else out.append(n)
                    else -> out.append(n)
                }
                i += 2
            } else {
                out.append(raw[i])
                i++
            }
        }
        return out.toString()
    }
}
