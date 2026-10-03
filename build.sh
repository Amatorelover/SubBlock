#!/bin/bash
# ============================================================
#  「遮幕」一键构建脚本
#
#  用法：
#    bash build.sh           打 Release 包（安装后即为正式版）
#    bash build.sh debug     打 Debug 包（调试用）
#
#  环境变量（都有默认值，按需覆盖即可）：
#    JAVA_HOME       JDK 17 所在目录
#    ANDROID_SDK_ROOT Android SDK 所在目录
#    GRADLE_BIN      gradle 可执行文件路径
#
#  优先使用项目自带的 Gradle Wrapper（./gradlew）。
#  Wrapper 的好处：所有协作者用完全相同的 Gradle 版本，
#  不会出现"在我机器上能编译"的问题。
# ============================================================

set -u

# ---- 自动探测 JDK：环境变量 > 本地便携目录 > 系统 java ----
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -d "E:/AndroidDev/jdk17" ]; then
    export JAVA_HOME='E:\AndroidDev\jdk17'
  fi
fi

# ---- 自动探测 Android SDK：环境变量 > 本地便携目录 > local.properties ----
if [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  if [ -d "E:/AndroidDev/sdk" ]; then
    export ANDROID_SDK_ROOT='E:\AndroidDev\sdk'
  fi
fi
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"

export MSYS_NO_PATHCONV=1
PROJECT="$(cd "$(dirname "$0")" && pwd)"

TASK="assembleRelease"
[ "${1:-}" = "debug" ] && TASK="assembleDebug"

cd "$PROJECT" || exit 1

# ---- 选择构建器：优先 Wrapper ----
if [ -x "./gradlew" ]; then
  RUNNER=("./gradlew")
  echo "==> 使用项目自带的 Gradle Wrapper"
elif [ -n "${GRADLE_BIN:-}" ] && [ -x "$GRADLE_BIN" ]; then
  RUNNER=("$GRADLE_BIN")
  echo "==> 使用 GRADLE_BIN=$GRADLE_BIN"
elif [ -x "/e/AndroidDev/gradle-8.9/bin/gradle.bat" ]; then
  RUNNER=("/e/AndroidDev/gradle-8.9/bin/gradle.bat")
  echo "==> 使用本地便携 Gradle"
elif command -v gradle >/dev/null 2>&1; then
  RUNNER=("gradle")
  echo "==> 使用系统 gradle"
else
  echo "!! 找不到 Gradle。请安装 Gradle，或执行 gradle wrapper 生成 Wrapper。"
  exit 1
fi

echo "==> JAVA_HOME        = ${JAVA_HOME:-（未设置，将使用系统默认 JDK）}"
echo "==> ANDROID_SDK_ROOT = ${ANDROID_SDK_ROOT:-（未设置，将读取 local.properties）}"
echo "==> 开始构建（$TASK），首次构建需下载依赖，请耐心等待"
echo

"${RUNNER[@]}" --no-daemon "$TASK" 2>&1 | tail -60

echo
echo "==> 产物："
find "$PROJECT/app/build/outputs/apk" -name "*.apk" -printf "%p  (%s bytes)\n" 2>/dev/null \
  || echo "（未找到 APK，请查看上方构建日志）"
