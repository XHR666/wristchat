package io.github.xhr666.wristchat.ui.common

import android.app.Activity
import androidx.annotation.StyleRes
import io.github.xhr666.wristchat.R
import io.github.xhr666.wristchat.data.SettingsStore

/** 三档主题(亮/暗/AMOLED) */
object ThemeManager {
    const val LIGHT = "light"
    const val DARK = "dark"
    const val AMOLED = "amoled"

    @StyleRes
    fun themeRes(theme: String): Int = when (theme) {
        LIGHT -> R.style.Theme_WristChat
        DARK -> R.style.Theme_WristChat_Dark
        else -> R.style.Theme_WristChat_Amoled
    }

    fun apply(activity: Activity, settings: SettingsStore) {
        activity.setTheme(themeRes(settings.theme))
    }
}
