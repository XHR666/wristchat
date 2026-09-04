package io.github.xhr666.wristchat.data

/** Provider 抽象:模型/端点/余额支持(设计参考 rikkahub 思路,自研实现) */
data class ProviderConfig(
    val id: String,
    val name: String,
    val defaultBaseUrl: String,
    val defaultPath: String,
    val supportsBalance: Boolean,
    val models: List<String>,
    val isCustom: Boolean = false,
)

object Providers {
    const val DEEPSEEK = "deepseek"
    const val QWEN = "qwen"
    const val GLM = "glm"
    const val KIMI = "kimi"
    const val VOLCANO = "volcano"
    const val CUSTOM = "custom"

    val deepseek = ProviderConfig(
        id = DEEPSEEK,
        name = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com",
        defaultPath = "/chat/completions",
        supportsBalance = true,
        models = listOf("deepseek-v4-flash", "deepseek-v4-pro", "deepseek-v4-flash-vision-exp"),
    )

    val qwen = ProviderConfig(
        id = QWEN,
        name = "通义千问",
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultPath = "/chat/completions",
        supportsBalance = false,
        models = listOf("qwen-max", "qwen-plus", "qwen-turbo"),
    )
    val glm = ProviderConfig(
        id = GLM,
        name = "智谱 GLM",
        defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
        defaultPath = "/chat/completions",
        supportsBalance = false,
        models = listOf("glm-4-plus", "glm-4-air", "glm-4-flash"),
    )
    val kimi = ProviderConfig(
        id = KIMI,
        name = "Kimi",
        defaultBaseUrl = "https://api.moonshot.cn/v1",
        defaultPath = "/chat/completions",
        supportsBalance = false,
        models = listOf("moonshot-v1-8k", "kimi-k2-turbo-preview", "moonshot-v1-32k"),
    )
    val volcano = ProviderConfig(
        id = VOLCANO,
        name = "火山方舟",
        defaultBaseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        defaultPath = "/chat/completions",
        supportsBalance = false,
        models = listOf("doubao-1-5-pro-32k-250115"),
    )

    fun all(): List<ProviderConfig> = listOf(deepseek, qwen, glm, kimi, volcano)

    fun byId(id: String): ProviderConfig = when (id) {
        DEEPSEEK -> deepseek
        QWEN -> qwen
        GLM -> glm
        KIMI -> kimi
        VOLCANO -> volcano
        else -> ProviderConfig(
            id = CUSTOM,
            name = "自定义",
            defaultBaseUrl = "https://example.com/v1",
            defaultPath = "/chat/completions",
            supportsBalance = false,
            models = emptyList(),
            isCustom = true,
        )
    }

    /** 用户当前生效的完整配置(自定义时取用户填写的 base/path) */
    fun resolve(settings: SettingsStore): ProviderConfig {
        val base = byId(settings.providerId)
        val baseUrl = settings.baseUrl.ifBlank { base.defaultBaseUrl }
        val path = settings.apiPath.ifBlank { base.defaultPath }
        return base.copy(
            defaultBaseUrl = baseUrl,
            defaultPath = path,
            models = if (base.isCustom && settings.model.isNotBlank()) listOf(settings.model) else base.models,
        )
    }
}
