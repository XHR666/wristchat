package io.github.xhr666.wristchat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed class ChatResult {
    data class Success(
        val content: String,
        val reasoning: String,
        val usage: TokenUsage,
        val cost: Double,
        val memoryOps: List<MemoryOp> = emptyList(),
        val model: String,
        val serverToolCalls: String = "",
        val citations: String = "",
    ) : ChatResult()

    data class Error(val message: String, val httpCode: Int = 0) : ChatResult()
}

data class MemoryOp(val action: String, val id: String?, val content: String?)

/** 聊天仓库:多轮拼接 / 思考模式 / 记忆工具(function calling)/ usage 解析 / 费用计算 */
class ChatRepository(
    private val settings: SettingsStore,
    private val memory: MemoryStore,
    private val app: android.app.Application,
) {

    companion object {
        const val VISION_MODEL = "deepseek-flash"
        /**
         * 官方 2026-09 起:deepseek-flash(= DeepSeek-V4.1-Flash)同时支持文本与图片;
         * 旧名 deepseek-v4-flash / deepseek-v4-flash-vision-exp 已下线但可调用(自动路由到 V4.1-Flash)。
         * v4-pro 系列不支持图片。
         */
        fun supportsVision(model: String): Boolean {
            val m = model.lowercase()
            if (m.contains("pro")) return false
            return m.contains("vision") || m.contains("flash") || m.contains("v4.1")
        }
    }

    private val MEMORY_TOOL_SCHEMA = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "memory_tool")
            put("description", """
                长期记忆工具,跨会话保存信息。action: create(新增)/ edit(更新)/ delete(删除)。
                - 无相关记录: create + content
                - 已有相关记录: edit + id + content
                - 过期/无关记录: delete + id
                记忆会自动出现在后续对话的 <memories> 标签中。
                不要存储敏感信息(种族/宗教/性取向/政治观点/性生活/犯罪记录)。
                可以存:称呼、偏好、计划、工作笔记、聊天风格偏好等。
                不要主动在对话中展示记忆内容,除非用户明确要求。
                相似记忆应合并,优先更新已有记录。
            """.trimIndent())
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().put("create").put("edit").put("delete"))
                    })
                    put("id", JSONObject().apply { put("type", "string") })
                    put("content", JSONObject().apply { put("type", "string") })
                })
                put("required", JSONArray().put("action"))
            })
        })
    }

    /** 单次对话请求;带 history(不含新提问)。imageFile:本轮提问附带的图片附件文件名 */
    suspend fun chat(
        history: List<ChatMessage>,
        newUserText: String,
        imageFiles: List<String> = emptyList(),
        enableMemory: Boolean,
        modelOverride: String? = null,
        onPartial: ((content: String, reasoning: String) -> Unit)? = null,
    ): ChatResult {
        val provider = Providers.resolve(settings)
        val apiKey = settings.apiKey
        if (apiKey.isBlank()) return ChatResult.Error("请先在设置中填写 API Key")
        val url = provider.defaultBaseUrl + provider.defaultPath
        val model = (modelOverride ?: settings.model).ifBlank { provider.models.firstOrNull() ?: "deepseek-flash" }
        val vision = supportsVision(model)
        if (imageFiles.isNotEmpty() && !vision) {
            return ChatResult.Error("当前模型($model)不支持图片,请在设置里切换到 $VISION_MODEL")
        }

        // 组装系统提示:自定义 Prompt → Skills → 记忆
        val sysParts = mutableListOf<String>()
        // 应用级系统提示词:放在最前面(优先于记忆/自定义提示词)
        settings.systemPrompt.trim().takeIf { it.isNotBlank() }?.let { sysParts.add(0, it) }
        settings.customPrompt.trim().takeIf { it.isNotBlank() }?.let { sysParts.add(it) }
        SkillStoreProvider.skillsPrompt?.takeIf { it.isNotBlank() }?.let { sysParts.add(it) }
        val web = settings.webTools && settings.providerId == SettingsStore.PROVIDER_DEEPSEEK
        val declared = WebTools.toolTypes(web, enableMemory)
        if (enableMemory) {
            memory.buildMemoriesTag().takeIf { it.isNotBlank() }?.let { sysParts.add(it) }
            // 联网工具和 memory_tool 不能同时声明。联网开着时只把记忆放进上下文。
            if (!web) sysParts.add(
                "长期记忆规则:当用户表达需要长期记住的信息(如\"记住…\"\"以后都…\"\"我喜欢/我不喜欢…\"、称呼、偏好、计划)时," +
                "必须调用 memory_tool(action=create) 保存;需要修正时先看 <memories> 中已有 id 再 action=edit;过期内容 action=delete。" +
                "不要为一次性、临时内容调用;不要在回复里复述工具调用过程。"
            )
        }
        val systemContent = sysParts.joinToString("\n\n")
        val preface = ApiTranscript.preface(history)
        val fullSystem = listOf(systemContent, preface).filter { it.isNotBlank() }.joinToString("\n\n")

        val safeHistory = ApiTranscript.build(
            history,
            ChatMessage(role = "user", content = newUserText, imgs = imageFiles),
        )
        val messages = JSONArray()
        if (fullSystem.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", fullSystem))
        }
        // 带 tools 时,官方要求把上一轮 reasoning_content 原样带回,即使本轮没有调用工具。
        val toolsOn = declared.isNotEmpty()
        safeHistory.forEach { m ->
            val o = JSONObject().put("role", m.role).put("content", contentOf(m, vision))
            if (toolsOn && m.role == "assistant") {
                o.put("reasoning_content", m.reasoning)
            }
            if (web && m.role == "assistant" && m.serverToolCalls.isNotBlank()) {
                runCatching { o.put("server_tool_calls", JSONArray(m.serverToolCalls)) }
            }
            messages.put(o)
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            if (settings.maxTokens > 0) put("max_tokens", settings.maxTokens)   // 0 = 不限制(留空)
            // 官方文档(guides/thinking_mode):
            //   思考开关 = {"thinking":{"type":"enabled"|"disabled"}}(默认 enabled,必须显式 disabled 才能关)
            //   思考强度 = 顶层 "reasoning_effort": "low"|"high"|"max"(原来误写在 thinking 里,一直没生效)
            if (settings.thinkingEnabled) {
                put("thinking", JSONObject().apply { put("type", "enabled") })
                put("reasoning_effort", settings.reasoningEffort)
            } else {
                put("thinking", JSONObject().apply { put("type", "disabled") })
                put("temperature", settings.temperature.toDouble())
                put("top_p", settings.topP.toDouble())
            }
            val declaredTools = declared
            if (web) {
                put("tools", JSONArray().apply {
                    declaredTools.forEach { put(JSONObject().put("type", it)) }
                })
            } else if (enableMemory) {
                put("tools", JSONArray().put(MEMORY_TOOL_SCHEMA))
                put("tool_choice", "auto")
            }
        }

        val streamed = onPartial != null
        if (streamed) {
            body.put("stream", true)
            body.put("stream_options", JSONObject().put("include_usage", true))
        }
        val msg: JSONObject
        val usage: TokenUsage
        if (!streamed) {
            val (code, text) = try {
                withContext(Dispatchers.IO) { Http.postJson(url, body.toString(), apiKey) }
            } catch (e: Exception) {
                return ChatResult.Error("网络错误:${e.message}")
            }
            if (code !in 200..299) return ChatResult.Error(parseError(code, text), code)
            val root = try { JSONObject(text) } catch (e: Exception) { return ChatResult.Error("响应解析失败") }
            val choice = root.optJSONArray("choices")?.optJSONObject(0)
                ?: return ChatResult.Error("响应缺少 choices")
            msg = choice.optJSONObject("message") ?: JSONObject()
            usage = UsageJson.parse(root.optJSONObject("usage"))
        } else {
            val acc = StreamAccum()
            val (code, err) = try {
                withContext(Dispatchers.IO) {
                    Http.postJsonStream(url, body.toString(), apiKey) { line ->
                        val ev = SseEvents.parseLine(line) ?: return@postJsonStream
                        if (ev.done) return@postJsonStream
                        if (ev.content.isNotEmpty()) acc.content.append(ev.content)
                        if (ev.reasoning.isNotEmpty()) acc.reasoning.append(ev.reasoning)
                        ev.usage?.let { acc.usage = it }
                        acc.absorb(ev.raw)
                        onPartial.invoke(acc.content.toString(), acc.reasoning.toString())
                    }
                }
            } catch (e: Exception) {
                return ChatResult.Error("网络错误:${e.message}")
            }
            if (code !in 200..299) return ChatResult.Error(parseError(code, err), code)
            msg = JSONObject().apply {
                put("content", acc.content.toString())
                put("reasoning_content", acc.reasoning.toString())
                if (acc.tools.length() > 0) put("tool_calls", acc.tools)
                if (acc.serverTools.isNotBlank()) put("server_tool_calls", JSONArray(acc.serverTools))
                if (acc.annotations.isNotBlank()) put("annotations", JSONArray(acc.annotations))
            }
            usage = acc.usage ?: TokenUsage()
            onPartial.invoke(acc.content.toString(), acc.reasoning.toString())
        }
        val now = System.currentTimeMillis() / 1000
        val cost = Pricing.cost(model, now, usage.cacheHit.toLong(), usage.cacheMiss.toLong(), usage.completionTokens.toLong())
        // 成本诊断(不含对话内容):便于对照平台"今日消费",排查累计金额偏差
        AppLog.i("cost", "m=$model hit=${usage.cacheHit} miss=${usage.cacheMiss} out=${usage.completionTokens} peak=${Pricing.isPeak(now)} cny=$cost")

        // 记忆工具调用处理(两轮)
        val toolCalls = msg.optJSONArray("tool_calls")
        if (enableMemory && toolCalls != null && toolCalls.length() > 0) {
            val ops = mutableListOf<MemoryOp>()
            val toolResults = JSONArray()
            for (i in 0 until toolCalls.length()) {
                val tc = toolCalls.optJSONObject(i) ?: continue
                val fn = tc.optJSONObject("function") ?: continue
                val name = fn.optString("name")
                val args = fn.optString("arguments")
                val id = tc.optString("id")
                if (name == "memory_tool") {
                    val result = executeMemoryTool(args, ops)
                    // 隐私:只记动作与 id,绝不把记忆内容(用户个人信息)写进日志
                    runCatching {
                        val o = JSONObject(args)
                        AppLog.i("mem", "action=${o.optString("action")} id=${o.optString("id")}")
                    }
                    toolResults.put(JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", id)
                        put("content", result)
                    })
                }
            }
            if (toolResults.length() > 0) {
                // 第二轮:带 tool 结果
                messages.put(JSONObject().apply {
                    put("role", "assistant")
                    put("content", JsonText.of(msg, "content"))
                    if (declared.isNotEmpty()) put("reasoning_content", JsonText.of(msg, "reasoning_content"))
                    put("tool_calls", toolCalls)
                })
                for (i in 0 until toolResults.length()) messages.put(toolResults.get(i))
                body.remove("stream")
                body.remove("stream_options")
                val body2 = body.put("messages", messages)
                val (code2, text2) = try {
                    withContext(Dispatchers.IO) { Http.postJson(url, body2.toString(), apiKey) }
                } catch (e: Exception) {
                    return ChatResult.Error("记忆处理后网络错误:${e.message}")
                }
                if (code2 !in 200..299) return ChatResult.Error(parseError(code2, text2), code2)
                val root2 = try { JSONObject(text2) } catch (e: Exception) { return ChatResult.Error("响应解析失败") }
                val choice2 = root2.optJSONArray("choices")?.optJSONObject(0) ?: return ChatResult.Error("响应缺少 choices")
                val msg2 = choice2.optJSONObject("message") ?: JSONObject()
                val usage2 = UsageJson.parse(root2.optJSONObject("usage"))
                val cost2 = cost + Pricing.cost(model, now, usage2.cacheHit.toLong(), usage2.cacheMiss.toLong(), usage2.completionTokens.toLong())
                onPartial?.invoke(JsonText.of(msg2, "content"), JsonText.of(msg2, "reasoning_content"))
                val finalText = JsonText.of(msg2, "content").ifBlank {
                    if (ops.isNotEmpty()) "✓ 已${if (ops.any { it.action == "delete" }) "删除" else "保存"} ${ops.size} 条记忆" else ""
                }
                return ChatResult.Success(
                    content = finalText,
                    reasoning = JsonText.of(msg2, "reasoning_content").ifBlank { JsonText.of(msg, "reasoning_content") },
                    usage = TokenUsage(
                        promptTokens = usage.promptTokens + usage2.promptTokens,
                        cacheHit = usage.cacheHit + usage2.cacheHit,
                        cacheMiss = usage.cacheMiss + usage2.cacheMiss,
                        completionTokens = usage.completionTokens + usage2.completionTokens,
                        totalTokens = usage.totalTokens + usage2.totalTokens,
                        reasoningTokens = usage.reasoningTokens + usage2.reasoningTokens,
                    ),
                    cost = cost2,
                    memoryOps = ops,
                    model = model,
                    serverToolCalls = serverToolsOf(msg2).ifBlank { serverToolsOf(msg) },
                    citations = citationsOf(msg2).ifBlank { citationsOf(msg) },
                )
            }
        }

        return ChatResult.Success(
            content = JsonText.of(msg, "content"),
            reasoning = JsonText.of(msg, "reasoning_content"),
            usage = usage,
            cost = cost,
            model = model,
            serverToolCalls = serverToolsOf(msg),
            citations = citationsOf(msg),
        )
    }

    /**
     * 消息内容序列化:user 消息带图片且模型支持 vision 时,content 为
     * [{"type":"text","text":..},{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,.."}}]
     * 否则保持纯文本字符串(官方格式,图片仅在 user 消息,每图 ≤384 tokens)。
     */
    private suspend fun contentOf(m: ChatMessage, vision: Boolean): Any {
        if (!vision || m.imgs.isEmpty() || m.role != "user") return m.content
        val b64s = withContext(Dispatchers.IO) { m.imgs.mapNotNull { Attachments.readBase64(app, it) } }
        if (b64s.isEmpty()) return m.content
        return JSONArray().apply {
            if (m.content.isNotBlank()) put(JSONObject().put("type", "text").put("text", m.content))
            b64s.forEach { b64 ->
                put(JSONObject().put("type", "image_url").put("image_url",
                    JSONObject().put("url", "data:image/jpeg;base64,$b64")))
            }
        }
    }

    private fun executeMemoryTool(args: String, ops: MutableList<MemoryOp>): String {
        return try {
            val o = JSONObject(args)
            val action = o.optString("action")
            val id = o.optString("id").takeIf { it.isNotBlank() }
            val content = o.optString("content")
            when (action) {
                "create" -> {
                    val item = memory.add(content)
                    ops.add(MemoryOp("create", item.id, item.content))
                    "ok: created ${item.id}"
                }
                "edit" -> {
                    if (id == null) "error: id required"
                    else {
                        memory.update(id, content)
                        ops.add(MemoryOp("edit", id, content))
                        "ok: updated $id"
                    }
                }
                "delete" -> {
                    if (id == null) "error: id required"
                    else {
                        memory.delete(id)
                        ops.add(MemoryOp("delete", id, null))
                        "ok: deleted $id"
                    }
                }
                else -> "error: unknown action"
            }
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }

    private fun serverToolsOf(msg: JSONObject): String {
        val a = msg.optJSONArray("server_tool_calls") ?: return ""
        return if (a.length() == 0) "" else a.toString()
    }

    private fun citationsOf(msg: JSONObject): String {
        val a = msg.optJSONArray("annotations") ?: return ""
        val lines = LinkedHashSet<String>()
        for (i in 0 until a.length()) {
            val c = a.optJSONObject(i)?.optJSONObject("url_citation") ?: continue
            val url = c.optString("url")
            if (url.isBlank()) continue
            val title = c.optString("title").ifBlank { url }
            lines += if (title == url) url else "$title\n$url"
        }
        return lines.joinToString("\n")
    }

    private fun parseError(code: Int, text: String): String {
        val msg = try { JSONObject(text).optJSONObject("error")?.optString("message") } catch (e: Exception) { null }
        return when {
            code == 401 -> "API Key 无效,请到设置检查"
            code == 429 -> "请求过于频繁,请稍后再试"
            code in 500..599 -> "服务繁忙,请稍后再试"
            !msg.isNullOrBlank() -> "错误: $msg"
            else -> "请求失败(HTTP $code)"
        }
    }

    /** 上下文压缩:用默认模型压缩旧消息为摘要(thinking 关闭,更快更省) */
    suspend fun compress(messages: List<ChatMessage>, model: String, apiKey: String, baseUrl: String, path: String): String {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content",
                    settings.promptCompress
                        .replace("{target_tokens}", "300")
                        .replace("{additional_context}", "")
                        .replace("{locale}", java.util.Locale.getDefault().displayLanguage)) )
                messages.forEach { m -> put(JSONObject().put("role", m.role).put("content", m.content)) }
            })
            put("max_tokens", 1024)
            put("thinking", JSONObject().apply { put("type", "disabled") })
        }
        val (code, text) = try {
            withContext(Dispatchers.IO) { Http.postJson(baseUrl + path, body.toString(), apiKey, timeoutMs = Http.READ_TIMEOUT_CHAT) }
        } catch (e: Exception) {
            return ""
        }
        if (code !in 200..299) return ""
        return try {
            JSONObject(text).optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content") ?: ""
        } catch (e: Exception) { "" }
    }

    /** 会话标题生成:用当前模型概括对话主题(短请求,thinking 关) */
    suspend fun genTitle(firstUser: String, firstReply: String): Pair<String, TokenUsage?> {
        val provider = Providers.resolve(settings)
        val key = settings.apiKey
        if (key.isBlank()) return "" to null
        val body = JSONObject().apply {
            put("model", settings.modelTitle)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content",
                    settings.promptTitle
                        .replace("{locale}", java.util.Locale.getDefault().displayLanguage)
                        .replace("{content}", "用户:${firstUser.take(120)}\n助手:${firstReply.take(120)}")))
                put(JSONObject().put("role", "user").put("content", "请给出标题"))
            })
            put("max_tokens", 32)
            put("thinking", JSONObject().apply { put("type", "disabled") })
        }
        val (code, text) = try {
            withContext(Dispatchers.IO) { Http.postJson(provider.defaultBaseUrl + provider.defaultPath, body.toString(), key, timeoutMs = Http.READ_TIMEOUT_QUICK) }
        } catch (e: Exception) {
            return "" to null
        }
        if (code !in 200..299) return "" to null
        return try {
            val root = JSONObject(text)
            val title = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                ?.trim()?.trim('"', '“', '”')?.take(16) ?: ""
            val usage = root.optJSONObject("usage")?.let { UsageJson.parse(it) }
            title to usage
        } catch (e: Exception) {
            "" to null
        }
    }
}

/** 流式增量:正文、思考、以及按 index 拼起来的 tool_calls。 */
private class StreamAccum {
    val content = StringBuilder()
    val reasoning = StringBuilder()
    var usage: TokenUsage? = null
    val tools = JSONArray()
    var serverTools: String = ""
    var annotations: String = ""

    fun absorb(root: JSONObject?) {
        takeTools(root)
        val choice = root?.optJSONArray("choices")?.optJSONObject(0) ?: return
        val delta = choice.optJSONObject("delta")
        val stc = delta?.optJSONArray("server_tool_calls")
            ?: choice.optJSONObject("message")?.optJSONArray("server_tool_calls")
        if (stc != null && stc.length() > 0) serverTools = stc.toString()
        val ann = delta?.optJSONArray("annotations")
            ?: choice.optJSONObject("message")?.optJSONArray("annotations")
        if (ann != null && ann.length() > 0) annotations = ann.toString()
    }

    fun takeTools(root: JSONObject?) {
        val delta = root?.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") ?: return
        val arr = delta.optJSONArray("tool_calls") ?: return
        for (i in 0 until arr.length()) {
            val part = arr.optJSONObject(i) ?: continue
            val index = if (part.has("index")) part.optInt("index") else i
            while (tools.length() <= index) {
                tools.put(JSONObject().apply {
                    put("id", "")
                    put("type", "function")
                    put("function", JSONObject().apply { put("name", ""); put("arguments", "") })
                })
            }
            val cur = tools.getJSONObject(index)
            val id = part.optString("id", "")
            if (id.isNotEmpty()) cur.put("id", id)
            val fn = part.optJSONObject("function") ?: continue
            val name = fn.optString("name", "")
            val f = cur.getJSONObject("function")
            if (name.isNotEmpty()) f.put("name", name)
            val args = fn.optString("arguments", "")
            if (args.isNotEmpty()) f.put("arguments", f.optString("arguments", "") + args)
        }
    }
}

/** 供 ChatRepository 读取已启用 skills 的注入文本(由 Settings 层设置) */
object SkillStoreProvider {
    var skillsPrompt: String? = null
}
