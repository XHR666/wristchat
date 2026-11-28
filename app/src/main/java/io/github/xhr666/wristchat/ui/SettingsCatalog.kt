package io.github.xhr666.wristchat.ui

/** 设置根目录。小标题和条目交替，顺序对齐常见助手设置。 */
object SettingsCatalog {
    sealed class Row {
        abstract val title: String
        data class Head(override val title: String) : Row()
        data class Entry(val key: String, override val title: String, val desc: String) : Row()
    }

    val root = listOf(
        Row.Head("通用设置"),
        Row.Entry("prefs", "偏好设置", "主题 / 通知 / 常规 / 界面 / 网络"),
        Row.Entry("assistant", "助手", "记忆"),
        Row.Entry("skills", "技能", "导入 / 启用"),
        Row.Head("模型与服务"),
        Row.Entry("models_prompts", "默认模型与提示词", "任务模型 / 提示词 / 参数"),
        Row.Entry("service", "提供商", "服务商 / Key / 地址"),
        Row.Entry("search", "搜索服务", "用哪个服务搜索"),
        Row.Head("数据"),
        Row.Entry("sync", "手机同步", "局域网同步"),
        Row.Entry("backup", "数据备份", "导出 / 导入会话"),
        Row.Entry("storage", "存储管理", "占用 / 清理"),
        Row.Entry("security", "安全", "启动密码"),
        Row.Head("关于"),
        Row.Entry("about", "关于", "版本 / 许可 / 日志"),
    )

    val entries: List<Row.Entry> = root.mapNotNull { it as? Row.Entry }
}
