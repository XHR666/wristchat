# CHANGELOG

## 2026-10-19 · 工程纪律落地(v0.8.4 基础上,未发版)
### 新增
- `check.sh` + `app/src/test/.../EngineeringRulesTest.kt`:**11 条可失败判据**
  - LaTeX:A1 根号 / A2 分数 / A3 上标 / A4 希腊与运算符 / A5 整份文档脚手架必须消失 / A6 源码不得原样出现 / A7 hasMath 双向
  - 计费:P1 工作日高峰 / P2 工作日非高峰 / P3 法定节假日全天空闲 / P4 周末(含调休上班周末)空闲
- `AGENTS.md`(入口与验证说明)、本 CHANGELOG

### 修复(判据发现)
- `Latex.replaceBraced` 解析花括号参数时多跳一位:`\sqrt{2}` 输出 `√()2`、`\frac{1}{2}` 输出 `/12` → 已修为正确 `√(2)` / `(1)/(2)`

### 读数
- 改前:可失败判据 0 条;`check.sh` 不存在
- 改后:11 通过 / 0 失败;变异自证 M1/M2/M3 期望红集 == 实际红集;还原后红集 []
- 编译(debug+release):通过

### 未验
- 真机渲染效果未验(LaTeX 显示需在手表上确认)
- 全量门禁仅有本判据集(尚无 instrumented 测试 / lint 门禁)

## 2026-10-19 · 补充:发布构建修复(环境变更 + 既有缺陷)
- 现象:`gradle :app:assembleRelease` 失败,`validateSigningRelease` 报 keystore 不存在
- 归因(R8 三分类):
  - ② **环境变更**:项目目录迁移到 `WatchProgram/WristChat/` 后,keystore 实际位置变了
  - ③ **既有缺陷**:`keystore.properties` 写死了本机绝对路径(搬动即失效)
- 修复:`storeFile` 改为相对 `app/` 的路径 `../../wristchat-keystore/wristchat-release.jks`(该文件已被 .gitignore,不入库)
- 读数:`assembleRelease + assembleDebug` BUILD SUCCESSFUL;`./check.sh` 11 通过 / 0 失败
- 未验:无(密钥值未改动)

## 2026-10-19 · 第二批(部分交付;设置页改动已回滚待重做)
### 已改(代码+编译+判据通过;**真机未验**)
- 滚动条:改为"固定长度滑块 + 位置随进度移动"(修"从中间向两边扩张"的错误形态)
- 更新:启动时清理 `cacheDir/updates/*.apk`(装完不再保留已下载包)
- 会话余额自愈:`briefs()` 在 `totalCost<=0` 时从消息 cost 求和(修列表余额恒为 0 的老会话)
- 消息:新增"用户/智能体左右互换"设置;智能体气泡上限 175dp→200dp(一行显示更多字)
- 锁屏:键盘 64dp→46dp、外边距 24→8dp(修只显示 123、下面键看不到)

### 已回滚(未交付)
- 设置页"偏好设置"拉平、设置页底部留白、`rev` 刷新、主题页黑屏修复:编辑过程破坏了 `SettingsScreen.kt` 结构(行构建函数被误编成局部函数),**已 `git checkout` 还原**,待重做
- 归因(R8):**本次改动引入**(编辑脚本按缩进重建时丢失收尾大括号)

### 未验
- 以上 5 项都只做了编译与判据验证,真机行为未验
- item 1(LaTeX 美观排版+长公式横向滚动)、item 3①(累计消费数值)、item 7(逐行右起阅读)未开始,需先确认口径

## 2026-10-19 · 第三批(主题页 + 逐行右起阅读)
### 已改(编译+判据通过;真机未验)
- 主题页空白:根因 `themePrefRows()` 函数体为空(0 项列表)→ 已补"主题/显示大小"两项
- 逐行从右往左读:新增 `TextTransform.reversePerLine()`(按码点逐行倒序),仅对**不含公式**的消息生效;公式保持 LaTeX 顺序
- 判据新增 A8/A9/A10(倒序中文、行序不变、含公式不倒序),变异自证 M4 期望红集 == 实际红集

### 已证明
- `./check.sh` ⇒ 14 通过 / 0 失败
- 变异自证:M1/M2/M3/M4 全部 PASS,还原后红集 []
- `assembleRelease + assembleDebug` ⇒ BUILD SUCCESSFUL

### 未验 / 未做
- 真机未验(主题页渲染、倒序显示)
- item 1 LaTeX 精美排版(待重接 KaTeX + 高度测量)未开始
- item 2② 设置页最后一项留白、2③ 设置页实时刷新 未做
- item 3① 累计消费数值:推断为**流式响应未带 usage**(未设 stream_options.include_usage)→ 单条 cost 只算到部分;需查流式解析后修
- item 4 滚动条:已改为固定长度滑块沿边缘移动,但**轨道未绘制**;待加固定轨道

## 2026-10-19 · 第四批(KaTeX 重接 + 设置页 + 滚动条轨道 + 价格判据)
### 已改(编译+判据通过;真机未验)
- item 1 LaTeX:重新接入 KaTeX(透明背景/主题字色/JSON 转义),**用 JS 测量内容高度**决定 WebView 高度;1.8s 内测不到高度则**自动退回原生可读文本**(绝不再空白);长公式 CSS `overflow-x:auto` 横向滚动
- item 2②:设置页两处列表底部 padding +40dp(最后一项完整可见)
- item 2③:列表内容读取 `settings.rev`,改完设置当页立即刷新
- item 4:滚动条=**固定轨道**(α0.20)+ 轨道内移动的滑块(α0.9)
- item 3①:更正我上一轮的推断(R8 留痕)——官方价格表与我们一致(USD 折算 CNY),问题不在单价;新增 `cost` 诊断日志(不含对话内容)与 P5 价格判据(谷 5.02 / 峰 10.04 / 峰谷 2 倍)

### 已证明
- `./check.sh` ⇒ 15 通过 / 0 失败
- 变异自证:M1..M5 全部 PASS,还原后红集 []
- `assembleRelease + assembleDebug` ⇒ BUILD SUCCESSFUL

### 未验
- 真机未验(KaTeX 是否真正渲染、高度是否正确、设置页刷新)
- item 3① 金额偏差仍需一次真机对照:导出日志里 `[cost]` 行 vs 平台"今日消费"

## 2026-10-19 · v0.8.5(授权发布)
- 版本:versionCode 45 / versionName 0.8.5
- 门禁:./check.sh ⇒ 15 通过 / 0 失败
- 构建:assembleRelease ×1 + assembleDebug ×1 ⇒ BUILD SUCCESSFUL
- 内容:KaTeX 重接(高度测量+兜底)、设置页留白与即时刷新、滚动条固定轨道+滑块、价格判据 P5、cost 诊断日志
- 未验:真机渲染与设置页行为;累计消费金额偏差待日志对照

### 发布记录(v0.8.5-rc1)
- tag:v0.8.5(annotated) · GitHub prerelease v0.8.5-rc1 · 提交 41039a9
- 产物:release 2703751B / debug 26450377B;sha256 文件 103B/101B
- 发布后验证:GitHub API assets=4 全部 uploaded;下载端点第 1 次即 HTTP 206(无传播延迟);
  release APK sha256 = 2177e0f0…(本地与发布 sha256 文件一致)
- 授权:用户当次明确授权("发布")

## 2026-10-20 · 渲染修复 + 系统提示词 + 应用图标
### 已改(编译+判据通过;真机渲染未验)
- LaTeX(WebView/KaTeX):开启 `allowFileAccessFromFileURLs` / `allowUniversalAccessFromFileURLs`(+allowFileAccess)
  —— Android 10+ 对 file:// 页面默认拦截子资源(JS/字体),这是"部分空白 + 退兜底"的最可能原因
- 原生兜底扩展:\vec/\overrightarrow/\bar/\overline/\hat/\dot/\ddot/\tilde(组合字符)、
  \triangle/\square/\ominus/\odot/\oplus/\otimes/角括号等;清理空上标 `^()` 残留
- 应用级系统提示词:设置→模型与对话→"系统提示词(应用级)",作为**第一条 system 消息**注入
- 应用图标:用 wristchat.jpg 生成 5 档密度 ic_launcher.png + 圆形 ic_launcher_round.png
### 参考
- KaTeX 官方 API 文档(katex.render/renderToString、throwOnError、字体相对路径 fonts/)
### 已证明
- ./check.sh ⇒ 17 通过 / 0 失败(新增 A11 向量/三角、A12 空上标)
- assembleRelease + assembleDebug ⇒ BUILD SUCCESSFUL;APK 内 mipmap ic_launcher 资源已更新
### 未验
- 真机 KaTeX 是否恢复渲染、Markdown 行内代码显示、系统提示词生效、图标在表盘/列表中显示

## 2026-10-20 · v0.8.6(授权发布)
- 版本:versionCode 46 / versionName 0.8.6
- 门禁:./check.sh ⇒ 17 通过 / 0 失败
- 构建:assembleRelease + assembleDebug ⇒ BUILD SUCCESSFUL
- 内容:KaTeX file:// 子资源授权、原生兜底符号扩展(向量/三角/圆圈运算符/空上标清理)、应用级系统提示词、应用图标替换
- 未验:真机渲染/系统提示词/图标显示
