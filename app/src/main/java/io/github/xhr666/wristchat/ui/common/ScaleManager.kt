package io.github.xhr666.wristchat.ui.common

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import io.github.xhr666.wristchat.data.SettingsStore
import java.util.Locale

/**
 * 显示大小缩放:在 Activity.attachBaseContext 中按比例调整 densityDpi 与 fontScale。
 * 圆屏安全区按真实像素计算 → 任意缩放比例下圆形适配天然成立。
 * 范围 0.9–1.3,步进 0.05,默认 1.0。
 */
object ScaleManager {

    const val MIN = 0.9f
    const val MAX = 1.3f
    const val STEP = 0.05f

    fun apply(context: Context, settings: SettingsStore): Context {
        val scale = settings.displayScale.coerceIn(MIN, MAX)
        if (Math.abs(scale - 1.0f) < 0.001f) return context

        val base = context.resources
        val config = Configuration(base.configuration)
        val origDensity = base.displayMetrics.density
        val origDpi = base.displayMetrics.densityDpi
        val targetDpi = (origDpi * scale).toInt().coerceAtLeast(120)
        config.densityDpi = targetDpi
        config.fontScale = origDensity * scale / (targetDpi / 160f)
        return context.createConfigurationContext(config)
    }
}
