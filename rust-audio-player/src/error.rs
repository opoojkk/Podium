// Error handling for audio player

use std::fmt;

/// Audio player error types
#[derive(Debug, Clone)]
pub enum AudioError {
    /// Failed to initialize the audio player
    InitializationError(String),

    /// Failed to load audio file
    LoadError(String),

    /// Playback error
    PlaybackError(String),

    /// Invalid state transition
    InvalidState(String),

    /// Audio format not supported
    UnsupportedFormat(String),

    /// Device error (hardware issues)
    DeviceError(String),

    /// Thread/synchronization error
    ThreadError(String),

    /// JNI error (Android-specific)
    #[cfg(target_os = "android")]
    JniError(String),

    /// IO error
    IoError(String),

    /// Decoding error
    DecodingError(String),

    /// Network error (download/streaming)
    NetworkError(String),

    /// Generic error
    Other(String),
}

impl fmt::Display for AudioError {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self {
            AudioError::InitializationError(msg) => write!(f, "Initialization error: {}", msg),
            AudioError::LoadError(msg) => write!(f, "Load error: {}", msg),
            AudioError::PlaybackError(msg) => write!(f, "Playback error: {}", msg),
            AudioError::InvalidState(msg) => write!(f, "Invalid state: {}", msg),
            AudioError::UnsupportedFormat(msg) => write!(f, "Unsupported format: {}", msg),
            AudioError::DeviceError(msg) => write!(f, "Device error: {}", msg),
            AudioError::ThreadError(msg) => write!(f, "Thread error: {}", msg),
            #[cfg(target_os = "android")]
            AudioError::JniError(msg) => write!(f, "JNI error: {}", msg),
            AudioError::IoError(msg) => write!(f, "IO error: {}", msg),
            AudioError::DecodingError(msg) => write!(f, "Decoding error: {}", msg),
            AudioError::NetworkError(msg) => write!(f, "Network error: {}", msg),
            AudioError::Other(msg) => write!(f, "Error: {}", msg),
        }
    }
}

impl std::error::Error for AudioError {}

/// Result type alias for audio operations
pub type Result<T> = std::result::Result<T, AudioError>;

// Conversion implementations
impl From<std::io::Error> for AudioError {
    fn from(err: std::io::Error) -> Self {
        AudioError::IoError(err.to_string())
    }
}

#[cfg(target_os = "android")]
impl From<jni::errors::Error> for AudioError {
    fn from(err: jni::errors::Error) -> Self {
        AudioError::JniError(err.to_string())
    }
}
