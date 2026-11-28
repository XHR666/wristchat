package io.github.xhr666.wristchat.data

object ProviderLabel {
    fun of(name: String, hasKey: Boolean): String =
        if (hasKey) "$name（有 API Key）" else name
}
