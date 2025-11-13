# Podium - Cross-Platform Podcast Player

<div align="center">

**A modern cross-platform podcast player for Android, iOS, and Desktop**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-blue.svg)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.9.0-green.svg)](https://www.jetbrains.com/lp/compose-mpp/)
[![License](https://img.shields.io/badge/License-GPLv3-yellow.svg)](LICENSE)

[中文](README.md) | **English**

</div>

## 📱 Introduction

Podium is a **universal podcast player** built with Kotlin Multiplatform and Compose Multiplatform. It uses a single codebase to deliver consistent, native user experiences across multiple platforms.

**Platform Status**: ✅ Android Production | 🚧 iOS In Development | 🚧 Desktop In Development

### ✨ Key Features

- 🎯 **Cross-Platform Architecture** - Unified codebase for Android, iOS, and Desktop (JVM)
- 🎨 **Modern UI** - Material 3 design + Spotify-inspired dark theme
- 🎵 **Full Playback Controls** - Play/pause, progress, speed control, sleep timer
- 📡 **RSS Subscriptions** - Standard RSS/Atom podcast feed support
- 💾 **Local Storage** - SQLDelight cross-platform data persistence
- 📥 **Offline Downloads** - Episode download and management
- 🔄 **Progress Sync** - Automatic playback position saving and restoration

## 🛠 Tech Stack

### Core Frameworks
- **[Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)** - Cross-platform code sharing
- **[Compose Multiplatform](https://www.jetbrains.com/lp/compose-mpp/)** - Declarative cross-platform UI framework
- **[Rust](https://www.rust-lang.org/)** - High-performance native components (RSS parser + audio player)
- **[SQLDelight](https://cashapp.github.io/sqldelight/)** - Cross-platform type-safe database
- **[Ktor](https://ktor.io/)** - Cross-platform networking framework
- **[Coil](https://coil-kt.github.io/coil/)** - Image loading library

### Architecture Patterns
- **MVVM + Repository Pattern** - Clear architectural layering
- **Kotlin Coroutines + Flow** - Asynchronous programming and reactive data streams
- **expect/actual Mechanism** - Cross-platform differentiation handling

### Core Components (Rust Implementation)

**RSS Parser (`rust-rss-parser`)**
- High-performance XML/RSS/Atom parsing
- Zero-copy design with low memory footprint

**Audio Player (`rust-audio-player`)**
- Cross-platform audio decoding (MP3, AAC, OGG, FLAC, etc.)
- Platform optimizations: Android (OpenSL ES/AAudio) | iOS (AVAudioEngine) | Desktop (cpal)

### Platform Integration

- **Android**: Jetpack Compose + Media3 media session
- **iOS**: SwiftUI + AVFoundation audio management
- **Desktop**: Compose for Desktop + Spotify-style theme

## 🚧 Roadmap

- [ ] **Play Queue & Playlists** - Queue management and custom playlists
- [ ] **Search** - Global search for podcasts and episodes
- [ ] **Podcast Discovery** - Recommended and trending podcasts
- [ ] **Favorites** - Bookmark favorite episodes
- [ ] **Multi-Device Sync** - Cloud data synchronization (optional)
- [ ] **Chapter Support** - RSS chapter parsing and navigation
- [ ] **OPML Import/Export** - Subscription data migration
- [ ] **Theme Switching** - Light/dark/auto themes
- [ ] **Audio Equalizer** - Sound effect adjustments

## 🚀 Quick Start

### Requirements

- **JDK** 11+
- **Android Studio** Ladybug (2024.2.1)+
- **Xcode** 14.0+ (macOS, for iOS development)
- **Rust** 1.70+ (auto-compiles native components)
  - Install: `curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh`
  - iOS development: `rustup target add aarch64-apple-ios`

**Platform Version Requirements**:
- Android: Minimum API 24 (Android 7.0), Target API 36 (Android 14)
- iOS: Minimum iOS 15.0

### Build & Run

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
# Open iosApp directory in Xcode, or use command line
cd iosApp
xcodebuild -scheme iosApp -configuration Debug
```

> 💡 First build will auto-compile Rust components, which may take a few minutes.

## 📐 Architecture

Podium uses a layered architecture combining Kotlin Multiplatform and Rust native components:

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

**Rust components bridge via JNI/FFI**: Android/Desktop use JNI, iOS uses FFI, providing unified interfaces for Kotlin/Swift.

## 📄 License

This project is licensed under **GPLv3** - see [LICENSE](LICENSE) for details

## 🙏 Acknowledgements

Thanks to the open-source community and these projects: [JetBrains](https://www.jetbrains.com/) (Kotlin & Compose MP) · [Rust](https://www.rust-lang.org/) · [SQLDelight](https://cashapp.github.io/sqldelight/) · [Ktor](https://ktor.io/) · [Coil](https://coil-kt.github.io/coil/) · [cpal](https://github.com/RustAudio/cpal)

<div align="center">

**⭐ Star this project if you find it helpful! ⭐**

Made with ❤️ using Kotlin Multiplatform

[GitHub](https://github.com/opoojkk/podium) · [Issues](https://github.com/opoojkk/podium/issues)

</div>
