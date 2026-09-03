# WristChat

OPPO Watch X2(及同类圆屏安卓手表)上的轻量 AI 助手。

与 DeepSeek / 通义千问等 OpenAI 兼容服务对话,查看 DeepSeek 账户余额与高峰/空闲时段价格,并可在手机上远程调整全部 AI 参数。

> ⚠️ 本仓库不含任何密钥/Token。API Key 只在你设备上输入,经 Android Keystore 加密后存于本机;数据直连各服务官方 API。

## 功能

- 🗂️ **四页左右滑动**:`负一屏(会话历史+AI标题) | 聊天 | 余额 | 设置`,启动默认在聊天页
- 💬 **聊天**
  - 全屏输入页:点击输入框自动唤起系统输入法,关闭输入法不发送,草稿左右滑动不丢失
  - 多轮对话、**深度思考**(思维链自动折叠、可展开查看)
  - **Markdown + LaTeX 渲染**(KaTeX;超长公式可横向滑动)
  - 快捷输入面板(点击填入后可继续编辑)
  - 斜杠命令:`/new` `/remember` `/memories` `/forget` `/skills` `/on` `/off`
  - **记忆工具**:模型经 function-calling 自动记忆,注入 `<memories>`,也可手动管理
  - **上下文自动压缩**(超出阈值用默认模型压缩旧消息,不弹提示)
  - 对话详情:消耗 tokens / 缓存命中率 / 上下文占用 / 消耗余额(按峰谷×模型计)
- 💰 **余额页**:官方余额(充值/赠送/总余额/可用)、**高峰/空闲时段徽章**(北京时间,官方规则:周一至五 9-12、14-18 为高峰,其余含周末全空闲)、今日用量(平台 Token 实时或本地记账)、本应用累计消费
- ⚙️ **设置(9 个分类子页)**
  - 服务:DeepSeek / 通义千问 / 自定义 OpenAI 兼容,Base URL 与路径可配
  - 模型与对话:模型选择(需先填 Key)、思考模式/强度、温度、Top P、上下文窗口、压缩阈值、自定义 Prompt、自动会话标题
  - 快捷输入:逐条增删改(上限 20)
  - 技能与记忆:Skills 导入(文件 / 文件夹扫描 / 手机同步粘贴)、记忆管理
  - 会话与导入:会话管理、聊天记录导入(JSON / chatbox / 文本)
  - 存储与缓存:分项大小、清理缓存、清理全部(5 秒倒计时确认)
  - 更新与同步:**检查更新**(GitHub Releases,仅真实 APK 新版本提示,下载校验后调系统安装器)、**手机同步**(二维码 + 4 位密钥,手机浏览器远程改参数)
  - 安全与外观:四位密码锁(默认关闭,5 次错误锁 30 秒,连续 5 次输 0000 后可强制关闭)、免密次数、主题(亮/暗/AMOLED)、显示大小(0.9-1.3 缩放)
  - 关于:版本、开源许可、崩溃日志、仓库
- 📐 **圆屏原生适配**:466px(233dp @320PPI)圆形安全区逐元素计算,任意显示大小下内容不被切;方屏自动兼容
- 🌀 **表冠滚动**:方向与手感按实机调优,支持触摸/表冠滚动;右侧滚动指示条(停止自动淡出);顶部时间胶囊(聊天页常驻,其余页面滚到顶弹出)

## 页面结构

```
[ 负一屏:会话 ]  ⇄  [ 聊天 ]  ⇄  [ 余额 ]  ⇄  [ 设置 ]
```

- 负一屏:AI 自动命名的会话列表(标题/消息数/时间/费用),点击进入聊天,长按删除
- 余额页滚动到顶部会弹出时间胶囊与当前时段说明

## 构建

```bash
# 需要 JDK 17 + Android SDK (build-tools 34, platforms android-30/34)
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
# 输出 app/build/outputs/apk/debug/app-debug.apk

# release(需要本地 keystore.properties 签名配置,已被 .gitignore 排除)
./gradlew assembleRelease
```

安装到手表:`adb install`,或通过微思应用商店侧载。

## 隐私与安全

- 无账号、无云端、无遥测;API Key / Token / 会话 / 记忆全部只存本机(Keystore AES-GCM 加密)
- 网络仅 HTTPS;声明权限仅 INTERNET / ACCESS_NETWORK_STATE / ACCESS_WIFI_STATE
- 自更新仅从 GitHub Release 下载 APK,校验 SHA-256(如提供)后交系统安装器;无 root、无 Shizuku
- 崩溃日志只写本机,设置→关于可查看

## License

MIT © 2026 wristchat contributors

第三方组件:ZXing core (Apache-2.0)、Markwon (Apache-2.0)、KaTeX (MIT)、marked.js (MIT)、AndroidX/Jetpack (Apache-2.0)、kotlinx-coroutines (Apache-2.0)。

界面与交互设计受 [rikkahub](https://github.com/rikkahub/rikkahub) (AGPL-3.0)、[Operit](https://github.com/AAswordman/Operit) (LGPL-3.0)、[chatbox](https://github.com/chatboxai/chatbox) (GPL-3.0)、[DeepSeek-Balance-Whale-Widget](https://github.com/MeteorNOX/DeepSeek-Balance-Whale-Widget) 启发(仅借鉴设计思路,无代码/资源引用)。
