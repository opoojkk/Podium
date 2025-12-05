#!/bin/bash
set -e

# Build script for podium-audio modular architecture
# Builds for multiple platforms: Android, iOS, JVM Desktop

echo "Building podium-audio..."

# Get the directory of this script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

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

    # Android ARM64
    cargo build --release --target aarch64-linux-android -p podium-bindings-android

    # Android x86_64 (for emulator)
    cargo build --release --target x86_64-linux-android -p podium-bindings-android

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
            cargo build --release --target aarch64-apple-darwin -p podium-bindings-android
            mkdir -p target/outputs/desktop/darwin-aarch64
            cp target/aarch64-apple-darwin/release/libpodium_bindings_android.dylib \
               target/outputs/desktop/darwin-aarch64/librust_audio_player.dylib 2>/dev/null || true
        else
            cargo build --release --target x86_64-apple-darwin -p podium-bindings-android
            mkdir -p target/outputs/desktop/darwin-x86_64
            cp target/x86_64-apple-darwin/release/libpodium_bindings_android.dylib \
               target/outputs/desktop/darwin-x86_64/librust_audio_player.dylib 2>/dev/null || true
        fi
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Linux (use JNI bindings with cpal renderer)
        cargo build --release -p podium-bindings-android
        mkdir -p target/outputs/desktop/linux-x86_64
        cp target/release/libpodium_bindings_android.so \
           target/outputs/desktop/linux-x86_64/librust_audio_player.so 2>/dev/null || true
    elif [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
        # Windows (use JNI bindings with cpal renderer)
        cargo build --release --target x86_64-pc-windows-msvc -p podium-bindings-android
        mkdir -p target/outputs/desktop/windows-x86_64
        cp target/x86_64-pc-windows-msvc/release/podium_bindings_android.dll \
           target/outputs/desktop/windows-x86_64/rust_audio_player.dll 2>/dev/null || true
    fi

    echo "Desktop build complete"
fi

echo "Build complete! Outputs in target/outputs/"
