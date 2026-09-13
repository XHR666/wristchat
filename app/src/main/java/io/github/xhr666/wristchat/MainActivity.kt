package io.github.xhr666.wristchat

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.xhr666.wristchat.ui.RotaryBus
import io.github.xhr666.wristchat.ui.WristAppRoot
import io.github.xhr666.wristchat.ui.common.ScaleManager

class MainActivity : ComponentActivity() {

    private val settings by lazy { (application as WristChatApp).settings }

    /** 每次"真正回到前台"递增 → 触发重新校验启动密码 */
    private val foregroundEpoch = androidx.compose.runtime.mutableStateOf(0)
    private var stoppedAt = 0L

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        val away = if (stoppedAt == 0L) Long.MAX_VALUE else System.currentTimeMillis() - stoppedAt
        // 配置变更(主题/缩放 recreate)与短时离开(选图片等)不重新上锁;退后台超过 30 秒则重新校验
        if (!isChangingConfigurations && away > 30_000L) {
            io.github.xhr666.wristchat.ui.LockGate.unlocked = false
            foregroundEpoch.value += 1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        setContent {
            WristAppRoot(settings, foregroundEpoch.value)
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        val s = try { (newBase.applicationContext as WristChatApp).settings } catch (e: Exception) { null }
        super.attachBaseContext(if (s != null) ScaleManager.apply(newBase, s) else newBase)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL) {
            // 官方语义:RotaryScrollEvent.verticalScrollPixels 已经是"像素",
            // 一格(一个 detent)≈ 平台滚动因子(ViewConfiguration.scaledVerticalScrollFactor ≈ 64dp)。
            // 旧实现每格只给 6~12dp,比官方小 8 倍,所以"转半天不动、转快了反而显得慢"。
            val axis = ev.getAxisValue(MotionEvent.AXIS_SCROLL)
            val raw = if (kotlin.math.abs(axis) > 0.01f) axis else -ev.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (kotlin.math.abs(raw) > 0.01f) {
                val sign = if (raw > 0) 1 else -1
                val detents = kotlin.math.abs(raw).coerceIn(0.5f, 4f)
                // 每格 ≈ 38dp(约半张卡片),多格事件按格数累加;快慢完全由转动格数决定
                val stepPx = 38f * resources.displayMetrics.density
                RotaryBus.emit((sign * stepPx * detents).toInt())
            }
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_DPAD_UP -> { RotaryBus.emit(-24); return true }
                KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> { RotaryBus.emit(24); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
