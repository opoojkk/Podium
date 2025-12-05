// cpal-based audio renderer for iOS/macOS

use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use cpal::{Device, Stream, StreamConfig};
use parking_lot::Mutex;
use podium_core::{AudioError, Result};
use podium_renderer_api::{AudioRenderer, AudioSpec};
use podium_ringbuffer::SharedRingBuffer;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

/// cpal audio renderer
pub struct CpalRenderer {
    stream: Option<Stream>,
    ring_buffer: SharedRingBuffer,
    is_playing: Arc<AtomicBool>,
    sample_rate: u32,
    channels: u16,
    buffer_size: usize,
    user_callback: Arc<Mutex<Option<podium_renderer_api::AudioCallback>>>,
}

impl CpalRenderer {
    pub fn new(spec: AudioSpec) -> Result<Self> {
        let host = cpal::default_host();
        let device = host
            .default_output_device()
            .ok_or_else(|| AudioError::InitializationError("No output device available".to_string()))?;

        let config = StreamConfig {
            channels: spec.channels,
            sample_rate: cpal::SampleRate(spec.sample_rate),
            buffer_size: cpal::BufferSize::Default,
        };

        let ring_buffer = SharedRingBuffer::new(spec.sample_rate as usize * spec.channels as usize * 4);
        let is_playing = Arc::new(AtomicBool::new(false));
        let user_callback: Arc<Mutex<Option<podium_renderer_api::AudioCallback>>> =
            Arc::new(Mutex::new(None));

        let ring_buffer_clone = ring_buffer.clone();
        let is_playing_clone = is_playing.clone();
        let user_callback_clone = user_callback.clone();

        let stream = device
            .build_output_stream(
                &config,
                move |data: &mut [f32], _: &cpal::OutputCallbackInfo| {
                    if !is_playing_clone.load(Ordering::Relaxed) {
                        data.fill(0.0);
                        return;
                    }

                    // Call user callback if set
                    let samples_written = if let Some(ref mut callback) = *user_callback_clone.lock() {
                        callback(data)
                    } else {
                        // Fallback: read from ring buffer
                        ring_buffer_clone.read(data)
                    };

                    // Zero-fill any unwritten samples to prevent playing stale data
                    if samples_written < data.len() {
                        data[samples_written..].fill(0.0);
                    }
                },
                |err| {
                    log::error!("Audio stream error: {}", err);
                },
                None,
            )
            .map_err(|e| AudioError::InitializationError(format!("Failed to build output stream: {}", e)))?;

        Ok(Self {
            stream: Some(stream),
            ring_buffer,
            is_playing,
            sample_rate: spec.sample_rate,
            channels: spec.channels,
            buffer_size: spec.buffer_size,
            user_callback,
        })
    }

    /// Get shared ring buffer for writing PCM data
    pub fn get_ring_buffer(&self) -> &SharedRingBuffer {
        &self.ring_buffer
    }
}

impl AudioRenderer for CpalRenderer {
    fn start(&mut self) -> Result<()> {
        if let Some(stream) = &self.stream {
            stream
                .play()
                .map_err(|e| AudioError::PlaybackError(format!("Failed to start stream: {}", e)))?;
            self.is_playing.store(true, Ordering::Relaxed);
        }
        Ok(())
    }

    fn stop(&mut self) -> Result<()> {
        self.is_playing.store(false, Ordering::Relaxed);
        if let Some(stream) = &self.stream {
            stream
                .pause()
                .map_err(|e| AudioError::PlaybackError(format!("Failed to stop stream: {}", e)))?;
        }
        Ok(())
    }

    fn pause(&mut self) -> Result<()> {
        self.is_playing.store(false, Ordering::Relaxed);
        if let Some(stream) = &self.stream {
            stream
                .pause()
                .map_err(|e| AudioError::PlaybackError(format!("Failed to pause stream: {}", e)))?;
        }
        Ok(())
    }

    fn resume(&mut self) -> Result<()> {
        self.is_playing.store(true, Ordering::Relaxed);
        if let Some(stream) = &self.stream {
            stream
                .play()
                .map_err(|e| AudioError::PlaybackError(format!("Failed to resume stream: {}", e)))?;
        }
        Ok(())
    }

    fn set_audio_callback(&mut self, callback: podium_renderer_api::AudioCallback) -> Result<()> {
        *self.user_callback.lock() = Some(callback);
        Ok(())
    }

    fn get_sample_rate(&self) -> u32 {
        self.sample_rate
    }

    fn get_channels(&self) -> u16 {
        self.channels
    }

    fn get_buffer_size(&self) -> usize {
        self.buffer_size
    }

    fn is_playing(&self) -> bool {
        self.is_playing.load(Ordering::Relaxed)
    }

    fn release(&mut self) -> Result<()> {
        self.stop()?;
        self.stream = None;
        Ok(())
    }
}
