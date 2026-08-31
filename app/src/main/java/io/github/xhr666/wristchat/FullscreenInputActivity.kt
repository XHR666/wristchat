package io.github.xhr666.wristchat

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import io.github.xhr666.wristchat.databinding.ActivityFullscreenInputBinding
import io.github.xhr666.wristchat.ui.common.ThemeManager

/**
 * 全屏输入页:
 * - 自动唤起系统输入法
 * - 关闭输入法不自动发送,停留本页可继续写
 * - 「发送」或右上 ➤ -> RESULT_SEND + 文本;返回 -> RESULT_DRAFT + 文本(草稿保留)
 */
class FullscreenInputActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullscreenInputBinding
    private val settings by lazy { (application as WristChatApp).settings }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this, settings)
        super.onCreate(savedInstanceState)
        binding = ActivityFullscreenInputBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        binding.etFullscreen.setText(intent.getStringExtra("draft") ?: "")
        binding.etFullscreen.requestFocus()

        binding.btnSend.setOnClickListener { finishWith(RESULT_SEND) }
        binding.btnBack.setOnClickListener { finishWith(RESULT_DRAFT) }

        // 自动唤起输入法
        binding.etFullscreen.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etFullscreen, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    private fun finishWith(result: Int) {
        setResult(result, android.content.Intent().putExtra("result", binding.etFullscreen.text.toString()))
        finish()
    }

    override fun onBackPressed() {
        finishWith(RESULT_DRAFT)
    }

    companion object {
        const val RESULT_SEND = 1
        const val RESULT_DRAFT = 2
    }
}
