package io.github.xhr666.wristchat.data

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
    ) : ChatResult()

    data class Error(val message: String, val httpCode: Int = 0) : ChatResult()
}

data class MemoryOp(val action: String, val id: String?, val content: String?)

/** 聊天仓库:多轮拼接 / 思考模式 / 记忆工具(function calling)/ usage 解析 / 费用计算 */
class ChatRepository(
    private val settings: SettingsStore,
    private val memory: MemoryStore,
) {

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

    /** 单次对话请求;带 history(不含新提问) */
    suspend fun chat(
        history: List<ChatMessage>,
        newUserText: String,
        enableMemory: Boolean,
    ): ChatResult {
        val provider = Providers.resolve(settings)
        val apiKey = settings.apiKey
        if (apiKey.isBlank()) return ChatResult.Error("请先在设置中填写 API Key")
        val url = provider.defaultBaseUrl + provider.defaultPath
        val model = settings.model.ifBlank { provider.models.firstOrNull() ?: "deepseek-v4-flash" }

        // 组装系统提示:自定义 Prompt → Skills → 记忆
        val sysParts = mutableListOf<String>()
        settings.customPrompt.trim().takeIf { it.isNotBlank() }?.let { sysParts.add(it) }
        SkillStoreProvider.skillsPrompt?.takeIf { it.isNotBlank() }?.let { sysParts.add(it) }
        if (enableMemory) memory.buildMemoriesTag().takeIf { it.isNotBlank() }?.let { sysParts.add(it) }
        val systemContent = sysParts.joinToString("\n\n")

        val messages = JSONArray()
        if (systemContent.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemContent))
        }
        // 历史(不含 reasoning_content:官方规则——无 tools 时忽略;带 tools 时回传)
        history.forEach { m ->
            val o = JSONObject().put("role", m.role).put("content", m.content)
            if (enableMemory && m.role == "assistant" && m.reasoning.isNotBlank()) {
                o.put("reasoning_content", m.reasoning)
            }
            messages.put(o)
        }
        messages.put(JSONObject().put("role", "user").put("content", newUserText))

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", settings.maxTokens)
            if (settings.thinkingEnabled) {
                put("thinking", JSONObject().apply {
                    put("type", "enabled")
                    put("reasoning_effort", settings.reasoningEffort)
                })
            } else {
                put("temperature", settings.temperature.toDouble())
                put("top_p", settings.topP.toDouble())
            }
            if (enableMemory) {
                put("tools", JSONArray().put(MEMORY_TOOL_SCHEMA))
                put("tool_choice", "auto")
            }
        }

        val (code, text) = try {
            Http.postJson(url, body.toString(), apiKey)
        } catch (e: Exception) {
            return ChatResult.Error("网络错误:${e.message}")
        }
        if (code !in 200..299) {
            return ChatResult.Error(parseError(code, text), code)
        }

        val root = try { JSONObject(text) } catch (e: Exception) { return ChatResult.Error("响应解析失败") }
        val choice = root.optJSONArray("choices")?.optJSONObject(0)
            ?: return ChatResult.Error("响应缺少 choices")
        val msg = choice.optJSONObject("message") ?: JSONObject()
        val usageRaw = root.optJSONObject("usage")
        val usage = TokenUsage(
            promptTokens = usageRaw?.optInt("prompt_tokens") ?: 0,
            cacheHit = usageRaw?.optInt("prompt_cache_hit_tokens") ?: 0,
            cacheMiss = usageRaw?.optInt("prompt_cache_miss_tokens") ?: 0,
            completionTokens = usageRaw?.optInt("completion_tokens") ?: 0,
            totalTokens = usageRaw?.optInt("total_tokens") ?: 0,
            reasoningTokens = usageRaw?.optJSONObject("completion_tokens_details")?.optInt("reasoning_tokens") ?: 0,
        )
        val now = System.currentTimeMillis() / 1000
        val cost = Pricing.cost(model, now, usage.cacheHit.toLong(), usage.cacheMiss.toLong(), usage.completionTokens.toLong())

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
                    put("content", msg.optString("content"))
                    if (settings.thinkingEnabled) put("reasoning_content", msg.optString("reasoning_content"))
                    put("tool_calls", toolCalls)
                })
                for (i in 0 until toolResults.length()) messages.put(toolResults.get(i))
                val body2 = body.put("messages", messages)
                val (code2, text2) = try {
                    Http.postJson(url, body2.toString(), apiKey)
                } catch (e: Exception) {
                    return ChatResult.Error("记忆处理后网络错误:${e.message}", code)
                }
                if (code2 !in 200..299) return ChatResult.Error(parseError(code2, text2), code2)
                val root2 = try { JSONObject(text2) } catch (e: Exception) { return ChatResult.Error("响应解析失败") }
                val choice2 = root2.optJSONArray("choices")?.optJSONObject(0) ?: return ChatResult.Error("响应缺少 choices")
                val msg2 = choice2.optJSONObject("message") ?: JSONObject()
                val usage2Raw = root2.optJSONObject("usage")
                val usage2 = TokenUsage(
                    promptTokens = usage2Raw?.optInt("prompt_tokens") ?: 0,
                    cacheHit = usage2Raw?.optInt("prompt_cache_hit_tokens") ?: 0,
                    cacheMiss = usage2Raw?.optInt("prompt_cache_miss_tokens") ?: 0,
                    completionTokens = usage2Raw?.optInt("completion_tokens") ?: 0,
                    totalTokens = usage2Raw?.optInt("total_tokens") ?: 0,
                    reasoningTokens = usage2Raw?.optJSONObject("completion_tokens_details")?.optInt("reasoning_tokens") ?: 0,
                )
                val cost2 = Pricing.cost(model, now, usage2.cacheHit.toLong(), usage2.cacheMiss.toLong(), usage2.completionTokens.toLong())
                return ChatResult.Success(
                    content = msg2.optString("content"),
                    reasoning = msg2.optString("reasoning_content"),
                    usage = usage2,
                    cost = cost2,
                    memoryOps = ops,
                    model = model,
                )
            }
        }

        return ChatResult.Success(
            content = msg.optString("content"),
            reasoning = msg.optString("reasoning_content"),
            usage = usage,
            cost = cost,
            model = model,
        )
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
                    "你是对话摘要助手。请将下面的对话压缩为简洁但保留关键信息的摘要(中文,300字以内)。不要添加对话中没有的信息。"))
                messages.forEach { m -> put(JSONObject().put("role", m.role).put("content", m.content)) }
            })
            put("max_tokens", 1024)
            put("thinking", JSONObject().apply { put("type", "disabled") })
        }
        val (code, text) = try {
            Http.postJson(baseUrl + path, body.toString(), apiKey, timeoutMs = Http.READ_TIMEOUT_CHAT)
        } catch (e: Exception) {
            return ""
        }
        if (code !in 200..299) return ""
        return try {
            JSONObject(text).optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content") ?: ""
        } catch (e: Exception) { "" }
    }
}

/** 供 ChatRepository 读取已启用 skills 的注入文本(由 Settings 层设置) */
object SkillStoreProvider {
    var skillsPrompt: String? = null
}
