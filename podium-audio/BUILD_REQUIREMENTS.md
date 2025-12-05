# Build Requirements

## Prerequisites

### All Platforms
- Rust 1.70+ with cargo
- Git

### Android
- **Android NDK** (26.1+ recommended)
  - Install via Android Studio SDK Manager
  - Or download from: https://developer.android.com/ndk/downloads
  - Set `ANDROID_NDK_HOME` environment variable

```bash
# Example for macOS
export ANDROID_NDK_HOME="$HOME/Library/Android/sdk/ndk/26.1.10909125"

# Example for Linux
export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/26.1.10909125"

# Example for Windows
set ANDROID_NDK_HOME=C:\Users\YourName\AppData\Local\Android\Sdk\ndk\26.1.10909125
```

- **Rust Android targets**:
```bash
rustup target add aarch64-linux-android
rustup target add x86_64-linux-android
```

### iOS (macOS only)
- Xcode 14+ with Command Line Tools
- **Rust iOS targets**:
```bash
rustup target add aarch64-apple-ios
rustup target add aarch64-apple-ios-sim
rustup target add x86_64-apple-ios  # for Intel Mac simulators
```

### Desktop - macOS
```bash
rustup target add aarch64-apple-darwin  # Apple Silicon
rustup target add x86_64-apple-darwin   # Intel Mac
```

### Desktop - Linux
```bash
# Usually no additional targets needed for native build
# For cross-compilation, install appropriate toolchains
```

### Desktop - Windows
- Visual Studio 2019+ with C++ Build Tools
- Or: Build Tools for Visual Studio
```bash
rustup target add x86_64-pc-windows-msvc
```

## Building

### Quick Start
```bash
cd podium-audio

# Build for all platforms
./build.sh all

# Or build specific platforms
./build.sh android
./build.sh ios
./build.sh desktop
```

### Troubleshooting

#### "Cannot find NDK" on macOS/Linux
1. Install Android NDK via Android Studio
2. Set environment variable:
   ```bash
   export ANDROID_NDK_HOME="/path/to/ndk"
   ```
3. Or edit `.cargo/config.toml` to set linker paths manually

#### "Linker not found" on Android
The error message will show you the expected NDK path. Make sure:
1. NDK is installed at that location
2. The version matches (26.1+ recommended)
3. `ANDROID_NDK_HOME` points to the NDK root directory

#### Windows build fails
Make sure Visual Studio C++ Build Tools are installed:
- Download from: https://visualstudio.microsoft.com/downloads/
- Select "Desktop development with C++" workload

## Verification

After building, libraries will be in:
- Android: `target/outputs/android/`
- iOS: `target/outputs/ios/`
- Desktop: `target/outputs/desktop/`

Each platform subdirectory contains the appropriate library files.
