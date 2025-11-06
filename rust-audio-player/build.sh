#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Print colored message
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Output directory
OUTPUT_DIR="$SCRIPT_DIR/target/outputs"
mkdir -p "$OUTPUT_DIR"

# Detect Android NDK
detect_android_ndk() {
    print_info "Detecting Android NDK..." >&2

    # Priority 1: Environment variable ANDROID_NDK_HOME
    if [ -n "$ANDROID_NDK_HOME" ] && [ -d "$ANDROID_NDK_HOME" ]; then
        echo "$ANDROID_NDK_HOME"
        return 0
    fi

    # Priority 2: Environment variable ANDROID_NDK
    if [ -n "$ANDROID_NDK" ] && [ -d "$ANDROID_NDK" ]; then
        echo "$ANDROID_NDK"
        return 0
    fi

    # Priority 3: Check Android SDK location
    if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME/ndk" ]; then
        NDK_VERSION=$(ls -1 "$ANDROID_HOME/ndk" | sort -V | tail -1)
        if [ -n "$NDK_VERSION" ]; then
            echo "$ANDROID_HOME/ndk/$NDK_VERSION"
            return 0
        fi
    fi

    # Priority 4: Common Linux locations
    if [ -d "$HOME/Android/Sdk/ndk" ]; then
        NDK_VERSION=$(ls -1 "$HOME/Android/Sdk/ndk" | sort -V | tail -1)
        if [ -n "$NDK_VERSION" ]; then
            echo "$HOME/Android/Sdk/ndk/$NDK_VERSION"
            return 0
        fi
    fi

    # Priority 5: Common macOS locations
    if [ -d "$HOME/Library/Android/sdk/ndk" ]; then
        NDK_VERSION=$(ls -1 "$HOME/Library/Android/sdk/ndk" | sort -V | tail -1)
        if [ -n "$NDK_VERSION" ]; then
            echo "$HOME/Library/Android/sdk/ndk/$NDK_VERSION"
            return 0
        fi
    fi

    print_error "Android NDK not found. Please set ANDROID_NDK_HOME or ANDROID_NDK environment variable." >&2
    return 1
}

# Install Rust targets if not already installed
install_targets() {
    print_info "Checking and installing required Rust targets..."

    # Android targets
    rustup target add aarch64-linux-android || true
    rustup target add armv7-linux-androideabi || true
    rustup target add i686-linux-android || true
    rustup target add x86_64-linux-android || true

    # Windows target
    if [[ "$OSTYPE" == "linux-gnu"* ]] || [[ "$OSTYPE" == "darwin"* ]]; then
        rustup target add x86_64-pc-windows-gnu || true
    fi

    # macOS targets
    if [[ "$OSTYPE" == "darwin"* ]]; then
        rustup target add x86_64-apple-darwin || true
        rustup target add aarch64-apple-darwin || true

        # iOS targets
        rustup target add aarch64-apple-ios || true
        rustup target add aarch64-apple-ios-sim || true
        rustup target add x86_64-apple-ios || true
    fi

    print_success "Rust targets installed"
}

# Build for Android
build_android() {
    print_info "Building for Android platforms..."

    # Detect NDK
    NDK_PATH=$(detect_android_ndk)
    if [ $? -ne 0 ]; then
        print_error "Skipping Android build - NDK not found"
        return 1
    fi

    print_success "Using Android NDK: $NDK_PATH"

    # Set up environment for Android builds
    export NDK_HOME="$NDK_PATH"

    # Android targets configuration
    declare -a ANDROID_TARGETS=(
        "aarch64-linux-android:aarch64:arm64-v8a"
        "armv7-linux-androideabi:armv7a:armeabi-v7a"
        "i686-linux-android:i686:x86"
        "x86_64-linux-android:x86_64:x86_64"
    )

    # Detect host OS for toolchain
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        HOST_TAG="linux-x86_64"
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        HOST_TAG="darwin-x86_64"
    else
        print_error "Unsupported host OS for Android build: $OSTYPE"
        return 1
    fi

    # Android API level
    ANDROID_API=24

    # Create output directory for Android
    ANDROID_OUTPUT_DIR="$OUTPUT_DIR/android"
    mkdir -p "$ANDROID_OUTPUT_DIR"

    # Build for each Android target
    for target_config in "${ANDROID_TARGETS[@]}"; do
        IFS=':' read -r TARGET ARCH ABI <<< "$target_config"

        print_info "Building for Android $ABI ($TARGET)..."

        # Set up toolchain paths
        TOOLCHAIN="$NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG"

        # Set environment variables for this target
        export CC="$TOOLCHAIN/bin/${ARCH}-linux-android${ANDROID_API}-clang"
        export CXX="$TOOLCHAIN/bin/${ARCH}-linux-android${ANDROID_API}-clang++"
        export AR="$TOOLCHAIN/bin/llvm-ar"
        export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"

        # Set Cargo-specific linker environment variable
        TARGET_UPPER=$(echo "$TARGET" | tr '[:lower:]' '[:upper:]' | tr '-' '_')
        export CARGO_TARGET_${TARGET_UPPER}_LINKER="$CC"

        # Special handling for armv7
        if [ "$TARGET" = "armv7-linux-androideabi" ]; then
            export CC="$TOOLCHAIN/bin/armv7a-linux-androideabi${ANDROID_API}-clang"
            export CXX="$TOOLCHAIN/bin/armv7a-linux-androideabi${ANDROID_API}-clang++"
            export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER="$CC"
        fi

        # Build
        cargo build --release --target "$TARGET" --features android

        # Copy to output directory with proper ABI naming
        ABI_OUTPUT_DIR="$ANDROID_OUTPUT_DIR/$ABI"
        mkdir -p "$ABI_OUTPUT_DIR"
        cp "target/$TARGET/release/librust_audio_player.so" "$ABI_OUTPUT_DIR/"

        print_success "Built for Android $ABI"
    done

    # Copy to composeApp jniLibs directory
    JNILIBS_DIR="$SCRIPT_DIR/../composeApp/src/androidMain/jniLibs"
    if [ -d "$JNILIBS_DIR" ]; then
        rm -rf "$JNILIBS_DIR"
    fi
    mkdir -p "$JNILIBS_DIR"
    cp -r "$ANDROID_OUTPUT_DIR"/* "$JNILIBS_DIR/"

    print_success "Android libraries copied to $JNILIBS_DIR"
}

# Build for Windows
build_windows() {
    print_info "Building for Windows..."

    # Check if we can cross-compile for Windows
    if command -v x86_64-w64-mingw32-gcc &> /dev/null; then
        export CC_x86_64_pc_windows_gnu=x86_64-w64-mingw32-gcc
        export CXX_x86_64_pc_windows_gnu=x86_64-w64-mingw32-g++
        export AR_x86_64_pc_windows_gnu=x86_64-w64-mingw32-ar

        cargo build --release --target x86_64-pc-windows-gnu --features desktop

        # Copy to output directory
        WINDOWS_OUTPUT_DIR="$OUTPUT_DIR/windows/x86_64"
        mkdir -p "$WINDOWS_OUTPUT_DIR"
        cp "target/x86_64-pc-windows-gnu/release/rust_audio_player.dll" "$WINDOWS_OUTPUT_DIR/" || \
        cp "target/x86_64-pc-windows-gnu/release/librust_audio_player.a" "$WINDOWS_OUTPUT_DIR/" || true

        print_success "Built for Windows x86_64"
    else
        print_warning "Skipping Windows build - mingw-w64 not found"
        print_info "To enable Windows builds on Linux, install: sudo apt-get install gcc-mingw-w64-x86-64"
    fi
}

# Build for macOS
build_macos() {
    if [[ "$OSTYPE" != "darwin"* ]]; then
        print_warning "Skipping macOS build - not running on macOS"
        return 0
    fi

    print_info "Building for macOS..."

    # Build for x86_64 (Intel)
    cargo build --release --target x86_64-apple-darwin --features desktop
    MACOS_OUTPUT_DIR="$OUTPUT_DIR/macos/x86_64"
    mkdir -p "$MACOS_OUTPUT_DIR"
    cp "target/x86_64-apple-darwin/release/librust_audio_player.dylib" "$MACOS_OUTPUT_DIR/" || \
    cp "target/x86_64-apple-darwin/release/librust_audio_player.a" "$MACOS_OUTPUT_DIR/" || true
    print_success "Built for macOS x86_64"

    # Build for aarch64 (Apple Silicon)
    cargo build --release --target aarch64-apple-darwin --features desktop
    MACOS_ARM_OUTPUT_DIR="$OUTPUT_DIR/macos/aarch64"
    mkdir -p "$MACOS_ARM_OUTPUT_DIR"
    cp "target/aarch64-apple-darwin/release/librust_audio_player.dylib" "$MACOS_ARM_OUTPUT_DIR/" || \
    cp "target/aarch64-apple-darwin/release/librust_audio_player.a" "$MACOS_ARM_OUTPUT_DIR/" || true
    print_success "Built for macOS aarch64"

    # Create universal binary
    print_info "Creating universal macOS binary..."
    MACOS_UNIVERSAL_DIR="$OUTPUT_DIR/macos/universal"
    mkdir -p "$MACOS_UNIVERSAL_DIR"

    if [ -f "target/x86_64-apple-darwin/release/librust_audio_player.dylib" ] && \
       [ -f "target/aarch64-apple-darwin/release/librust_audio_player.dylib" ]; then
        lipo -create \
            "target/x86_64-apple-darwin/release/librust_audio_player.dylib" \
            "target/aarch64-apple-darwin/release/librust_audio_player.dylib" \
            -output "$MACOS_UNIVERSAL_DIR/librust_audio_player.dylib"
        print_success "Created universal macOS binary"
    fi

    # Copy to composeApp jvmMain resources directory
    JVM_RESOURCES_DIR="$SCRIPT_DIR/../composeApp/src/jvmMain/resources"
    mkdir -p "$JVM_RESOURCES_DIR/darwin-aarch64"
    mkdir -p "$JVM_RESOURCES_DIR/darwin-x86_64"

    if [ -f "$MACOS_ARM_OUTPUT_DIR/librust_audio_player.dylib" ]; then
        cp "$MACOS_ARM_OUTPUT_DIR/librust_audio_player.dylib" "$JVM_RESOURCES_DIR/darwin-aarch64/"
        print_success "Copied aarch64 library to $JVM_RESOURCES_DIR/darwin-aarch64/"
    fi

    if [ -f "$MACOS_OUTPUT_DIR/librust_audio_player.dylib" ]; then
        cp "$MACOS_OUTPUT_DIR/librust_audio_player.dylib" "$JVM_RESOURCES_DIR/darwin-x86_64/"
        print_success "Copied x86_64 library to $JVM_RESOURCES_DIR/darwin-x86_64/"
    fi
}

# Build for iOS
build_ios() {
    if [[ "$OSTYPE" != "darwin"* ]]; then
        print_warning "Skipping iOS build - not running on macOS"
        return 0
    fi

    print_info "Building for iOS..."

    # Build for aarch64 (iOS devices - arm64)
    cargo build --release --target aarch64-apple-ios --features ios
    IOS_ARM64_OUTPUT_DIR="$OUTPUT_DIR/ios/aarch64"
    mkdir -p "$IOS_ARM64_OUTPUT_DIR"
    cp "target/aarch64-apple-ios/release/librust_audio_player.a" "$IOS_ARM64_OUTPUT_DIR/" || true
    print_success "Built for iOS arm64"

    # Build for aarch64 simulator (Apple Silicon Macs)
    cargo build --release --target aarch64-apple-ios-sim --features ios
    IOS_SIM_ARM64_OUTPUT_DIR="$OUTPUT_DIR/ios/aarch64-sim"
    mkdir -p "$IOS_SIM_ARM64_OUTPUT_DIR"
    cp "target/aarch64-apple-ios-sim/release/librust_audio_player.a" "$IOS_SIM_ARM64_OUTPUT_DIR/" || true
    print_success "Built for iOS Simulator arm64"

    # Build for x86_64 simulator (Intel Macs)
    cargo build --release --target x86_64-apple-ios --features ios
    IOS_SIM_X86_OUTPUT_DIR="$OUTPUT_DIR/ios/x86_64-sim"
    mkdir -p "$IOS_SIM_X86_OUTPUT_DIR"
    cp "target/x86_64-apple-ios/release/librust_audio_player.a" "$IOS_SIM_X86_OUTPUT_DIR/" || true
    print_success "Built for iOS Simulator x86_64"

    # Create XCFramework if xcodebuild is available
    if command -v xcodebuild &> /dev/null; then
        print_info "Creating XCFramework..."
        XCFRAMEWORK_DIR="$OUTPUT_DIR/ios/RustAudioPlayer.xcframework"
        rm -rf "$XCFRAMEWORK_DIR"

        # Create universal simulator library
        print_info "Creating universal iOS simulator library..."
        SIM_UNIVERSAL="$OUTPUT_DIR/ios/librust_audio_player_sim.a"
        lipo -create \
            "target/aarch64-apple-ios-sim/release/librust_audio_player.a" \
            "target/x86_64-apple-ios/release/librust_audio_player.a" \
            -output "$SIM_UNIVERSAL"

        # Create XCFramework with static libraries
        xcodebuild -create-xcframework \
            -library "target/aarch64-apple-ios/release/librust_audio_player.a" \
            -library "$SIM_UNIVERSAL" \
            -output "$XCFRAMEWORK_DIR"

        print_success "Created XCFramework"

        # Clean up temporary file
        rm -f "$SIM_UNIVERSAL"
    fi
}

# Main build process
main() {
    print_info "Starting cross-platform build for rust-audio-player..."
    print_info "Working directory: $SCRIPT_DIR"

    # Install required targets
    install_targets

    # Build for all platforms
    build_android
    build_windows
    build_macos
    build_ios

    # Summary
    echo ""
    print_success "Build completed!"
    print_info "Output directory: $OUTPUT_DIR"

    if [ -d "$OUTPUT_DIR/android" ]; then
        print_info "Android libraries:"
        find "$OUTPUT_DIR/android" -name "*.so" | while read -r lib; do
            echo "  - $lib"
        done
    fi

    if [ -d "$OUTPUT_DIR/windows" ]; then
        print_info "Windows libraries:"
        find "$OUTPUT_DIR/windows" -name "*.dll" -o -name "*.a" | while read -r lib; do
            echo "  - $lib"
        done
    fi

    if [ -d "$OUTPUT_DIR/macos" ]; then
        print_info "macOS libraries:"
        find "$OUTPUT_DIR/macos" -name "*.dylib" -o -name "*.a" | while read -r lib; do
            echo "  - $lib"
        done
    fi

    if [ -d "$OUTPUT_DIR/ios" ]; then
        print_info "iOS libraries:"
        find "$OUTPUT_DIR/ios" -name "*.a" -o -name "*.xcframework" | while read -r lib; do
            echo "  - $lib"
        done
    fi
}

# Run main
main
