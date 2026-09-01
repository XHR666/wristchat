package io.github.xhr666.wristchat

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import io.github.xhr666.wristchat.databinding.ActivityMainBinding
import io.github.xhr666.wristchat.ui.balance.BalanceFragment
import io.github.xhr666.wristchat.ui.chat.ChatFragment
import io.github.xhr666.wristchat.ui.common.CrownScroll
import io.github.xhr666.wristchat.ui.common.PagerAdapter
import io.github.xhr666.wristchat.ui.common.ThemeManager
import io.github.xhr666.wristchat.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val settings by lazy { (application as WristChatApp).settings }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this, settings)
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pager.adapter = PagerAdapter(this, listOf(
            ChatFragment(), BalanceFragment(), SettingsFragment(),
        ))
        binding.pager.offscreenPageLimit = 2
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // 切页时收起键盘(草稿在 ViewModel,不丢)
                window.decorView.clearFocus()
            }
        })

        // 密码锁:启动时验证
        maybeShowLock()
    }

    override fun onResume() {
        super.onResume()
        maybeShowLock()
    }

    private fun maybeShowLock() {
        val s = settings
        if (!s.passwordEnabled) return
        if (AppLockActivity.unlocked) return
        if (AppLockActivity.visible) return
        if (s.graceLeft > 0) {
            s.graceLeft = s.graceLeft - 1
            return
        }
        val intent = Intent(this, AppLockActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        // 表冠旋转事件一律消费,绝不落到框架层(部分手表会把手势转成点击,导致误触发开关/弹窗)
        if (ev.action == MotionEvent.ACTION_SCROLL) {
            val scope = currentPageView() ?: binding.root
            CrownScroll.handleGenericMotion(scope, ev)
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val scope = currentPageView() ?: binding.root
                    CrownScroll.handleKey(scope, event.keyCode)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** 当前页 fragment 的根视图(表冠只滚当前页) */
    private fun currentPageView(): View? {
        val adapter = binding.pager.adapter as? PagerAdapter ?: return null
        return adapter.fragmentAt(binding.pager.currentItem)?.view
    }
}
