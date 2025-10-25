# Podium - 跨平台播客播放器

<div align="center">

**一个现代化的跨平台播客播放器，支持 Android、iOS 和桌面端**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-blue.svg)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.9.0-green.svg)](https://www.jetbrains.com/lp/compose-mpp/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

## 📱 项目简介

Podium 是一个使用 Kotlin Multiplatform 和 Compose Multiplatform 技术构建的**泛用播客播放器**。它采用单一代码库实现多平台支持，提供了一致且原生的用户体验。

### ✨ 核心特性

- 🎯 **跨平台支持** - Android、iOS、Desktop (JVM) 三端统一代码库
- 🎨 **现代化 UI** - 基于 Material 3 设计，桌面端采用 Spotify 风格深色主题
- 🎵 **完整播放功能** - 播放、暂停、快进快退、进度控制
- 📡 **RSS 订阅** - 支持标准 RSS/Atom 播客源订阅与解析
- 💾 **本地存储** - 使用 SQLDelight 实现跨平台数据持久化
- 📥 **离线下载** - 单集下载与管理，支持自动下载
- 🔄 **播放进度同步** - 自动保存和恢复播放进度
- 🎧 **播放历史** - 记录收听历史，快速继续播放
- 🖼️ **图片加载** - 高效的网络图片加载与缓存

---

## 🛠 技术栈

### 核心框架
- **[Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)** - 跨平台代码共享
- **[Compose Multiplatform](https://www.jetbrains.com/lp/compose-mpp/)** - 声明式跨平台 UI 框架
- **[Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)** - 异步编程与并发

### 架构设计
- **MVVM + Repository Pattern** - 清晰的架构分层
- **Flow & StateFlow** - 响应式数据流
- **平台期望/实现机制** - 跨平台差异化处理

### 平台特定技术

#### Android
- **[ExoPlayer](https://developer.android.com/media/media3/exoplayer)** - 高性能媒体播放
- **Android Media3** - 现代化媒体 API
- **Jetpack Compose** - 原生 Android UI

#### iOS  
- **AVPlayer** - Apple 原生音频播放框架
- **SwiftUI** - iOS 应用入口

#### Desktop (JVM)
- **[JLayer](http://www.javazoom.net/javalayer/javalayer.html)** - 纯 Java MP3 解码
- **Java Sound API** - WAV 等格式支持
- **Compose for Desktop** - 桌面 UI 渲染

---

## 📚 三方库依赖

### 网络 & 数据
| 库 | 版本 | 用途 |
|---|---|---|
| [Ktor Client](https://ktor.io/) | 2.3.12 | HTTP 客户端，RSS 订阅源获取 |
| [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization) | - | JSON/XML 序列化 |
| [SQLDelight](https://cashapp.github.io/sqldelight/) | 2.0.2 | 跨平台类型安全 SQL 数据库 |
| [Kotlinx DateTime](https://github.com/Kotlin/kotlinx-datetime) | 0.6.1 | 跨平台日期时间处理 |

### UI & 图片
| 库 | 版本 | 用途 |
|---|---|---|
| [Compose Material 3](https://developer.android.com/jetpack/compose/designsystems/material3) | - | Material Design 3 组件 |
| [Coil](https://coil-kt.github.io/coil/) | 3.0.4 | 高性能图片加载库 (支持 Compose Multiplatform) |

### 平台专用
| 库 | 版本 | 平台 | 用途 |
|---|---|---|---|
| Ktor OkHttp | 2.3.12 | Android | HTTP 引擎 |
| Ktor Darwin | 2.3.12 | iOS | HTTP 引擎 |
| Ktor CIO | 2.3.12 | JVM | HTTP 引擎 |
| JLayer | 1.0.1.4 | JVM | MP3 音频解码 |
| VLCj | 4.8.2 | JVM | (预留) 增强音频支持 |

### 开发工具
| 库 | 版本 | 用途 |
|---|---|---|
| [Compose Hot Reload](https://github.com/JetBrains/compose-hot-reload) | 1.0.0-beta07 | 开发时热重载 |

---

## 🎯 功能实现状态

### ✅ 已实现功能

#### 核心播放
- [x] 多平台音频播放 (Android ExoPlayer / iOS AVPlayer / JVM JLayer)
- [x] 播放控制 (播放、暂停、停止、继续)
- [x] 进度控制 (跳转、快进 30 秒、快退 15 秒)
- [x] 实时播放进度显示
- [x] 播放状态自动保存 (每 10 秒)
- [x] 应用重启后恢复播放状态
- [x] 缓冲状态显示

#### 订阅管理
- [x] RSS/Atom 播客源解析
- [x] 添加/删除订阅
- [x] 自动刷新订阅源
- [x] 订阅列表展示
- [x] 单集列表查看
- [x] 播客封面展示

#### 数据存储
- [x] SQLDelight 跨平台数据库
- [x] 播客信息持久化
- [x] 单集信息持久化
- [x] 播放进度持久化
- [x] 下载状态管理

#### 用户界面
- [x] 主页 - 最近更新、最近收听
- [x] 订阅页 - 订阅列表管理
- [x] 个人页 - 用户配置
- [x] 播放详情页 (全屏)
- [x] 底部播放控制栏
- [x] 桌面端侧边导航栏
- [x] 桌面端 Spotify 风格 UI
- [x] 移动端 Material 3 设计
- [x] 响应式布局适配

#### 其他
- [x] 图片异步加载与缓存
- [x] 网络请求日志
- [x] 错误处理
- [x] 跨平台依赖注入

### 🚧 计划实现功能

#### 近期计划 (v1.1 - v1.2)
- [ ] **播放队列** - 创建和管理播放列队
- [ ] **播放列表** - 自定义播放列表
- [ ] **搜索功能** - 全局搜索播客和单集
- [ ] **播客发现** - 推荐和热门播客
- [ ] **倍速播放** - 0.5x ~ 2.0x 变速播放
- [ ] **睡眠定时器** - 定时停止播放
- [ ] **播放历史** - 完整历史记录页面
- [ ] **收藏功能** - 收藏喜欢的单集

#### 计划 (v1.3 - v1.5)
- [ ] **多设备同步** - 云端数据同步 (可选)
- [ ] **章节支持** - RSS 章节信息解析与跳转
- [ ] **播客分类** - 自定义分类管理
- [ ] **导入/导出** - OPML 格式订阅导入导出
- [ ] **主题切换** - 浅色/深色/自动主题
- [ ] **音频均衡器** - 音效调节 (Android/iOS)
- [ ] **后台播放优化** - 更好的后台播放体验
- [ ] **通知控制** - 系统通知栏播放控制

---

## 🚀 构建与运行

### 环境要求

- **JDK**: 11 或更高版本
- **Android Studio**: Ladybug (2024.2.1) 或更高版本
- **Xcode**: 14.0+ (仅 macOS，用于 iOS 开发)
- **Kotlin**: 2.2.20
- **Gradle**: 8.10+ (自动下载)

### 构建 Android 应用

```bash
# macOS/Linux
./gradlew :composeApp:assembleDebug

# Windows
.\gradlew.bat :composeApp:assembleDebug
```

### 运行 Desktop 应用

```bash
# macOS/Linux
./gradlew :composeApp:run

# Windows
.\gradlew.bat :composeApp:run
```

### 构建 iOS 应用

1. 打开 `iosApp` 目录在 Xcode 中
2. 选择目标设备或模拟器
3. 点击运行按钮

或使用命令行 (需要 Xcode Command Line Tools):

```bash
cd iosApp
xcodebuild -scheme iosApp -configuration Debug
```

---

## 📐 架构设计

### 数据流

```
┌──────────────┐
│   UI Layer   │  (Composable Functions)
└──────┬───────┘
       │ StateFlow
┌──────▼───────┐
│ Presentation │  (Controller / ViewModel)
└──────┬───────┘
       │
┌──────▼───────┐
│  Repository  │  (Data Source Abstraction)
└──┬─────────┬─┘
   │         │
┌──▼──┐  ┌──▼──┐
│ RSS │  │ DB  │  (Remote & Local Data)
└─────┘  └─────┘
```

### 跨平台差异化处理

```kotlin
// 接口定义 (commonMain)
expect class PlatformContext
expect fun createPodcastPlayer(context: PlatformContext): PodcastPlayer

// Android 实现 (androidMain)
actual class PlatformContext(val context: Context)
actual fun createPodcastPlayer(context: PlatformContext): PodcastPlayer {
    return AndroidPodcastPlayer(context.context)
}

// iOS 实现 (iosMain)
actual class PlatformContext
actual fun createPodcastPlayer(context: PlatformContext): PodcastPlayer {
    return IosPodcastPlayer()
}

// Desktop 实现 (jvmMain)
actual class PlatformContext
actual fun createPodcastPlayer(context: PlatformContext): PodcastPlayer {
    return DesktopPodcastPlayer()
}
```

---

## 🎨 UI 设计

### 移动端 (Android/iOS)
- **设计语言**: Material Design 3
- **主题**: 动态取色 (Material You)
- **导航**: 底部导航栏
- **布局**: 自适应屏幕尺寸

### 桌面端 (Desktop)
- **设计语言**: Spotify 风格
- **配色方案**:
  - 主背景: `#181818` (深灰)
  - 侧边栏: `#000000` (纯黑)
  - 卡片背景: `#282828` (中等灰)
  - 强调色: `#1DB954` (Spotify 绿)
- **导航**: 左侧侧边导航栏
- **布局**: 三栏布局 (导航 + 内容 + 播放控制)

详细 UI 设计请参考 [UI_LAYOUT_GUIDE.md](./UI_LAYOUT_GUIDE.md)

---

## 🧪 测试

```bash
# 运行所有测试
./gradlew test

# Android 单元测试
./gradlew :composeApp:testDebugUnitTest

# iOS 测试
cd iosApp
xcodebuild test -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 15'
```

---

## 📦 打包发布

### Android APK

```bash
./gradlew :composeApp:assembleRelease
# 输出: composeApp/build/outputs/apk/release/
```

### Desktop 应用

```bash
# macOS DMG
./gradlew :composeApp:packageDmg

# Windows MSI
./gradlew :composeApp:packageMsi

# Linux DEB
./gradlew :composeApp:packageDeb
```

### iOS IPA

在 Xcode 中:
1. Product → Archive
2. Distribute App → 选择发布方式

---

## 🤝 贡献指南

欢迎贡献代码、报告问题或提出新功能建议！

### 贡献流程

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

### 代码规范

- 遵循 Kotlin 官方编码规范
- 使用有意义的变量和函数命名
- 为公共 API 添加 KDoc 注释
- 确保所有测试通过

---

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

---

## 🙏 致谢

- [JetBrains](https://www.jetbrains.com/) - Kotlin 和 Compose Multiplatform
- [Cash App](https://cashapp.github.io/sqldelight/) - SQLDelight
- [Ktor](https://ktor.io/) - 网络框架
- [Coil](https://coil-kt.github.io/coil/) - 图片加载

---

## 📧 联系方式

- 项目地址: [GitHub](https://github.com/opoojkk/podium)
- 问题反馈: [Issues](https://github.com/opoojkk/podium/issues)

---

<div align="center">

**⭐ 如果这个项目对你有帮助，请给个 Star！ ⭐**

Made with ❤️ using Kotlin Multiplatform

</div>
