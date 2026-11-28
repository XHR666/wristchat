package io.github.xhr666.wristchat.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.vector.ImageVector

/** 设置项图标。用的是 Compose 自带的 Material 图标，不是手绘路径。 */
object SettingGlyph {
    fun of(title: String): ImageVector = when {
        title.contains("搜索") -> Icons.Filled.Search
        title.contains("密码") || title.contains("安全") || title.contains("后台") || title.contains("遮住") -> Icons.Filled.Lock
        title.contains("赞助") -> Icons.Filled.Favorite
        title.contains("版本") || title.contains("关于") || title.contains("许可") || title.contains("日志") -> Icons.Filled.Info
        title.contains("通知") || title.contains("提醒") -> Icons.Filled.Notifications
        title.contains("主题") || title.contains("字体") || title.contains("界面") -> Icons.Filled.Create
        title.contains("模型") || title.contains("提示词") -> Icons.Filled.List
        title.contains("提供商") || title.contains("服务商") || title.contains("偏好") -> Icons.Filled.Settings
        title.contains("同步") || title.contains("刷新") || title.contains("重试") || title.contains("网络") -> Icons.Filled.Refresh
        title.contains("备份") || title.contains("导入") || title.contains("导出") -> Icons.Filled.Share
        title.contains("存储") || title.contains("缓存") || title.contains("清理") -> Icons.Filled.Delete
        title.contains("记忆") || title.contains("助手") -> Icons.Filled.Face
        title.contains("技能") -> Icons.Filled.Build
        title.contains("首页") || title.contains("常规") -> Icons.Filled.Home
        else -> Icons.Filled.KeyboardArrowRight
    }
}
