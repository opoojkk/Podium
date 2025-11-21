#!/usr/bin/env bash
set -euo pipefail

# Rebuild iOS simulator Compose framework from a clean slate.

REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO"

# 1) 选择可用的 JDK（默认用 21）
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"

# 2) 清理旧的 KMP 产物和 Xcode DerivedData，避免复用旧框架
echo "Step 1/3: Cleaning previous iOS framework outputs and DerivedData..."
rm -rf composeApp/build/xcode-frameworks composeApp/build/bin composeApp/build/konan
rm -rf "$HOME/Library/Developer/Xcode/DerivedData/iosApp-"*

# 3) 先构建 Rust 音频库（Gradle 任务会触发 rust-audio-player/build.sh）
echo "Step 2/3: Building Rust audio player..."
./gradlew :composeApp:buildRustAudioPlayer --no-build-cache --rerun-tasks

# 4) 强制重建 iOS 模拟器框架，带上最新的链接选项
echo "Step 3/3: Rebuilding Compose framework for iOS Simulator (arm64)..."
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 \
  --no-build-cache --rerun-tasks --stacktrace

echo "Done. Open Xcode and Run (or use xcodebuild) to install the new framework."
