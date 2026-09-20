package io.github.xhr666.wristchat.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** 全局设置存储:SharedPreferences + Keystore 加密敏感字段 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("wristchat_settings", Context.MODE_PRIVATE)

    /**
     * 设置版本号:任何一次写入(含后台写入)都会 +1。
     * 界面读取 [rev] 即可在设置变化后立即重组 —— 修复"切换服务商后要过一会儿才更新"的问题。
     */
    private val revState = androidx.compose.runtime.mutableStateOf(0)
    val rev: Int get() = revState.value

    init {
        prefs.registerOnSharedPreferenceChangeListener { _, _ ->
            try { revState.value += 1 } catch (_: Throwable) {}
        }
    }

    // ---- Provider / Key ----
    var providerId: String
        get() = prefs.getString(KEY_PROVIDER, PROVIDER_DEEPSEEK) ?: PROVIDER_DEEPSEEK
        set(v) = prefs.edit().putString(KEY_PROVIDER, v).apply()

    var apiKey: String
        get() = Crypto.decrypt(prefs.getString(KEY_API_KEY, "") ?: "")
        set(v) = prefs.edit().putString(KEY_API_KEY, Crypto.encrypt(v.trim())).apply()

    var platformToken: String
        get() = Crypto.decrypt(prefs.getString(KEY_PLATFORM_TOKEN, "") ?: "")
        set(v) = prefs.edit().putString(KEY_PLATFORM_TOKEN, Crypto.encrypt(v.trim())).apply()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "") ?: ""
        set(v) = prefs.edit().putString(KEY_BASE_URL, v.trim().trimEnd('/')).apply()

    var apiPath: String
        get() = prefs.getString(KEY_API_PATH, "") ?: ""
        set(v) = prefs.edit().putString(KEY_API_PATH, v.trim()).apply()

    // ---- 模型 / 思考 / 参数 ----
    /** 聊天模型(历史字段名保持不变:model == modelChat) */
    var model: String
        get() = prefs.getString(KEY_MODEL, "deepseek-flash") ?: "deepseek-flash"
        set(v) = prefs.edit().putString(KEY_MODEL, v.trim()).apply()

    // ---- 各类任务默认模型(默认全部 deepseek-flash)----
    private fun strOf(key: String, def: String) = prefs.getString(key, def) ?: def
    private fun setStr(key: String, v: String) = prefs.edit().putString(key, v.trim()).apply()

    var modelQuick: String
        get() = strOf(KEY_MODEL_QUICK, "deepseek-flash")
        set(v) = setStr(KEY_MODEL_QUICK, v)
    var modelTitle: String
        get() = strOf(KEY_MODEL_TITLE, "deepseek-flash")
        set(v) = setStr(KEY_MODEL_TITLE, v)
    var modelTranslate: String
        get() = strOf(KEY_MODEL_TRANSLATE, "deepseek-flash")
        set(v) = setStr(KEY_MODEL_TRANSLATE, v)
    var modelOcr: String
        get() = strOf(KEY_MODEL_OCR, "deepseek-flash")
        set(v) = setStr(KEY_MODEL_OCR, v)
    var modelCompress: String
        get() = strOf(KEY_MODEL_COMPRESS, "deepseek-flash")
        set(v) = setStr(KEY_MODEL_COMPRESS, v)

    // ---- 提示词模板(可在设置里改,可一键恢复默认)----
    var promptTranslate: String
        get() = strOf(KEY_P_TRANSLATE, DEFAULT_PROMPT_TRANSLATE)
        set(v) = setStr(KEY_P_TRANSLATE, v)
    var promptTitle: String
        get() = strOf(KEY_P_TITLE, DEFAULT_PROMPT_TITLE)
        set(v) = setStr(KEY_P_TITLE, v)
    var promptOcr: String
        get() = strOf(KEY_P_OCR, DEFAULT_PROMPT_OCR)
        set(v) = setStr(KEY_P_OCR, v)
    var promptCompress: String
        get() = strOf(KEY_P_COMPRESS, DEFAULT_PROMPT_COMPRESS)
        set(v) = setStr(KEY_P_COMPRESS, v)

    fun resetPrompts() {
        prefs.edit()
            .remove(KEY_P_TRANSLATE).remove(KEY_P_TITLE).remove(KEY_P_OCR).remove(KEY_P_COMPRESS)
            .apply()
    }

    var thinkingEnabled: Boolean
        get() = prefs.getBoolean(KEY_THINKING, true)
        set(v) = prefs.edit().putBoolean(KEY_THINKING, v).apply()

    var reasoningEffort: String
        get() = prefs.getString(KEY_EFFORT, "low") ?: "low"
        set(v) = prefs.edit().putString(KEY_EFFORT, v).apply()

    var temperature: Float
        get() = prefs.getFloat(KEY_TEMPERATURE, 1.0f)
        set(v) = prefs.edit().putFloat(KEY_TEMPERATURE, v).apply()

    var topP: Float
        get() = prefs.getFloat(KEY_TOPP, 1.0f)
        set(v) = prefs.edit().putFloat(KEY_TOPP, v).apply()

    var maxTokens: Int
        get() = prefs.getInt(KEY_MAX_TOKENS, 4096)
        set(v) = prefs.edit().putInt(KEY_MAX_TOKENS, v).apply()

    var contextWindow: Int
        get() = prefs.getInt(KEY_CONTEXT_WINDOW, 1_000_000)
        set(v) = prefs.edit().putInt(KEY_CONTEXT_WINDOW, v).apply()

    var compressThreshold: Int
        get() = prefs.getInt(KEY_COMPRESS_THRESHOLD, 85)
        set(v) = prefs.edit().putInt(KEY_COMPRESS_THRESHOLD, v).apply()

    var customPrompt: String
        get() = prefs.getString(KEY_CUSTOM_PROMPT, "") ?: ""
        set(v) = prefs.edit().putString(KEY_CUSTOM_PROMPT, v).apply()

    var memoryAuto: Boolean
        get() = prefs.getBoolean(KEY_MEMORY_AUTO, true)
        set(v) = prefs.edit().putBoolean(KEY_MEMORY_AUTO, v).apply()

    var autoTitle: Boolean // 新会话自动生成标题(默认开)
        get() = prefs.getBoolean(KEY_AUTO_TITLE, true)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_TITLE, v).apply()

    // ---- 主题 ----
    var theme: String // light / dark / amoled
        get() = prefs.getString(KEY_THEME, "amoled") ?: "amoled"
        set(v) = prefs.edit().putString(KEY_THEME, v).apply()

    var displayScale: Float // 显示大小 0.9-1.3
        get() = prefs.getFloat(KEY_DISPLAY_SCALE, 1.0f)
        set(v) = prefs.edit().putFloat(KEY_DISPLAY_SCALE, v).apply()

    // ---- 密码 ----
    var passwordEnabled: Boolean
        get() = prefs.getBoolean(KEY_PASS_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_PASS_ENABLED, v).apply()

    var passwordHash: String
        get() = prefs.getString(KEY_PASS_HASH, "") ?: ""
        set(v) = prefs.edit().putString(KEY_PASS_HASH, v).apply()

    var passwordSalt: String
        get() = prefs.getString(KEY_PASS_SALT, "") ?: ""
        set(v) = prefs.edit().putString(KEY_PASS_SALT, v).apply()

    var graceLeft: Int // 剩余免密次数
        get() = prefs.getInt(KEY_GRACE_LEFT, 0)
        set(v) = prefs.edit().putInt(KEY_GRACE_LEFT, v).apply()

    var graceDefault: Int
        get() = prefs.getInt(KEY_GRACE_DEFAULT, 5)
        set(v) = prefs.edit().putInt(KEY_GRACE_DEFAULT, v).apply()

    // 密码锁状态(进程内内存保存,不清持久化): 失败计数/锁定时间
    var lockUntil: Long
        get() = prefs.getLong(KEY_LOCK_UNTIL, 0L)
        set(v) = prefs.edit().putLong(KEY_LOCK_UNTIL, v).apply()

    var failCount: Int
        get() = prefs.getInt(KEY_FAIL_COUNT, 0)
        set(v) = prefs.edit().putInt(KEY_FAIL_COUNT, v).apply()

    var lastFiveInputs: String // "0000,1234,..."
        get() = prefs.getString(KEY_LAST_INPUTS, "") ?: ""
        set(v) = prefs.edit().putString(KEY_LAST_INPUTS, v).apply()

    var pendingReset0000: Boolean
        get() = prefs.getBoolean(KEY_PENDING_RESET, false)
        set(v) = prefs.edit().putBoolean(KEY_PENDING_RESET, v).apply()

    // ---- 更新 ----
    var repoOwner: String
        get() = prefs.getString(KEY_REPO_OWNER, "XHR666") ?: "XHR666"
        set(v) = prefs.edit().putString(KEY_REPO_OWNER, v.trim()).apply()

    var repoName: String
        get() = prefs.getString(KEY_REPO_NAME, "wristchat") ?: "wristchat"
        set(v) = prefs.edit().putString(KEY_REPO_NAME, v.trim()).apply()

    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, v).apply()

    var updateCooldownMin: Int
        get() = prefs.getInt(KEY_UPDATE_COOLDOWN, 15)
        set(v) = prefs.edit().putInt(KEY_UPDATE_COOLDOWN, v).apply()

    // ---- 快捷输入 ----
    fun getQuickInputs(): List<String> {
        val raw = prefs.getString(KEY_QUICK_INPUTS, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        return (0 until arr.length()).map { arr.optString(it) }
    }

    fun setQuickInputs(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY_QUICK_INPUTS, arr.toString()).apply()
    }

    // ---- 手机同步 ----
    var syncPin: String
        get() = Crypto.decrypt(prefs.getString(KEY_SYNC_PIN, "") ?: "")
        set(v) = prefs.edit().putString(KEY_SYNC_PIN, Crypto.encrypt(v)).apply()

    // ---- 本地记账(余额差值) ----
    var versionName: String
        get() = prefs.getString(KEY_VERSION_NAME, "0.1.0") ?: "0.1.0"
        set(v) = prefs.edit().putString(KEY_VERSION_NAME, v).apply()
    var ledgerLastBalance: Double?
        get() = if (prefs.contains(KEY_LEDGER_LAST)) prefs.getFloat(KEY_LEDGER_LAST, 0f).toDouble() else null
        set(v) = prefs.edit().apply {
            if (v == null) remove(KEY_LEDGER_LAST) else putFloat(KEY_LEDGER_LAST, v.toFloat())
        }.apply()

    var ledgerTodayUsage: Double
        get() = prefs.getFloat(KEY_LEDGER_USAGE, 0f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_LEDGER_USAGE, v.toFloat()).apply()

    var ledgerDate: String
        get() = prefs.getString(KEY_LEDGER_DATE, "") ?: ""
        set(v) = prefs.edit().putString(KEY_LEDGER_DATE, v).apply()

    companion object {
        private const val KEY_PROVIDER = "provider"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_PLATFORM_TOKEN = "platform_token"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_PATH = "api_path"
        private const val KEY_MODEL = "model"
        private const val KEY_MODEL_QUICK = "model_quick"
        private const val KEY_MODEL_TITLE = "model_title"
        private const val KEY_MODEL_TRANSLATE = "model_translate"
        private const val KEY_MODEL_OCR = "model_ocr"
        private const val KEY_MODEL_COMPRESS = "model_compress"
        private const val KEY_P_TRANSLATE = "prompt_translate"
        private const val KEY_P_TITLE = "prompt_title"
        private const val KEY_P_OCR = "prompt_ocr"
        private const val KEY_P_COMPRESS = "prompt_compress"

        /** 翻译提示词(变量:{source_text} {target_lang}) */
        val DEFAULT_PROMPT_TRANSLATE = """
You are a translation expert, skilled in translating various languages, and maintaining accuracy, faithfulness, and elegance in translation.
Next, I will send you text. Please translate it into {target_lang}, and return the translation result directly, without adding any explanations or other content.

Please translate the <source_text> section:

<source_text>
{source_text}
</source_text>
""".trimIndent()

        /** 标题生成提示词(变量:{content} {locale}) */
        val DEFAULT_PROMPT_TITLE = """
I will give you some dialogue content in the `<content>` block.
You need to summarize the conversation between user and assistant into a short title.
1. The title language should be consistent with the user's primary language
2. Do not use punctuation or other special symbols
3. Reply directly with the title
4. Summarize using {locale} language
5. The title should not exceed 10 characters

<content>
{content}
</content>
""".trimIndent()

        /** OCR 提示词(变量:{images}) */
        val DEFAULT_PROMPT_OCR = """
You are an OCR assistant.

Extract all visible text from the image and also describe any non-text elements (icons, shapes, arrows, objects, symbols, or emojis).

For each element, specify:
- The exact text (for text) or a short description (for non-text).
- For document-type content, please use markdown and latex format.
- If there are objects like buildings or characters, try to identify who they are.
- Its approximate position in the image (e.g., 'top left', 'center right', 'bottom middle').
- Its spatial relationship to nearby elements (e.g., 'above', 'below', 'next to', 'on the left of').

Keep the original reading order and layout structure as much as possible.
Do not interpret or translate—only transcribe and describe what is visually present.
""".trimIndent()

        /** 上下文压缩提示词(变量:{content} {target_tokens} {additional_context} {locale}) */
        val DEFAULT_PROMPT_COMPRESS = """
You are a conversation compression assistant. Compress the following conversation into a concise summary.

Requirements:
1. Preserve key facts, decisions, and important context that would be needed to continue the conversation
2. Keep the summary in the same language as the original conversation
3. Target approximately {target_tokens} tokens
4. Output the summary directly without any explanations or meta-commentary
5. Format the summary as context information that can be used to continue the conversation
6. Use {locale} language
7. Start the output with a clear indicator that this is a summary (e.g., "[Summary of previous conversation]" or equivalent in the target language)

{additional_context}

<conversation>
{content}
</conversation>
""".trimIndent()
        private const val KEY_THINKING = "thinking"
        private const val KEY_EFFORT = "effort"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_TOPP = "topp"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_CONTEXT_WINDOW = "context_window"
        private const val KEY_COMPRESS_THRESHOLD = "compress_threshold"
        private const val KEY_CUSTOM_PROMPT = "custom_prompt"
        private const val KEY_MEMORY_AUTO = "memory_auto"
        private const val KEY_AUTO_TITLE = "auto_title"
        private const val KEY_THEME = "theme"
        private const val KEY_DISPLAY_SCALE = "display_scale"
        private const val KEY_PASS_ENABLED = "pass_enabled"
        private const val KEY_PASS_HASH = "pass_hash"
        private const val KEY_PASS_SALT = "pass_salt"
        private const val KEY_GRACE_LEFT = "grace_left"
        private const val KEY_GRACE_DEFAULT = "grace_default"
        private const val KEY_LOCK_UNTIL = "lock_until"
        private const val KEY_FAIL_COUNT = "fail_count"
        private const val KEY_LAST_INPUTS = "last_inputs"
        private const val KEY_PENDING_RESET = "pending_reset"
        private const val KEY_REPO_OWNER = "repo_owner"
        private const val KEY_REPO_NAME = "repo_name"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_UPDATE_COOLDOWN = "update_cooldown"
        private const val KEY_QUICK_INPUTS = "quick_inputs"
        private const val KEY_SYNC_PIN = "sync_pin"
        private const val KEY_LEDGER_LAST = "ledger_last"
        private const val KEY_LEDGER_USAGE = "ledger_usage"
        private const val KEY_LEDGER_DATE = "ledger_date"
        private const val KEY_VERSION_NAME = "version_name"

        const val PROVIDER_DEEPSEEK = "deepseek"
        const val PROVIDER_QWEN = "qwen"
        const val PROVIDER_CUSTOM = "custom"

        fun newInstance(context: Context): SettingsStore = SettingsStore(context.applicationContext)
    }
}
