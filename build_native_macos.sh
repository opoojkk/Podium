#!/bin/bash
# Build script for macOS native libraries

set -e

echo "Building Podium audio FFI for macOS..."

# Determine architecture
ARCH=$(uname -m)
if [ "$ARCH" = "arm64" ]; then
    TARGET="aarch64-apple-darwin"
    RESOURCE_DIR="darwin-aarch64"
elif [ "$ARCH" = "x86_64" ]; then
    TARGET="x86_64-apple-darwin"
    RESOURCE_DIR="darwin-x86_64"
else
    echo "Unsupported architecture: $ARCH"
    exit 1
fi

echo "Architecture: $ARCH"
echo "Rust target: $TARGET"
echo "Resource directory: $RESOURCE_DIR"

# Build Rust audio player (podium-player-ffi)
cd podium-audio
cargo build -p podium-player-ffi --release --target=$TARGET --features desktop
cd ..

# Copy the built library to resources
DEST_DIR="composeApp/src/jvmMain/resources/$RESOURCE_DIR"
mkdir -p "$DEST_DIR"
cp "podium-audio/target/$TARGET/release/libpodium_audio_player.dylib" "$DEST_DIR/"

echo "✅ Native library built and copied to $DEST_DIR"
echo ""
echo "You can now run the application with: ./gradlew :composeApp:jvmRun"
