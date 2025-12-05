#!/bin/bash
set -e

# Build script for podium-audio modular architecture
# Builds for multiple platforms: Android, iOS, JVM Desktop

echo "Building podium-audio..."

# Get the directory of this script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Auto-detect Android NDK if not set
if [ -z "$ANDROID_NDK_HOME" ]; then
    # Try common NDK locations
    if [ -d "$HOME/Library/Android/sdk/ndk" ]; then
        # macOS
        NDK_VERSION=$(ls -1 "$HOME/Library/Android/sdk/ndk" | sort -V | tail -n 1)
        if [ -n "$NDK_VERSION" ]; then
            export ANDROID_NDK_HOME="$HOME/Library/Android/sdk/ndk/$NDK_VERSION"
            echo "Auto-detected Android NDK: $ANDROID_NDK_HOME"
        fi
    elif [ -d "$HOME/Android/Sdk/ndk" ]; then
        # Linux
        NDK_VERSION=$(ls -1 "$HOME/Android/Sdk/ndk" | sort -V | tail -n 1)
        if [ -n "$NDK_VERSION" ]; then
            export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/$NDK_VERSION"
            echo "Auto-detected Android NDK: $ANDROID_NDK_HOME"
        fi
    elif [ -d "$LOCALAPPDATA/Android/Sdk/ndk" ]; then
        # Windows
        NDK_VERSION=$(ls -1 "$LOCALAPPDATA/Android/Sdk/ndk" | sort -V | tail -n 1)
        if [ -n "$NDK_VERSION" ]; then
            export ANDROID_NDK_HOME="$LOCALAPPDATA/Android/Sdk/ndk/$NDK_VERSION"
            echo "Auto-detected Android NDK: $ANDROID_NDK_HOME"
        fi
    fi
fi

# Set up Android NDK linkers if building for Android
setup_android_ndk() {
    if [ -z "$ANDROID_NDK_HOME" ]; then
        echo "ERROR: ANDROID_NDK_HOME not set and could not auto-detect NDK"
        echo "Please install Android NDK or set ANDROID_NDK_HOME environment variable"
        echo "See BUILD_REQUIREMENTS.md for details"
        exit 1
    fi

    # Detect NDK host platform
    case "$(uname -s)" in
        Darwin*)
            if [ "$(uname -m)" = "arm64" ]; then
                NDK_HOST="darwin-x86_64"  # NDK still uses x86_64 on Apple Silicon
            else
                NDK_HOST="darwin-x86_64"
            fi
            ;;
        Linux*)
            NDK_HOST="linux-x86_64"
            ;;
        MINGW*|MSYS*|CYGWIN*)
            NDK_HOST="windows-x86_64"
            ;;
        *)
            echo "ERROR: Unknown host platform"
            exit 1
            ;;
    esac

    # Set linker environment variables for cargo
    NDK_TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$NDK_HOST/bin"

    if [ ! -d "$NDK_TOOLCHAIN" ]; then
        echo "ERROR: NDK toolchain not found at: $NDK_TOOLCHAIN"
        echo "Please verify ANDROID_NDK_HOME is correct: $ANDROID_NDK_HOME"
        exit 1
    fi

    export CC_aarch64_linux_android="$NDK_TOOLCHAIN/aarch64-linux-android21-clang"
    export CXX_aarch64_linux_android="$NDK_TOOLCHAIN/aarch64-linux-android21-clang++"
    export AR_aarch64_linux_android="$NDK_TOOLCHAIN/llvm-ar"
    export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$NDK_TOOLCHAIN/aarch64-linux-android21-clang"

    export CC_x86_64_linux_android="$NDK_TOOLCHAIN/x86_64-linux-android21-clang"
    export CXX_x86_64_linux_android="$NDK_TOOLCHAIN/x86_64-linux-android21-clang++"
    export AR_x86_64_linux_android="$NDK_TOOLCHAIN/llvm-ar"
    export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$NDK_TOOLCHAIN/x86_64-linux-android21-clang"

    echo "Android NDK configured: $ANDROID_NDK_HOME"
}

# Parse command line arguments
BUILD_ANDROID=false
BUILD_IOS=false
BUILD_DESKTOP=false
BUILD_ALL=false

if [ $# -eq 0 ]; then
    BUILD_ALL=true
else
    for arg in "$@"; do
        case $arg in
            android) BUILD_ANDROID=true ;;
            ios) BUILD_IOS=true ;;
            desktop) BUILD_DESKTOP=true ;;
            all) BUILD_ALL=true ;;
            *) echo "Unknown target: $arg"; exit 1 ;;
        esac
    done
fi

# If BUILD_ALL is true, enable all platforms
if [ "$BUILD_ALL" = true ]; then
    BUILD_ANDROID=true
    BUILD_IOS=true
    BUILD_DESKTOP=true
fi

# Create output directories
mkdir -p target/outputs/android
mkdir -p target/outputs/ios
mkdir -p target/outputs/desktop

# Build for Android
if [ "$BUILD_ANDROID" = true ]; then
    echo "Building for Android..."

    # Set up Android NDK environment
    setup_android_ndk

    # Android ARM64
    cargo build --release --target aarch64-linux-android -p podium-bindings-android --features android

    # Android x86_64 (for emulator)
    cargo build --release --target x86_64-linux-android -p podium-bindings-android --features android

    # Copy libraries to output
    mkdir -p target/outputs/android/arm64-v8a
    mkdir -p target/outputs/android/x86_64

    # Copy the bindings library (this is what gets loaded by the app)
    if [ -f "target/aarch64-linux-android/release/libpodium_bindings_android.so" ]; then
        # Rename to match the expected library name in RustAudioPlayer.kt
        cp target/aarch64-linux-android/release/libpodium_bindings_android.so \
           target/outputs/android/arm64-v8a/librust_audio_player.so
    fi

    if [ -f "target/x86_64-linux-android/release/libpodium_bindings_android.so" ]; then
        cp target/x86_64-linux-android/release/libpodium_bindings_android.so \
           target/outputs/android/x86_64/librust_audio_player.so
    fi

    echo "Android build complete"
fi

# Build for iOS
if [ "$BUILD_IOS" = true ]; then
    echo "Building for iOS..."

    # iOS ARM64 (device)
    cargo build --release --target aarch64-apple-ios -p podium-bindings-ios

    # iOS simulator ARM64
    cargo build --release --target aarch64-apple-ios-sim -p podium-bindings-ios

    # iOS simulator x86_64 (Intel Macs)
    if [ "$(uname -m)" = "x86_64" ]; then
        cargo build --release --target x86_64-apple-ios -p podium-bindings-ios
    fi

    # Copy libraries to output
    mkdir -p target/outputs/ios

    if [ -f "target/aarch64-apple-ios/release/libpodium_bindings_ios.a" ]; then
        cp target/aarch64-apple-ios/release/libpodium_bindings_ios.a \
           target/outputs/ios/
    fi

    if [ -f "target/aarch64-apple-ios-sim/release/libpodium_bindings_ios.a" ]; then
        cp target/aarch64-apple-ios-sim/release/libpodium_bindings_ios.a \
           target/outputs/ios/libpodium_bindings_ios_sim.a
    fi

    echo "iOS build complete"
fi

# Build for Desktop (JVM)
if [ "$BUILD_DESKTOP" = true ]; then
    echo "Building for Desktop (JVM with JNI + cpal)..."

    # Detect host platform
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS (use JNI bindings with cpal renderer)
        if [ "$(uname -m)" = "arm64" ]; then
            cargo build --release --target aarch64-apple-darwin -p podium-bindings-android --features desktop
            mkdir -p target/outputs/desktop/darwin-aarch64
            cp target/aarch64-apple-darwin/release/libpodium_bindings_android.dylib \
               target/outputs/desktop/darwin-aarch64/librust_audio_player.dylib 2>/dev/null || true
        else
            cargo build --release --target x86_64-apple-darwin -p podium-bindings-android --features desktop
            mkdir -p target/outputs/desktop/darwin-x86_64
            cp target/x86_64-apple-darwin/release/libpodium_bindings_android.dylib \
               target/outputs/desktop/darwin-x86_64/librust_audio_player.dylib 2>/dev/null || true
        fi
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Linux (use JNI bindings with cpal renderer)
        cargo build --release -p podium-bindings-android --features desktop
        mkdir -p target/outputs/desktop/linux-x86_64
        cp target/release/libpodium_bindings_android.so \
           target/outputs/desktop/linux-x86_64/librust_audio_player.so 2>/dev/null || true
    elif [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
        # Windows (use JNI bindings with cpal renderer)
        cargo build --release --target x86_64-pc-windows-msvc -p podium-bindings-android --features desktop
        mkdir -p target/outputs/desktop/windows-x86_64
        cp target/x86_64-pc-windows-msvc/release/podium_bindings_android.dll \
           target/outputs/desktop/windows-x86_64/rust_audio_player.dll 2>/dev/null || true
    fi

    echo "Desktop build complete"
fi

echo "Build complete! Outputs in target/outputs/"
