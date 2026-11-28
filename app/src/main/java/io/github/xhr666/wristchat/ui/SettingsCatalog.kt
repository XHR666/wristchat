package io.github.xhr666.wristchat.ui

/** 设置根目录。顺序对齐常见助手设置：偏好、助手、模型与服务、数据、关于。 */
object SettingsCatalog {
    data class Entry(val key: String, val title: String, val desc: String)

    val root = listOf(
        Entry("prefs", "偏好设置", "主题 / 通知 / 常规 / 界面 / 网络"),
        Entry("assistant", "助手", "提示词 / 记忆"),
        Entry("skills", "技能", "导入 / 启用"),
        Entry("models_prompts", "默认模型与提示词", "任务模型 / 提示词"),
        Entry("service", "提供商", "服务商 / Key / 地址"),
        Entry("search", "搜索服务", "联网搜索 / 阅读链接"),
        Entry("sync", "手机同步", "局域网同步"),
        Entry("backup", "数据备份", "导出 / 导入会话"),
        Entry("storage", "存储管理", "占用 / 清理"),
        Entry("security", "安全", "启动密码"),
        Entry("about", "关于", "版本 / 许可 / 日志"),
    )
}
