// Audio renderer abstraction layer

use podium_core::Result;

/// Type alias for audio callback
pub type AudioCallback = Box<dyn FnMut(&mut [f32]) -> usize + Send + 'static>;

/// Audio renderer trait
/// Platform-specific implementations (Android, iOS, Desktop) implement this trait
pub trait AudioRenderer: Send + Sync {
    /// Start the audio stream
    fn start(&mut self) -> Result<()>;

    /// Stop the audio stream
    fn stop(&mut self) -> Result<()>;

    /// Pause the audio stream
    fn pause(&mut self) -> Result<()>;

    /// Resume the audio stream
    fn resume(&mut self) -> Result<()>;

    /// Set the audio callback that provides PCM data
    /// The callback receives a mutable slice and should fill it with f32 samples
    /// Returns the number of samples actually written
    fn set_audio_callback(&mut self, callback: AudioCallback) -> Result<()>;

    /// Get the sample rate of the audio output device
    fn get_sample_rate(&self) -> u32;

    /// Get the number of channels
    fn get_channels(&self) -> u16;

    /// Get buffer size in frames
    fn get_buffer_size(&self) -> usize;

    /// Check if the renderer is currently playing
    fn is_playing(&self) -> bool;

    /// Release all audio resources
    fn release(&mut self) -> Result<()>;
}

/// Audio format specification for the renderer
#[derive(Debug, Clone, Copy)]
pub struct AudioSpec {
    pub sample_rate: u32,
    pub channels: u16,
    pub buffer_size: usize,
}

impl Default for AudioSpec {
    fn default() -> Self {
        Self {
            sample_rate: 48000,
            channels: 2,
            buffer_size: 1024,
        }
    }
}

/// Audio renderer factory trait
/// Allows creating platform-specific renderers
pub trait RendererFactory: Send + Sync {
    /// Create a new renderer with the given audio specification
    fn create_renderer(&self, spec: AudioSpec) -> Result<Box<dyn AudioRenderer>>;

    /// Get the preferred audio specification for this platform
    fn get_preferred_spec(&self) -> AudioSpec;
}
