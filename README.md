# Podium - 跨平台播客播放器

<div align="center">

**一个现代化的跨平台播客播放器，支持 Android、iOS 和桌面端**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-blue.svg)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.9.0-green.svg)](https://www.jetbrains.com/lp/compose-mpp/)
[![License](https://img.shields.io/badge/License-GPLv3-yellow.svg)](LICENSE)

**中文** | [English](README_EN.md)

</div>

## 📱 项目简介

Podium 是一个使用 Kotlin Multiplatform 和 Compose Multiplatform 技术构建的**泛用播客播放器**。它采用单一代码库实现多平台支持，提供了一致且原生的用户体验。

**平台状态**: ✅ Android 正常使用 | 🚧 iOS 开发中 | 🚧 Desktop 开发中

### ✨ 核心特性

- 🎯 **跨平台架构** - Android、iOS、Desktop (JVM) 三端统一代码库
- 🎨 **现代化 UI** - Material 3 设计 + Spotify 风格深色主题
- 🎵 **完整播放功能** - 播放控制、进度管理、倍速播放、睡眠定时器
- 📡 **RSS 订阅** - 支持标准 RSS/Atom 播客源订阅与解析
- 💾 **本地存储** - SQLDelight 跨平台数据持久化
- 📥 **离线下载** - 单集下载与管理
- 🔄 **播放进度同步** - 自动保存和恢复播放进度

## 🛠 技术栈

### 核心框架
- **[Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)** - 跨平台代码共享
- **[Compose Multiplatform](https://www.jetbrains.com/lp/compose-mpp/)** - 声明式跨平台 UI 框架
- **[Rust](https://www.rust-lang.org/)** - 高性能原生组件（RSS 解析 + 音频播放）
- **[SQLDelight](https://cashapp.github.io/sqldelight/)** - 跨平台类型安全数据库
- **[Ktor](https://ktor.io/)** - 跨平台网络框架
- **[Coil](https://coil-kt.github.io/coil/)** - 图片加载库

### 架构模式
- **MVVM + Repository Pattern** - 清晰的架构分层
- **Kotlin Coroutines + Flow** - 异步编程与响应式数据流
- **expect/actual 机制** - 跨平台差异化处理

### 核心组件（Rust 实现）

**RSS 解析器 (`rust-rss-parser`)**
- 高性能 XML/RSS/Atom 解析
- 零拷贝设计，低内存占用

**音频播放器 (`rust-audio-player`)**
- 跨平台音频解码（MP3, AAC, OGG, FLAC 等）
- 平台优化：Android (OpenSL ES/AAudio) | iOS (AVAudioEngine) | Desktop (cpal)

### 平台集成

- **Android**: Jetpack Compose + Media3 媒体会话
- **iOS**: SwiftUI + AVFoundation 音频管理
- **Desktop**: Compose for Desktop + Spotify 风格主题

## 🚧 近期计划

- [ ] **播放队列与列表** - 队列管理和自定义播放列表
- [ ] **搜索功能** - 全局搜索播客和单集
- [ ] **播客发现** - 推荐和热门播客
- [ ] **收藏功能** - 收藏喜欢的单集
- [ ] **多设备同步** - 云端数据同步（可选）
- [ ] **章节支持** - RSS 章节解析与跳转
- [ ] **OPML 导入/导出** - 订阅数据迁移
- [ ] **主题切换** - 浅色/深色/自动主题
- [ ] **音频均衡器** - 音效调节

## 🚀 快速开始

### 环境要求

- **JDK** 11+
- **Android Studio** Ladybug (2024.2.1)+
- **Xcode** 14.0+ (macOS, iOS 开发)
- **Rust** 1.70+ (自动编译原生组件)
  - 安装: `curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh`
  - iOS 开发: `rustup target add aarch64-apple-ios`

**平台版本要求**:
- Android: 最低 API 24 (Android 7.0), 目标 API 36 (Android 14)
- iOS: 最低 iOS 15.0

### 构建与运行

**Android**
```bash
./gradlew :composeApp:assembleDebug
```

**Desktop**
```bash
./gradlew :composeApp:run
```

**iOS**
```bash
# 在 Xcode 中打开 iosApp 目录，或使用命令行
cd iosApp
xcodebuild -scheme iosApp -configuration Debug
```

> 💡 首次构建时，Gradle 会自动编译 Rust 组件，可能需要几分钟。

## 📐 架构设计

Podium 采用分层架构，结合 Kotlin Multiplatform 和 Rust 原生组件：

```
┌─────────────────────────────────────────────────┐
│            UI Layer (Compose MP)                │
└────────────────────┬────────────────────────────┘
                     │ StateFlow / Flow
┌────────────────────▼────────────────────────────┐
│      Presentation (ViewModel / Controller)      │
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────┐
│         Repository (Data Abstraction)           │
└──────┬───────────────────┬──────────────────┬───┘
       │                   │                  │
   ┌───▼────┐       ┌─────▼──────┐      ┌────▼────┐
   │  RSS   │       │  Database  │      │ Player  │
   │ (Rust) │       │(SQLDelight)│      │ (Rust)  │
   └────────┘       └────────────┘      └─────────┘
```

**Rust 组件通过 JNI/FFI 桥接**：Android/Desktop 使用 JNI，iOS 使用 FFI，为 Kotlin/Swift 提供统一接口。

## 📄 许可证

本项目采用 **GPLv3** 许可证 - 查看 [LICENSE](LICENSE) 了解详情

## 🙏 致谢

感谢开源社区和以下项目：[JetBrains](https://www.jetbrains.com/) (Kotlin & Compose MP) · [Rust](https://www.rust-lang.org/) · [SQLDelight](https://cashapp.github.io/sqldelight/) · [Ktor](https://ktor.io/) · [Coil](https://coil-kt.github.io/coil/) · [cpal](https://github.com/RustAudio/cpal)

<div align="center">

**⭐ 如果这个项目对你有帮助，请给个 Star！ ⭐**

Made with ❤️ using Kotlin Multiplatform

[GitHub](https://github.com/opoojkk/podium) · [Issues](https://github.com/opoojkk/podium/issues)

</div>
