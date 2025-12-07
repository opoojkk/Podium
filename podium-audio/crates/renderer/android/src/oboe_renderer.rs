// Oboe-based audio renderer for Android

use oboe::{
    AudioOutputStream, AudioStream, AudioStreamAsync, AudioStreamBase, AudioStreamBuilder,
    DataCallbackResult, Output, PerformanceMode, SharingMode, Stereo,
};
use parking_lot::Mutex;
use podium_core::{AudioError, Result};
use podium_renderer::{AudioRenderer, AudioSpec};
use podium_ringbuffer::SharedRingBuffer;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

/// Audio callback for Oboe
struct OboeCallback {
    ring_buffer: SharedRingBuffer,
    is_playing: Arc<AtomicBool>,
    user_callback: Arc<Mutex<Option<podium_renderer::AudioCallback>>>,
}

impl oboe::AudioOutputCallback for OboeCallback {
    type FrameType = (f32, Stereo);

    fn on_audio_ready(
        &mut self,
        _stream: &mut dyn oboe::AudioOutputStreamSafe,
        output: &mut [(f32, f32)],
    ) -> DataCallbackResult {
        if !self.is_playing.load(Ordering::Relaxed) {
            // Fill with silence when not playing
            for frame in output.iter_mut() {
                *frame = (0.0, 0.0);
            }
            return DataCallbackResult::Continue;
        }

        // Call user callback if set
        if let Some(ref mut callback) = *self.user_callback.lock() {
            let frame_count = output.len();
            let mut interleaved = vec![0.0f32; frame_count * 2];
            let written = callback(&mut interleaved);

            // Convert interleaved to frame format
            for (i, frame) in output.iter_mut().enumerate() {
                let idx = i * 2;
                if idx + 1 < written {
                    frame.0 = interleaved[idx];
                    frame.1 = interleaved[idx + 1];
                } else {
                    *frame = (0.0, 0.0);
                }
            }
        } else {
            // Fallback: read from ring buffer
            let frame_count = output.len();
            let mut interleaved = vec![0.0f32; frame_count * 2];
            let samples_read = self.ring_buffer.read(&mut interleaved);

            for (i, frame) in output.iter_mut().enumerate() {
                let idx = i * 2;
                if idx + 1 < samples_read {
                    frame.0 = interleaved[idx];
                    frame.1 = interleaved[idx + 1];
                } else {
                    *frame = (0.0, 0.0);
                }
            }
        }

        DataCallbackResult::Continue
    }
}

/// Oboe audio renderer
pub struct OboeRenderer {
    stream: Option<AudioStreamAsync<Output, OboeCallback>>,
    ring_buffer: SharedRingBuffer,
    is_playing: Arc<AtomicBool>,
    sample_rate: u32,
    channels: u16,
    buffer_size: usize,
    user_callback: Arc<Mutex<Option<podium_renderer::AudioCallback>>>,
}

impl OboeRenderer {
    pub fn new(spec: AudioSpec) -> Result<Self> {
        let ring_buffer = SharedRingBuffer::new(spec.sample_rate as usize * spec.channels as usize * 4);
        let is_playing = Arc::new(AtomicBool::new(false));
        let user_callback = Arc::new(Mutex::new(None));

        let callback = OboeCallback {
            ring_buffer: ring_buffer.clone(),
            is_playing: is_playing.clone(),
            user_callback: user_callback.clone(),
        };

        let stream = AudioStreamBuilder::default()
            .set_performance_mode(PerformanceMode::LowLatency)
            .set_sharing_mode(SharingMode::Exclusive)
            .set_format::<f32>()
            .set_channel_count(spec.channels as i32)
            .set_sample_rate(spec.sample_rate as i32)
            .set_callback(callback)
            .open_stream()
            .map_err(|e| AudioError::InitializationError(format!("Failed to open Oboe stream: {:?}", e)))?;

        let actual_sample_rate = stream.get_sample_rate() as u32;
        let actual_buffer_size = stream.get_frames_per_burst() as usize;

        Ok(Self {
            stream: Some(stream),
            ring_buffer,
            is_playing,
            sample_rate: actual_sample_rate,
            channels: spec.channels,
            buffer_size: actual_buffer_size,
            user_callback,
        })
    }

    /// Get shared ring buffer for writing PCM data
    pub fn get_ring_buffer(&self) -> &SharedRingBuffer {
        &self.ring_buffer
    }
}

impl AudioRenderer for OboeRenderer {
    fn start(&mut self) -> Result<()> {
        if let Some(stream) = &mut self.stream {
            stream
                .start()
                .map_err(|e| AudioError::PlaybackError(format!("Failed to start stream: {:?}", e)))?;
            self.is_playing.store(true, Ordering::Relaxed);
        }
        Ok(())
    }

    fn stop(&mut self) -> Result<()> {
        self.is_playing.store(false, Ordering::Relaxed);
        if let Some(stream) = &mut self.stream {
            stream
                .stop()
                .map_err(|e| AudioError::PlaybackError(format!("Failed to stop stream: {:?}", e)))?;
        }
        Ok(())
    }

    fn pause(&mut self) -> Result<()> {
        self.is_playing.store(false, Ordering::Relaxed);
        if let Some(stream) = &mut self.stream {
            stream
                .pause()
                .map_err(|e| AudioError::PlaybackError(format!("Failed to pause stream: {:?}", e)))?;
        }
        Ok(())
    }

    fn resume(&mut self) -> Result<()> {
        self.is_playing.store(true, Ordering::Relaxed);
        if let Some(stream) = &mut self.stream {
            stream
                .start()
                .map_err(|e| AudioError::PlaybackError(format!("Failed to resume stream: {:?}", e)))?;
        }
        Ok(())
    }

    fn set_audio_callback(&mut self, callback: podium_renderer::AudioCallback) -> Result<()> {
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
