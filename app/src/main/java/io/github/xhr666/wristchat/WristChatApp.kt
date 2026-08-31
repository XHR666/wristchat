package io.github.xhr666.wristchat

import android.app.Application
import io.github.xhr666.wristchat.data.SettingsStore

class WristChatApp : Application() {
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore.newInstance(this)
        settings.versionName = BuildConfig.VERSION_NAME
    }
}
