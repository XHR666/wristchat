# AGENTS.md — WristChat 入口(给代理/会话)

开工前先读**工作区根下的 `docs/ENGINEERING-RULES.md`(只读,严格遵循)**。

## 项目结构
- `app/` — Android 应用(Compose + Material3, minSdk 30 / target 34, 圆屏 466px @2.0)
- 纯逻辑可测部分:`ui/chat/Latex.kt`(LaTeX→可读文本)、`data/Pricing.kt`(计费时段)、`data/SessionStore.kt`(会话 JSON)

## 验证(可失败判据)
```bash
./check.sh                 # = gradle :app:testDebugUnitTest,打印 "通过/失败" 并返回真实退出码
```
- 判据在 `app/src/test/java/.../EngineeringRulesTest.kt`,每条都**能失败**(改坏实现必须红)
- 环境:JDK 17、Android SDK(ANDROID_HOME)、gradle 在 PATH(可用 `GRADLE_BIN` 覆盖)
- 变异自证脚本:`/tmp/mutate.py`(仓库外临时脚本,不提交)

## 构建
```bash
gradle :app:assembleRelease :app:assembleDebug
```
产物在 `app/build/outputs/apk/<variant>/app-<variant>.apk`。

## 纪律要点(摘自工程纪律手册)
- R1/R2:判据必须能失败;改坏实现 ⇒ 必须红;期望红集 == 实际红集
- R6/R7:改前先记读数;改动前备份/保留旧版本
- R15:发布 / 打 tag / 删除数据等**不可逆动作,每次先问用户**;做完记录
- R14:Release 说明只写客观技术描述,不引用用户原话
- R16:交付报告分三段(已证明 / 推断 / 未验)
