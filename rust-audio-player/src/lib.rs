// Cross-platform audio player library
// Supports Android (x86, ARM, ARMv8), iOS, Windows, and macOS

// Platform-specific modules
#[cfg(target_os = "android")]
pub mod android;

#[cfg(any(target_os = "ios", target_os = "macos"))]
pub mod ios;

#[cfg(any(target_os = "windows", target_os = "linux", all(target_os = "macos", not(target_os = "ios"))))]
pub mod desktop;

pub mod player;
pub mod decoder;
pub mod error;
pub mod callback;
pub mod metadata;

// Re-exports
pub use player::{AudioPlayer, PlayerState, PlaybackStatus};
pub use error::{AudioError, Result};
pub use callback::{PlayerCallback, CallbackEvent};
pub use metadata::{AudioMetadata, AudioTags, FormatInfo, QualityParams, CoverArt, Chapter};

// JNI bindings for Android
#[cfg(target_os = "android")]
pub mod jni_bindings;

// Initialize logging based on platform
pub fn init_logging() {
    #[cfg(target_os = "android")]
    {
        android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(log::LevelFilter::Debug)
                .with_tag("RustAudioPlayer"),
        );
    }

    #[cfg(not(target_os = "android"))]
    {
        // For other platforms, you can use env_logger or other loggers
        // env_logger::init();
    }
}

/// Create a new audio player instance for the current platform
pub fn create_player() -> Result<Box<dyn AudioPlayer>> {
    init_logging();

    #[cfg(target_os = "android")]
    {
        log::info!("Creating Android audio player");
        Ok(Box::new(android::AndroidAudioPlayer::new()?))
    }

    #[cfg(target_os = "ios")]
    {
        log::info!("Creating iOS audio player");
        Ok(Box::new(ios::IOSAudioPlayer::new()?))
    }

    #[cfg(all(target_os = "macos", not(target_os = "ios")))]
    {
        log::info!("Creating macOS audio player");
        Ok(Box::new(desktop::DesktopAudioPlayer::new()?))
    }

    #[cfg(target_os = "windows")]
    {
        log::info!("Creating Windows audio player");
        Ok(Box::new(desktop::DesktopAudioPlayer::new()?))
    }

    #[cfg(target_os = "linux")]
    {
        log::info!("Creating Linux audio player");
        Ok(Box::new(desktop::DesktopAudioPlayer::new()?))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_player_creation() {
        let player = create_player();
        assert!(player.is_ok());
    }
}
