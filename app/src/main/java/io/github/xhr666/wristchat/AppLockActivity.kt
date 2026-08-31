package io.github.xhr666.wristchat

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.xhr666.wristchat.databinding.ActivityLockBinding
import io.github.xhr666.wristchat.ui.common.ThemeManager
import java.security.MessageDigest

/**
 * 四位密码锁:
 * - 5 次错误 -> 锁定 30 秒
 * - 连续 5 次输 0000 -> 解锁后再输 0000 强制关闭密码
 * - 密码哈希 = SHA-256(salt + pin),salt 随机存本地
 */
class AppLockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private val settings by lazy { (application as WristChatApp).settings }
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this, settings)
        super.onCreate(savedInstanceState)
        visible = true
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btn0.setOnClickListener { onDigit("0") }
        binding.btn1.setOnClickListener { onDigit("1") }
        binding.btn2.setOnClickListener { onDigit("2") }
        binding.btn3.setOnClickListener { onDigit("3") }
        binding.btn4.setOnClickListener { onDigit("4") }
        binding.btn5.setOnClickListener { onDigit("5") }
        binding.btn6.setOnClickListener { onDigit("6") }
        binding.btn7.setOnClickListener { onDigit("7") }
        binding.btn8.setOnClickListener { onDigit("8") }
        binding.btn9.setOnClickListener { onDigit("9") }
        binding.btnBack.setOnClickListener { backspace() }
        refreshLockState()
    }

    private var input = StringBuilder()

    private fun onDigit(d: String) {
        val now = System.currentTimeMillis()
        if (now < settings.lockUntil) return
        input.append(d)
        updateDots()
        if (input.length == 4) {
            val pin = input.toString()
            input.clear()
            updateDots()
            checkPin(pin)
        }
    }

    private fun backspace() {
        if (input.isNotEmpty()) input.deleteCharAt(input.length - 1)
        updateDots()
    }

    private fun updateDots() {
        val dots = buildString { repeat(4) { append(if (it < input.length) "●" else "○") } }
        binding.tvDots.text = dots
    }

    private fun checkPin(pin: String) {
        val s = settings
        // 强制重置通道:连续 5 次 0000 触发锁定后,再输 0000 直接关闭密码
        if (s.pendingReset0000 && pin == "0000") {
            forceReset()
            return
        }
        val expected = s.passwordHash
        if (expected.isNotBlank() && hash(pin, s.passwordSalt) == expected) {
            // 成功
            s.failCount = 0
            s.lastFiveInputs = ""
            s.pendingReset0000 = false
            unlocked = true
            finish()
            return
        }
        // 失败
        s.failCount = s.failCount + 1
        val lastInputs = (s.lastFiveInputs.split(",").filter { it.isNotEmpty() } + pin).takeLast(5)
        s.lastFiveInputs = lastInputs.joinToString(",")

        // 0000 强制重置:最近 5 次全是 0000
        if (lastInputs.size >= 5 && lastInputs.all { it == "0000" }) {
            s.pendingReset0000 = true
        }

        if (s.failCount >= 5) {
            s.lockUntil = System.currentTimeMillis() + 30_000
            s.failCount = 0
            binding.tvHint.text = "错误 5 次,锁定 30 秒"
            startLockTimer(30_000L)
            return
        }
        binding.tvHint.text = "密码错误,还可尝试 ${5 - s.failCount} 次"
    }

    private fun forceReset() {
        val s = settings
        s.passwordEnabled = false
        s.passwordHash = ""
        s.passwordSalt = ""
        s.pendingReset0000 = false
        s.failCount = 0
        s.lastFiveInputs = ""
        unlocked = true
        Toast.makeText(this, "已强制关闭密码", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun refreshLockState() {
        val now = System.currentTimeMillis()
        if (now < settings.lockUntil) {
            val remain = settings.lockUntil - now
            binding.tvHint.text = "锁定中,${remain / 1000 + 1} 秒后重试"
            startLockTimer(remain)
        }
    }

    private fun startLockTimer(ms: Long) {
        timer?.cancel()
        timer = object : CountDownTimer(ms, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvHint.text = "锁定中,${millisUntilFinished / 1000 + 1} 秒后重试"
            }
            override fun onFinish() {
                binding.tvHint.text = if (settings.pendingReset0000) "提示:连续错误重置通道已就绪" else "请输入密码"
            }
        }.start()
    }

    override fun onDestroy() {
        visible = false
        timer?.cancel()
        super.onDestroy()
    }

    override fun onBackPressed() {
        // 锁定页不允许返回(除 reset 场景)
        moveTaskToBack(true)
    }

    companion object {
        /** 进程内解锁标记:本次启动已验证通过则不再弹锁 */
        @Volatile
        var unlocked: Boolean = false

        /** 锁页是否已在前台(防重复弹锁) */
        @Volatile
        var visible: Boolean = false

        fun hash(pin: String, salt: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            return md.digest("$salt:$pin".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }

        fun randomSalt(): String {
            val bytes = ByteArray(16)
            java.security.SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
