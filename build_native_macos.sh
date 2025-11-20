#!/bin/bash
# Build script for macOS native libraries

set -e

echo "Building Rust audio player for macOS..."

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

# Build Rust audio player
cd rust-audio-player
cargo build --release --target=$TARGET
cd ..

# Copy the built library to resources
DEST_DIR="composeApp/src/jvmMain/resources/$RESOURCE_DIR"
mkdir -p "$DEST_DIR"
cp "rust-audio-player/target/$TARGET/release/librust_audio_player.dylib" "$DEST_DIR/"

echo "✅ Native library built and copied to $DEST_DIR"
echo ""
echo "You can now run the application with: ./gradlew :composeApp:jvmRun"
