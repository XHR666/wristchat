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
