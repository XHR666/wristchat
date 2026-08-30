# WristChat

OPPO Watch X2(圆屏安卓手表)上的轻量 AI 助手:与 DeepSeek / 通义千问等(OpenAI 兼容)对话,并查看 DeepSeek 账户余额。

> ⚠️ 本仓库不含任何密钥/Token。API Key 仅在你手表本地输入,经 Android Keystore 加密存储,数据直连各服务官方 API。

## 功能

- 💬 三页左右滑动:聊天 / 余额 / 设置
- ⌨️ 全屏输入页(自动唤起系统输入法,关闭不发送)
- 🎯 快捷输入面板
- 📐 圆屏 466px(233dp)原生适配,方屏兼容
- 🌀 表冠滚动(ACTION_SCROLL + 兜底)
- 💰 DeepSeek 余额 / 高峰空闲时段徽章 / 今日用量
- 📐 LaTeX 渲染(懒加载 WebView + KaTeX 子集)
- 📥 Skills 导入(纯文本提示词级)
- 🔒 四位密码锁 + 免密次数
- 🌗 亮色 / 暗色 / AMOLED 纯黑三档主题
- 💾 会话本地持久化(列表管理 / 删除 / 占用统计)

## 构建

```bash
# 需要 JDK 17 + Android SDK (build-tools 34, platform 30/34)
export ANDROID_HOME=/path/to/sdk
./gradlew assembleDebug
# 输出 app/build/outputs/apk/debug/app-debug.apk
```

安装到手表:adb install 或通过微思应用商店侧载。

## 隐私与安全

- 无账号、无云端、无遥测;所有数据(Key、会话)仅存本机
- 仅 HTTPS;权限最小(INTERNET)
- 会话与密码均不离开设备

## License

MIT © 2026 wristchat contributors

灵感与设计参考(仅思路,无代码引用):
[rikkahub](https://github.com/rikkahub/rikkahub) (AGPL-3.0)、[Operit](https://github.com/AAswordman/Operit) (LGPL-3.0)、[chatbox](https://github.com/chatboxai/chatbox) (GPL-3.0)、[DeepSeek-Balance-Whale-Widget](https://github.com/MeteorNOX/DeepSeek-Balance-Whale-Widget)
