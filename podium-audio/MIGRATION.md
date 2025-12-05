# Migration Guide: rust-audio-player → podium-audio

## 概述 / Overview

本文档介绍从单体 `rust-audio-player` 模块迁移到模块化 `podium-audio` 架构的过程。

This document describes the migration from the monolithic `rust-audio-player` module to the modular `podium-audio` architecture.

## 架构变化 / Architecture Changes

### 旧架构 / Old Architecture
```
rust-audio-player/
├── src/
│   ├── player.rs (所有功能 / all functionality)
│   ├── android.rs
│   ├── jni_bindings.rs
│   └── ...
└── Cargo.toml
```

### 新架构 / New Architecture
```
podium-audio/
├── crates/
│   ├── core/                    # 核心类型和错误 / Core types and errors
│   ├── transport-http/          # HTTP 下载和流式传输 / HTTP download and streaming
│   ├── source-buffer/           # 媒体源缓冲 / Media source buffering
│   ├── demux-symphonia/         # 解封装 / Demuxing
│   ├── decode-symphonia/        # 解码 / Decoding
│   ├── resampler/               # 重采样 / Resampling
│   ├── ringbuffer/              # 环形缓冲区 / Ring buffer
│   ├── renderer-api/            # 渲染器 API / Renderer API
│   ├── renderer-android/        # Android (Oboe) 渲染器 / Android renderer
│   ├── renderer-ios/            # iOS/Desktop (cpal) 渲染器 / iOS/Desktop renderer
│   ├── bindings-android/        # JNI 绑定 (Android + JVM Desktop) / JNI bindings
│   └── bindings-ios/            # C FFI 绑定 (iOS) / C FFI bindings
└── Cargo.toml
```

## 平台支持 / Platform Support

### Android 📱
- **接口**: JNI (bindings-android)
- **渲染器**: Oboe (renderer-android)
- **优势**: Google 官方推荐，最佳性能和延迟

### iOS 🍎
- **接口**: C FFI (bindings-ios)
- **渲染器**: cpal (renderer-ios)
- **优势**: 原生 iOS 音频支持

### JVM Desktop 🖥️
- **接口**: JNI (bindings-android，复用 Android 绑定)
- **渲染器**: cpal (renderer-ios，通过条件编译)
- **平台**: Windows, macOS, Linux
- **优势**: 跨平台统一接口，代码复用

**架构智能化**：`bindings-android` 根据编译目标自动选择渲染器：
- Android → Oboe
- 其他平台 → cpal
```

## 优势 / Benefits

### 1. 模块化 / Modularity
- 每个 crate 职责单一、边界清晰 / Single responsibility per crate
- 更容易测试和维护 / Easier to test and maintain
- 可以独立替换组件 / Components can be replaced independently

### 2. 可复用性 / Reusability
- 各个模块可以在其他项目中复用 / Modules can be reused in other projects
- 清晰的 API 边界 / Clear API boundaries

### 3. 性能 / Performance
- 修复了 5 个关键性能和正确性问题:
  - P1: Range 响应验证 (防止文件损坏)
  - P1: EOF 处理 (防止无效请求)
  - P2: 完整音频格式支持 (防止静音)
  - P1: 音频渲染器样本计数 (防止音频伪影)
  - P1: 缓存驱逐逻辑 (防止重复下载)

- Fixed 5 critical performance and correctness issues:
  - P1: Range response validation (prevents file corruption)
  - P1: EOF handling (prevents invalid requests)
  - P2: Complete audio format support (prevents silent playback)
  - P1: Audio renderer sample count (prevents audio artifacts)
  - P1: Cache eviction logic (prevents repeated downloads)

## API 兼容性 / API Compatibility

### Kotlin (Android)
**无需更改!** `RustAudioPlayer.kt` 的 API 保持完全兼容。

**No changes needed!** The `RustAudioPlayer.kt` API remains fully compatible.

```kotlin
// 使用方式完全相同 / Usage remains exactly the same
val player = RustAudioPlayer()
player.loadUrl("https://example.com/audio.mp3")
player.play()
```

### Swift (iOS)
**需要更新函数名!** C FFI 使用新的函数名前缀。

**Function names updated!** C FFI uses new function name prefix.

```swift
// 旧 API / Old API
let playerId = rust_audio_player_create()
rust_audio_player_play(playerId)

// 新 API / New API
let playerId = podium_player_create()
podium_player_play(playerId)
```

## 迁移步骤 / Migration Steps

### 已完成 / Completed ✅

1. ✅ 创建模块化架构 (10 个 crates)
2. ✅ 实现 Android JNI bindings (API 兼容)
3. ✅ 实现 iOS C FFI bindings
4. ✅ 更新构建脚本 (`podium-audio/build.sh`)
5. ✅ 更新 `build.gradle.kts` 引用新路径

### 待完成 / To Do ⏳

1. ⏳ 更新 iOS Swift 代码使用新的函数名
2. ⏳ 测试 Android 构建和运行时
3. ⏳ 测试 iOS 构建和运行时
4. ⏳ (可选) 删除旧的 `rust-audio-player` 目录

## 构建说明 / Build Instructions

### Android
```bash
cd podium-audio
./build.sh android

# 输出位置 / Output location:
# target/outputs/android/arm64-v8a/librust_audio_player.so
# target/outputs/android/x86_64/librust_audio_player.so
```

### iOS
```bash
cd podium-audio
./build.sh ios

# 输出位置 / Output location:
# target/outputs/ios/libpodium_bindings_ios.a
# target/outputs/ios/libpodium_bindings_ios_sim.a
```

### 全部平台 / All Platforms
```bash
cd podium-audio
./build.sh all
```

## 测试清单 / Testing Checklist

### Android 测试 / Android Testing
- [ ] 构建成功 / Build succeeds
- [ ] 应用启动无崩溃 / App launches without crash
- [ ] 播放本地文件 / Play local file
- [ ] 流式播放 URL / Stream URL
- [ ] 暂停/恢复 / Pause/resume
- [ ] 音量控制 / Volume control
- [ ] 后台播放 / Background playback

### iOS 测试 / iOS Testing
- [ ] 构建成功 / Build succeeds
- [ ] 应用启动无崩溃 / App launches without crash
- [ ] 播放本地文件 / Play local file
- [ ] 流式播放 URL / Stream URL
- [ ] 暂停/恢复 / Pause/resume
- [ ] 音量控制 / Volume control
- [ ] 后台播放 / Background playback

## 常见问题 / FAQ

### Q: 为什么 Android 库名还是 `librust_audio_player.so`?
A: 为了保持向后兼容,构建脚本将 `libpodium_bindings_android.so` 重命名为 `librust_audio_player.so`,这样 Kotlin 代码无需修改。

### Q: Why is the Android library still named `librust_audio_player.so`?
A: For backward compatibility, the build script renames `libpodium_bindings_android.so` to `librust_audio_player.so`, so Kotlin code needs no changes.

---

### Q: 旧的 rust-audio-player 可以删除吗?
A: 在完成所有测试后可以删除。建议先保留一段时间作为备份。

### Q: Can I delete the old rust-audio-player?
A: Yes, after completing all tests. Recommended to keep it as backup for a while.

---

### Q: 性能有提升吗?
A: 是的!新架构修复了多个导致文件损坏、音频伪影和重复下载的关键问题。

### Q: Is performance improved?
A: Yes! The new architecture fixes multiple critical issues causing file corruption, audio artifacts, and repeated downloads.

## 技术细节 / Technical Details

### 音频管道流程 / Audio Pipeline Flow

```
HTTP Download → Source Buffer → Demuxer → Decoder → Resampler → Ring Buffer → Renderer → Output
     ↓               ↓            ↓          ↓          ↓            ↓          ↓
transport-http  source-buffer  demux-   decode-   resampler   ringbuffer  renderer-android
                              symphonia symphonia                        /renderer-ios
```

### 关键改进 / Key Improvements

1. **HTTP Range 支持 / HTTP Range Support**
   - 智能检测服务器是否支持 Range 请求
   - 对不支持 Range 的服务器采用不同的缓存策略
   - Smart detection of Range support
   - Different caching strategy for Range-unsupported servers

2. **完整格式支持 / Complete Format Support**
   - 支持所有 Symphonia 音频缓冲格式 (F32, F64, S8-S32, U8-U32)
   - 正确处理 S24/U24 newtype
   - Support for all Symphonia audio buffer formats
   - Correct handling of S24/U24 newtypes

3. **零填充 / Zero-filling**
   - 防止缓冲区欠载时的音频伪影
   - Prevents audio artifacts from buffer underruns

## 联系方式 / Contact

如有问题,请在 GitHub 创建 issue。

For questions, please create an issue on GitHub.
