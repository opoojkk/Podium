# Rust Audio Player

Cross-platform audio player library written in Rust, supporting Android, iOS, Windows, macOS, and Linux.

## Features

- **Cross-platform**: Single codebase for Android (x86, ARM, ARMv8), iOS, Windows, macOS, and Linux
- **Low-latency audio**: Platform-specific optimizations for best performance
  - Android: Oboe (OpenSL ES / AAudio)
  - iOS: CoreAudio
  - Desktop: cpal
- **Format support**: MP3, AAC, FLAC, WAV, OGG, and more (via Symphonia)
- **Thread-safe**: Designed for concurrent access with minimal locking
- **Callback throttling**: Prevents excessive JNI calls on Android
- **Memory efficient**: Ring buffer architecture for smooth playback
- **State management**: Clear state machine with validation

## Architecture

### Core Components

1. **AudioPlayer trait**: Platform-agnostic interface
2. **Platform implementations**:
   - `AndroidAudioPlayer`: Android using Oboe
   - `IOSAudioPlayer`: iOS using CoreAudio
   - `DesktopAudioPlayer`: Windows/Mac/Linux using cpal
3. **AudioDecoder**: Format-agnostic audio decoding using Symphonia
4. **CallbackManager**: Thread-safe event dispatch with throttling
5. **JNI bindings**: Android interop layer

### Threading Model

- **Main thread**: State management and API calls
- **Decoder thread**: Background audio decoding
- **Audio callback thread**: Real-time audio output (platform-specific)

Ring buffers decouple decoding from playback, preventing blocking.

### Memory Management

- **Arc/Mutex**: Shared ownership with interior mutability
- **Atomic operations**: Lock-free flags for performance-critical paths
- **RAII**: Automatic resource cleanup on drop

## Building

### Prerequisites

- Rust 1.70+
- Platform-specific tools:
  - **Android**: Android NDK r21+
  - **iOS**: Xcode (macOS only)
  - **Windows cross-compilation**: mingw-w64

### Build All Platforms

```bash
cd rust-audio-player
./build.sh
```

### Build Specific Platform

```bash
# Android
cargo build --release --target aarch64-linux-android --features android

# iOS
cargo build --release --target aarch64-apple-ios --features ios

# Desktop
cargo build --release --features desktop
```

## Usage

### Rust API

```rust
use rust_audio_player::create_player;

let mut player = create_player().unwrap();

// Load audio file
player.load_file("/path/to/audio.mp3")?;

// Play
player.play()?;

// Pause
player.pause()?;

// Seek
player.seek(5000)?; // 5 seconds

// Set volume (0.0 - 1.0)
player.set_volume(0.8)?;

// Get status
let status = player.get_status();
println!("Position: {} / {}", status.position_ms, status.duration_ms);

// Release resources
player.release()?;
```

### Android (Kotlin)

```kotlin
// JNI wrapper (to be implemented)
val player = RustAudioPlayer()
player.loadFile("/path/to/audio.mp3")
player.play()
```

## Design Decisions

### 1. Build & ABI

- **Multiple ABIs**: Support for x86, ARM, ARMv8 on Android
- **Static linking**: Embedded dependencies to avoid version conflicts
- **Feature flags**: Conditional compilation for platform-specific code

### 2. JNI Optimization

- **Handle-based API**: Player instances managed via ID (not raw pointers)
- **Minimal JNI calls**: Callback throttling (default 100ms for position updates)
- **No JNI in audio thread**: All JNI interactions on decoder thread

### 3. Threading

- **Separate decoder thread**: Non-blocking audio decoding
- **Atomic flags**: Lock-free playback state checks
- **Ring buffer**: 4-second buffer to handle thread scheduling jitter

### 4. Memory Safety

- **No unsafe in public API**: All unsafe code encapsulated
- **RAII patterns**: Resources automatically released
- **Arc instead of raw pointers**: Reference counting prevents use-after-free

### 5. Debugging

- **Logging**: Platform-specific (android_logger on Android)
- **Error types**: Detailed error variants with context
- **State validation**: Invalid transitions caught early

### 6. Audio-Specific Optimizations

- **Direct audio APIs**: Bypass high-level frameworks for low latency
- **F32 samples**: Native format for most audio hardware
- **Interleaved buffers**: Matches platform expectations
- **No resampling in hot path**: Decoder thread handles format conversion

## Known Limitations

1. **Streaming**: URL loading not yet implemented
2. **Playback rate**: Speed adjustment not implemented (requires resampling)
3. **Advanced JNI callbacks**: Simplified implementation (needs JavaVM attachment)
4. **iOS audio session**: Basic configuration (no background audio handling yet)

## Testing

```bash
# Run tests
cargo test

# Run with logging
RUST_LOG=debug cargo test -- --nocapture
```

## Integration with Kotlin Multiplatform

The build script automatically copies libraries to the correct locations:

- **Android**: `composeApp/src/androidMain/jniLibs/`
- **macOS/JVM**: `composeApp/src/jvmMain/resources/darwin-*`

Gradle integration (add to `composeApp/build.gradle.kts`):

```kotlin
val buildRustAudioPlayer by tasks.registering(Exec::class) {
    workingDir(project.file("../rust-audio-player"))
    commandLine("bash", "build.sh")

    inputs.dir("../rust-audio-player/src")
    inputs.file("../rust-audio-player/Cargo.toml")
    outputs.dir("../rust-audio-player/target/outputs")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(buildRustAudioPlayer)
}
```

## License

See LICENSE file in project root.

## Contributing

1. Follow Rust API guidelines
2. Add tests for new features
3. Run `cargo fmt` and `cargo clippy` before committing
4. Update documentation for API changes
