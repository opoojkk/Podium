# Podium Audio

A modular, cross-platform Rust audio player engine designed for podcasts and music streaming.

## Architecture

Podium Audio is built as a modular workspace with clear separation of concerns:

```
podium-audio/
├── crates/
│   ├── core/                 # Core types, state management, callbacks
│   ├── ringbuffer/           # Lock-free PCM ring buffer
│   ├── renderer/             # Renderer API + platform targets
│   │   ├── android/          # Android implementation (Oboe)
│   │   └── ios/              # iOS/macOS implementation (cpal)
│   ├── transport-http/       # HTTP download and streaming
│   ├── source-buffer/        # Network source adapter for Symphonia
│   ├── demux/                # Format demuxing (MP3, AAC, FLAC, etc.)
│   ├── decode/               # Audio decoding to PCM
│   └── resampler/            # Sample rate and channel conversion
```

## Module Overview

### Core Modules

- **podium-core**: Foundation types including:
  - Player state machine (`PlayerState`, `PlayerStateContainer`)
  - Error types (`AudioError`, `Result`)
  - Callback system (`PlayerCallback`, `CallbackManager`)
  - Core player trait (`AudioPlayer`)

- **podium-ringbuffer**: Thread-safe PCM audio ring buffer
  - Lock-free reads and writes
  - Dynamic resizing
  - Optimized for low-latency playback

### Transport Layer

- **podium-transport-http**: HTTP-based audio transport
  - Progressive download with prebuffering
  - Range request support
  - Smart M4A/MP4 handling (full download)
  - Background download continuation

### Media Pipeline

- **podium-source-buffer**: Bridges HTTP transport to Symphonia
  - `NetworkSource` for HTTP range streaming
  - `StreamingSource` for progressive buffering

- **podium-demux**: Format demuxing
  - Wraps Symphonia's format readers
  - Supports MP3, AAC, M4A, FLAC, WAV, OGG, Opus
  - Track selection and seeking

- **podium-decode**: Audio decoding
  - Converts encoded packets to PCM samples
  - Automatic format conversion to f32
  - Interleaved output

- **podium-resampler**: Audio processing
  - Sample rate conversion (linear interpolation)
  - Channel conversion (mono ↔ stereo)

### Rendering Layer

- **podium-renderer**: Platform-agnostic renderer trait
  - `AudioRenderer` trait definition
  - Common audio specification types

- **podium-renderer-android**: Android implementation (under `crates/renderer/android`)
  - Uses Oboe for low-latency audio
  - AAudio/OpenSL ES backend selection
  - JNI-compatible

- **podium-renderer-ios**: iOS/macOS implementation (under `crates/renderer/ios`)
  - Uses cpal with CoreAudio backend
  - iOS SDK 18.4+ compatible
  - Low-latency playback

## Pipeline Flow

```
HTTP Download -> Source Buffer -> Demuxer -> Decoder -> Resampler -> Ring Buffer -> Renderer -> Output
     |              |              |          |          |            |           |
transport-http  source-buffer   demux      decode     resampler    ringbuffer   renderer-*
                                                                            (android/ios)
```

## Key Features

### Modular Design
- Each component is a separate crate
- Clear interfaces between modules
- Easy to replace or extend components
- Suitable for open-source contribution

### Cross-Platform
- Android (Oboe/AAudio)
- iOS/macOS (cpal/CoreAudio)
- Desktop (future: Windows/Linux via cpal)

### Performance Optimized
- Lock-free ring buffer
- Efficient sample conversion
- Minimal memory allocations
- Background download for streaming

### Smart Streaming
- Progressive buffering for instant playback
- HTTP Range requests for seeking
- Adaptive buffer sizing
- M4A/MP4 special handling (moov atom)

## Building

### Prerequisites
- Rust 1.70+
- Platform-specific SDKs:
  - Android: NDK 25+
  - iOS: Xcode 15+

### Build Commands

```bash
# Build all crates
cargo build --release

# Build for Android
cargo build --target aarch64-linux-android --release

# Build for iOS
cargo build --target aarch64-apple-ios --release
```

## Usage Example

```rust
use podium_core::{AudioPlayer, PlayerState};
use podium_renderer_android::OboeRenderer;
use podium_renderer::AudioSpec;

// Create renderer
let spec = AudioSpec {
    sample_rate: 48000,
    channels: 2,
    buffer_size: 1024,
};
let mut renderer = OboeRenderer::new(spec)?;

// Start playback
renderer.start()?;

// ... feed PCM data to renderer ...

// Stop playback
renderer.stop()?;
```

## Migration from Old Structure

The old monolithic `rust-audio-player` crate has been split into focused modules:

| Old Location | New Location |
|-------------|-------------|
| `player.rs`, `error.rs`, `callback.rs` | `podium-core` |
| `decoder.rs` (ring buffer) | `podium-ringbuffer` |
| `http_*.rs`, `m4a_*.rs` | `podium-transport-http` |
| `streaming_source.rs` | `podium-source-buffer` |
| `decoder.rs` (demux) | `podium-demux` |
| `decoder.rs` (decode) | `podium-decode` |
| `renderer trait` | `podium-renderer` (`crates/renderer`) |
| `android/mod.rs` | `podium-renderer-android` (`crates/renderer/android`) |
| `ios/mod.rs` | `podium-renderer-ios` (`crates/renderer/ios`) |

## Benefits of New Architecture

1. **Better Maintainability**: Each module has a single responsibility
2. **Easier Testing**: Modules can be tested independently
3. **Open Source Ready**: Clear APIs make contribution easier
4. **Extensible**: Easy to add new renderers, codecs, or transports
5. **Reusable**: Modules can be used in other projects

## License

MIT OR Apache-2.0

## Contributing

Contributions are welcome! Please see individual crate documentation for specific contribution guidelines.
