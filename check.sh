#!/usr/bin/env bash
# WristChat 判据入口(能失败:R1)。用法: ./check.sh [额外 gradle 参数]
# 依赖: JAVA_HOME(可选,默认 PATH 里的 java)、ANDROID_HOME(构建需要)、gradlew(仓库自带)
set -uo pipefail
cd "$(dirname "$0")"

LOG=$(mktemp)
# 用 PATH 里的 gradle(可用 GRADLE_BIN 覆盖);仓库内不写死任何本机绝对路径
GRADLE_BIN="${GRADLE_BIN:-gradle}"
command -v "$GRADLE_BIN" >/dev/null 2>&1 || { echo "FAIL: 找不到 gradle(设置 GRADLE_BIN 或把 gradle 加到 PATH)"; exit 2; }
"$GRADLE_BIN" --console=plain :app:testDebugUnitTest "$@" >"$LOG" 2>&1
RC=$?

RES_DIR=app/build/test-results/testDebugUnitTest
if [ ! -d "$RES_DIR" ]; then
  echo "FAIL: 未产生测试结果(构建失败?)"
  tail -40 "$LOG"
  rm -f "$LOG"; exit 1
fi

sum_attr() { grep -ho "$1=\"[0-9]*\"" "$RES_DIR"/*.xml 2>/dev/null | grep -o '[0-9]*' | awk '{s+=$1} END{print s+0}'; }
TOTAL=$(sum_attr tests)
FAILS=$(sum_attr failures)
ERRS=$(sum_attr errors)
SKIPPED=$(sum_attr skipped)
PASS=$((TOTAL - FAILS - ERRS - SKIPPED))

echo "===== 判据: $PASS 通过 / $((FAILS + ERRS)) 失败 / $SKIPPED 跳过 (共 $TOTAL) ====="
if [ $((FAILS + ERRS)) -gt 0 ]; then
  echo "--- 失败明细 ---"
  grep -h -A2 "<failure\|<error" "$RES_DIR"/*.xml | head -60
  rm -f "$LOG"; exit 1
fi
if [ "$TOTAL" -eq 0 ]; then
  echo "FAIL: 一条判据都没跑(R1 假绿)"
  rm -f "$LOG"; exit 1
fi
if [ "$RC" -ne 0 ]; then
  echo "FAIL: gradle 退出码 $RC"
  tail -30 "$LOG"; rm -f "$LOG"; exit 1
fi
rm -f "$LOG"
echo "PASS(退出码 0)"
