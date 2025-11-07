# 🚨 Rust Audio Player - 构建修复指南

## 当前问题

应用启动时崩溃，错误信息：
```
library "libc++_shared.so" not found
```

## 原因

Rust 音频播放器使用 Oboe (C++ 库)，需要 C++ 标准库，但：
1. 库文件还没有编译
2. `libc++_shared.so` 还没有被复制到 APK 中

## 🔧 解决步骤

### 第 1 步：重新编译 Rust 库

```bash
# 进入 Rust 项目目录
cd rust-audio-player

# 清理旧的构建产物
cargo clean

# 重新编译（这会自动复制 libc++_shared.so）
./build.sh
```

**预期输出**：
```
[INFO] Building for Android arm64-v8a (aarch64-linux-android)...
[SUCCESS] Built for Android arm64-v8a
[INFO] Building for Android armeabi-v7a (armv7-linux-androideabi)...
[SUCCESS] Built for Android armeabi-v7a
[INFO] Building for Android x86 (i686-linux-android)...
[SUCCESS] Built for Android x86
[INFO] Building for Android x86_64 (x86_64-linux-android)...
[SUCCESS] Built for Android x86_64
[INFO] Copying libc++_shared.so for each ABI...
  Copied libc++_shared.so for arm64-v8a
  Copied libc++_shared.so for armeabi-v7a
  Copied libc++_shared.so for x86
  Copied libc++_shared.so for x86_64
[SUCCESS] Android libraries copied to ../composeApp/src/androidMain/jniLibs
```

### 第 2 步：验证库文件已复制

```bash
# 返回项目根目录
cd ..

# 检查库文件
ls -la composeApp/src/androidMain/jniLibs/arm64-v8a/

# 应该看到两个文件:
# librust_audio_player.so  (我们的 Rust 库)
# libc++_shared.so         (C++ 标准库)
```

### 第 3 步：重新编译并安装 APK

```bash
# 清理旧的构建
./gradlew clean

# 重新编译并安装到设备
./gradlew installDebug

# 或者在 Android Studio 中点击 "Run" 按钮
```

## ✅ 验证成功

启动应用后，查看 logcat：

```bash
adb logcat -s RustAudioPlayer
```

**成功的日志应该显示**：
```
RustAudioPlayer: C++ standard library loaded
RustAudioPlayer: Native library loaded successfully
RustAudioPlayer: Audio player created with ID: 123456789
```

**不应该再看到**：
```
❌ library "libc++_shared.so" not found
❌ cannot locate symbol "__cxa_pure_virtual"
```

## 🐛 如果还有问题

### 问题 1: NDK 未找到

```
[ERROR] Android NDK not found
```

**解决**：
```bash
# 设置 NDK 路径
export ANDROID_NDK_HOME=$HOME/Library/Android/sdk/ndk/25.1.8937393

# 或者 (根据您的 NDK 版本)
export ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/25.1.8937393
```

### 问题 2: 编译错误

**解决**：检查您的 NDK 版本是否兼容，推荐使用 NDK 25.x

```bash
# 查看当前 NDK 版本
ls $ANDROID_NDK_HOME
```

### 问题 3: 库文件没有被复制

**手动复制**：
```bash
# 创建目录
mkdir -p composeApp/src/androidMain/jniLibs/arm64-v8a
mkdir -p composeApp/src/androidMain/jniLibs/armeabi-v7a
mkdir -p composeApp/src/androidMain/jniLibs/x86
mkdir -p composeApp/src/androidMain/jniLibs/x86_64

# 复制 Rust 库
cp rust-audio-player/target/aarch64-linux-android/release/librust_audio_player.so \
   composeApp/src/androidMain/jniLibs/arm64-v8a/

# 复制 C++ 标准库 (从 NDK)
cp $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so \
   composeApp/src/androidMain/jniLibs/arm64-v8a/
```

## 📊 构建文件结构

构建成功后，应该有这样的结构：

```
composeApp/src/androidMain/jniLibs/
├── arm64-v8a/
│   ├── librust_audio_player.so   ← Rust 音频播放器
│   └── libc++_shared.so          ← C++ 标准库
├── armeabi-v7a/
│   ├── librust_audio_player.so
│   └── libc++_shared.so
├── x86/
│   ├── librust_audio_player.so
│   └── libc++_shared.so
└── x86_64/
    ├── librust_audio_player.so
    └── libc++_shared.so
```

## 🎯 快速修复命令 (一键执行)

```bash
# 在项目根目录执行
cd rust-audio-player && \
cargo clean && \
./build.sh && \
cd .. && \
./gradlew clean installDebug
```

## 📝 技术说明

### 为什么需要 libc++_shared.so？

- Rust 音频播放器使用 **Oboe** (Google 的 C++ 音频库)
- Oboe 依赖 C++ 标准库的符号，如 `__cxa_pure_virtual`
- Android NDK 提供 `libc++_shared.so` 作为 C++ 运行时
- 我们需要将它打包到 APK 中，以便运行时加载

### 链接配置

我们在 `.cargo/config.toml` 中添加了：
```toml
[target.aarch64-linux-android]
rustflags = ["-C", "link-arg=-lc++_shared"]
```

这告诉 Rust 链接器依赖 `libc++_shared`，但库文件本身需要单独打包。

---

**状态**: 等待您运行 `./build.sh` 重新编译库

**下一步**: 编译完成后重新安装 APK 即可使用 Rust 播放器 🚀
