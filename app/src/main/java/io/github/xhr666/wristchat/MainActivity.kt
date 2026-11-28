package io.github.xhr666.wristchat

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cc.star0.wear.lib.cnwearoverlay.runtime.CnWearOverlay
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
        (application as? WristChatApp)?.appVisible = false
    }

    override fun onStart() {
        super.onStart()
        (application as? WristChatApp)?.onBecameVisible()
        val away = if (stoppedAt == 0L) Long.MAX_VALUE else System.currentTimeMillis() - stoppedAt
        // 配置变更(主题/缩放 recreate)与短时离开(选图片等)不重新上锁;退后台超过 30 秒则重新校验
        if (!isChangingConfigurations && away > 30_000L) {
            io.github.xhr666.wristchat.ui.LockGate.unlocked = false
            foregroundEpoch.value += 1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 不再常亮:按系统息屏策略走(省电)
        // 用现代的 WindowInsets 控制器(旧的 systemUiVisibility 沉浸式 flag 在 Android 11 上
        // 与输入法同屏时容易出现光标/预览错位,正是我们遇到的键盘 bug)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            WristAppRoot(settings, foregroundEpoch.value)
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        val s = try { (newBase.applicationContext as WristChatApp).settings } catch (e: Exception) { null }
        super.attachBaseContext(if (s != null) ScaleManager.apply(newBase, s) else newBase)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        // OPPO 表冠是鼠标滚轮(AXIS_VSCROLL)。cnwearoverlay 在目标机上把它换成旋转编码器的 AXIS_SCROLL。
        val mapped = CnWearOverlay.rotaryEvent(ev)
        try {
            if (mapped.action == MotionEvent.ACTION_SCROLL) {
                val axis = mapped.getAxisValue(MotionEvent.AXIS_SCROLL)
                val v = if (kotlin.math.abs(axis) > 0.001f) axis else -mapped.getAxisValue(MotionEvent.AXIS_VSCROLL)
                if (kotlin.math.abs(v) > 0.001f) {
                    val factor = android.view.ViewConfiguration.get(this).scaledVerticalScrollFactor
                    // 向下转表冠 = 内容向下走。覆盖层只换轴、不取反,这里补上方向。
                    RotaryBus.emit((-v * factor).toInt())
                    return true
                }
            }
            return super.dispatchGenericMotionEvent(ev)
        } finally {
            CnWearOverlay.recycleRotaryEvent(ev, mapped)
        }
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
