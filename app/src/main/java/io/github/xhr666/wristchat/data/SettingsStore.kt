package io.github.xhr666.wristchat.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** 全局设置存储:SharedPreferences + Keystore 加密敏感字段 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("wristchat_settings", Context.MODE_PRIVATE)

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
    var model: String
        get() = prefs.getString(KEY_MODEL, "deepseek-v4-flash") ?: "deepseek-v4-flash"
        set(v) = prefs.edit().putString(KEY_MODEL, v.trim()).apply()

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
