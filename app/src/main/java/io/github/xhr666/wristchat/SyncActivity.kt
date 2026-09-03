package io.github.xhr666.wristchat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.xhr666.wristchat.data.SettingsStore
import io.github.xhr666.wristchat.data.SyncServer
import io.github.xhr666.wristchat.databinding.ActivitySyncBinding
import io.github.xhr666.wristchat.ui.common.QrUtil
import io.github.xhr666.wristchat.ui.common.RoundInsets
import io.github.xhr666.wristchat.ui.common.ThemeManager

/** 手机同步全屏页:二维码 + 地址 + 密钥;服务随本页生命周期(返回即停) */
class SyncActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySyncBinding
    private val settings by lazy { (application as WristChatApp).settings }
    private var server: SyncServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this, settings)
        super.onCreate(savedInstanceState)
        binding = ActivitySyncBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.post {
            val inset = RoundInsets.horizontalInsetPx(binding.root, binding.topBar.top.toFloat() + binding.topBar.height / 2f)
            val minInset = (10 * resources.displayMetrics.density).toInt()
            binding.topBar.setPadding(inset.coerceAtLeast(minInset), binding.topBar.paddingTop, inset.coerceAtLeast(minInset), binding.topBar.paddingBottom)
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnClose.setOnClickListener { finish() }
        binding.btnCopy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("sync", "${binding.tvUrl.text}\nPIN:${settings.syncPin}"))
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
        }

        startSyncServer()
    }

    private fun startSyncServer() {
        val s = settings
        if (s.syncPin.length != 4) s.syncPin = (1000..9999).random().toString()
        val svc = SyncServer(
            pin = s.syncPin,
            readConfig = { configJson() },
            applyConfig = { body -> applyConfigJson(body) },
        )
        if (!svc.start()) {
            Toast.makeText(this, "同步服务启动失败", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        server = svc
        val ip = svc.localIp() ?: "(未获取到 IP,请检查网络)"
        val url = "http://$ip:${svc.port}"
        binding.tvUrl.text = url
        binding.tvPin.text = "密钥:${s.syncPin}"
        binding.ivQr.setImageBitmap(QrUtil.generate(url, (150 * resources.displayMetrics.density).toInt()))
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    // ---------- 配置读写(回调可能来自后台线程,统一回主线程) ----------
    private fun configJson(): String {
        val s = settings
        return org.json.JSONObject().apply {
            put("temperature", s.temperature); put("topP", s.topP)
            put("maxTokens", s.maxTokens); put("thinking", s.thinkingEnabled)
            put("effort", s.reasoningEffort); put("model", s.model)
            put("provider", s.providerId); put("baseUrl", s.baseUrl); put("apiPath", s.apiPath)
            put("customPrompt", s.customPrompt)
            put("quickInputs", org.json.JSONArray(s.getQuickInputs()))
        }.toString()
    }

    private fun applyConfigJson(body: String): String {
        return try {
            val o = org.json.JSONObject(body)
            val s = settings
            s.temperature = o.optDouble("temperature", 1.0).toFloat().coerceIn(0f, 2f)
            s.topP = o.optDouble("topP", 1.0).toFloat().coerceIn(0f, 1f)
            s.maxTokens = o.optInt("maxTokens", 4096).coerceIn(256, 65536)
            s.thinkingEnabled = o.optBoolean("thinking", true)
            s.reasoningEffort = o.optString("effort", "low")
            s.model = o.optString("model", s.model)
            s.providerId = o.optString("provider", s.providerId)
            s.baseUrl = o.optString("baseUrl", s.baseUrl)
            s.apiPath = o.optString("apiPath", s.apiPath)
            s.customPrompt = o.optString("customPrompt", s.customPrompt)
            o.optJSONArray("quickInputs")?.let { arr -> s.setQuickInputs((0 until arr.length()).map { arr.optString(it) }) }
            o.optString("apiKey").takeIf { it.isNotBlank() }?.let { s.apiKey = it }
            val skillName = o.optString("skillName"); val skillContent = o.optString("skillContent")
            if (skillContent.isNotBlank()) {
                val store = io.github.xhr666.wristchat.data.SkillStore(applicationContext)
                store.importFile(skillName.ifBlank { "web_import.md" }, skillContent)
            }
            val chatImport = o.optString("chatImport")
            if (chatImport.isNotBlank()) {
                val vm = io.github.xhr666.wristchat.ui.settings.SettingsViewModel(application)
                val r = vm.importFromText(chatImport, "web_chat.json")
                return if (r.startsWith("已导入")) "已保存;$r" else "已保存(聊天导入失败:$r)"
            }
            "已保存"
        } catch (e: Exception) {
            "保存失败:${e.message}"
        }
    }
}
