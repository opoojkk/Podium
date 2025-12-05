// Core audio player trait and session management

use crate::callback::PlayerCallback;
use crate::error::Result;
use crate::state::{PlayerState, PlaybackStatus};
use std::sync::Arc;

/// Core audio player trait
/// All platform-specific implementations must implement this trait
pub trait AudioPlayer: Send + Sync {
    /// Load audio from a file path
    fn load_file(&mut self, path: &str) -> Result<()>;

    /// Load audio from a URL (streaming)
    fn load_url(&mut self, url: &str) -> Result<()>;

    /// Load audio from memory buffer
    fn load_buffer(&mut self, buffer: &[u8]) -> Result<()>;

    /// Start or resume playback
    fn play(&mut self) -> Result<()>;

    /// Pause playback
    fn pause(&mut self) -> Result<()>;

    /// Stop playback and reset position
    fn stop(&mut self) -> Result<()>;

    /// Seek to a specific position (in milliseconds)
    fn seek(&mut self, position_ms: u64) -> Result<()>;

    /// Set volume (0.0 - 1.0)
    fn set_volume(&mut self, volume: f32) -> Result<()>;

    /// Set playback rate/speed (1.0 = normal speed)
    fn set_playback_rate(&mut self, rate: f32) -> Result<()>;

    /// Get current player state
    fn get_state(&self) -> PlayerState;

    /// Get current playback status
    fn get_status(&self) -> PlaybackStatus;

    /// Set a callback for player events
    fn set_callback(&mut self, callback: Option<Arc<dyn PlayerCallback>>);

    /// Release all resources
    fn release(&mut self) -> Result<()>;

    /// Downcast to concrete type (for accessing platform-specific features)
    fn as_any(&self) -> &dyn std::any::Any;
}

/// Player session that coordinates the entire pipeline:
/// Download → Demux → Decode → Resample → RingBuffer → Render
pub struct Session {
    // This will be implemented as we build out the other components
    // For now, it's a placeholder for the coordinating structure
}

impl Session {
    pub fn new() -> Self {
        Self {}
    }
}
