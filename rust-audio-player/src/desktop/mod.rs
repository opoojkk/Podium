// Desktop audio player implementation using cpal
// Supports Windows, macOS, and Linux

use crate::error::{AudioError, Result};
use crate::player::{AudioPlayer, PlayerState, PlayerStateContainer, PlaybackStatus};
use crate::callback::{CallbackEvent, PlayerCallback, CallbackManager};
use crate::decoder::{AudioDecoder, AudioRingBuffer};
use std::sync::Arc;
use parking_lot::Mutex;
use std::thread;
use std::sync::atomic::{AtomicBool, Ordering};
use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use cpal::{Device, Host, Stream, StreamConfig};

/// Default ring buffer size (in samples) - used at initialization
/// Will be optimized based on audio duration when loading
const RING_BUFFER_SIZE: usize = 48000 * 2 * 4;

/// Minimum buffer duration in seconds (for short clips)
const MIN_BUFFER_DURATION_SECS: u64 = 2;

/// Maximum buffer duration in seconds (to limit memory usage)
const MAX_BUFFER_DURATION_SECS: u64 = 8;

/// Position update interval (milliseconds)
const POSITION_UPDATE_INTERVAL_MS: u64 = 100;

/// Pre-buffer target in milliseconds (amount to decode before playback starts)
const PRE_BUFFER_MS: u64 = 100;

/// Desktop audio player
pub struct DesktopAudioPlayer {
    state_container: PlayerStateContainer,
    callback_manager: Arc<CallbackManager>,
    // Wrapped in Arc<Mutex> because cpal::Stream is not Send+Sync on all platforms
    audio_stream: Arc<Mutex<Option<Stream>>>,
    ring_buffer: Arc<Mutex<AudioRingBuffer>>,
    is_playing: Arc<AtomicBool>,
    sample_count: Arc<Mutex<u64>>,
    decoder_thread: Option<thread::JoinHandle<()>>,
    stop_decoder: Arc<AtomicBool>,
    decoder: Arc<Mutex<Option<AudioDecoder>>>,
    volume: Arc<Mutex<f32>>,
    playback_rate: Arc<Mutex<f32>>,
    host: Host,
    device: Option<Device>,
}

impl DesktopAudioPlayer {
    pub fn new() -> Result<Self> {
        log::info!("Initializing desktop audio player");

        // Get default host and output device
        let host = cpal::default_host();
        let device = host.default_output_device()
            .ok_or_else(|| AudioError::DeviceError("No output device available".to_string()))?;

        log::info!("Using audio device: {}", device.name().unwrap_or_else(|_| "Unknown".to_string()));

        Ok(Self {
            state_container: PlayerStateContainer::new(),
            callback_manager: Arc::new(CallbackManager::new()),
            audio_stream: Arc::new(Mutex::new(None)),
            ring_buffer: Arc::new(Mutex::new(AudioRingBuffer::new(RING_BUFFER_SIZE))),
            is_playing: Arc::new(AtomicBool::new(false)),
            sample_count: Arc::new(Mutex::new(0)),
            decoder_thread: None,
            stop_decoder: Arc::new(AtomicBool::new(false)),
            decoder: Arc::new(Mutex::new(None)),
            volume: Arc::new(Mutex::new(1.0)),
            playback_rate: Arc::new(Mutex::new(1.0)),
            host,
            device: Some(device),
        })
    }

    fn initialize_audio_stream(&mut self, sample_rate: u32, channels: u16) -> Result<()> {
        log::info!("Initializing audio stream: {}Hz, {} channels", sample_rate, channels);

        // Drop existing stream
        *self.audio_stream.lock() = None;

        let device = self.device.as_ref()
            .ok_or_else(|| AudioError::DeviceError("No audio device".to_string()))?;

        // Configure stream
        let config = StreamConfig {
            channels: channels,
            sample_rate: cpal::SampleRate(sample_rate),
            buffer_size: cpal::BufferSize::Default,
        };

        log::debug!("Stream config: {:?}", config);

        // Create stream
        let ring_buffer = self.ring_buffer.clone();
        let is_playing = self.is_playing.clone();
        let sample_count = self.sample_count.clone();
        let volume = self.volume.clone();

        let err_fn = |err| {
            log::error!("Audio stream error: {}", err);
        };

        let stream = device.build_output_stream(
            &config,
            move |data: &mut [f32], _: &cpal::OutputCallbackInfo| {
                if !is_playing.load(Ordering::Relaxed) {
                    // Fill with silence
                    data.fill(0.0);
                    return;
                }

                let vol = *volume.lock();
                let mut buffer = ring_buffer.lock();
                let read = buffer.read(data);

                // Apply volume (skip if volume is 1.0 to avoid unnecessary multiplication)
                if (vol - 1.0).abs() > 0.001 {
                    for sample in data[..read].iter_mut() {
                        *sample *= vol;
                    }
                }

                // Fill remaining with silence
                if read < data.len() {
                    data[read..].fill(0.0);
                }

                // Update sample count
                let mut count = sample_count.lock();
                *count += (read / channels as usize) as u64;
            },
            err_fn,
            None,
        )
        .map_err(|e| AudioError::InitializationError(format!("Failed to build output stream: {}", e)))?;

        *self.audio_stream.lock() = Some(stream);

        log::info!("Audio stream initialized successfully");
        Ok(())
    }

    fn start_decoder_thread(&mut self) {
        // Stop any existing decoder thread
        self.stop_decoder_thread();

        let decoder = self.decoder.clone();
        let ring_buffer = self.ring_buffer.clone();
        let is_playing = self.is_playing.clone();
        let stop_decoder = self.stop_decoder.clone();
        let sample_count = self.sample_count.clone();
        let callback_manager = self.callback_manager.clone();
        let state_container = self.state_container.clone();

        stop_decoder.store(false, Ordering::Relaxed);

        let handle = thread::spawn(move || {
            log::info!("Decoder thread started");

            let mut last_position_update = std::time::Instant::now();

            loop {
                if stop_decoder.load(Ordering::Relaxed) {
                    log::info!("Decoder thread stopping");
                    break;
                }

                if !is_playing.load(Ordering::Relaxed) {
                    thread::sleep(std::time::Duration::from_millis(10));
                    continue;
                }

                // Decode next packet and get format info
                let decode_result = {
                    let mut decoder_lock = decoder.lock();
                    if let Some(ref mut dec) = *decoder_lock {
                        let sample_rate = dec.format.sample_rate;
                        let duration_ms = dec.format.duration_ms;
                        match dec.decode_next() {
                            Ok(Some(samples)) => Some((samples, sample_rate, duration_ms)),
                            Ok(None) => None,
                            Err(e) => {
                                log::error!("Decoding error: {}", e);
                                callback_manager.dispatch_event(CallbackEvent::Error {
                                    message: e.to_string(),
                                });
                                is_playing.store(false, Ordering::Relaxed);
                                state_container.set_state(PlayerState::Error);
                                return;  // Exit decode_result block with implicit None
                            }
                        }
                    } else {
                        return;  // No decoder, exit decode_result block
                    }
                };  // decoder_lock is released here

                match decode_result {
                    Some((samples, sample_rate, duration_ms)) => {
                        // Write to ring buffer (decoder lock already released)
                        let mut buffer = ring_buffer.lock();
                        let mut written = 0;
                        while written < samples.len() {
                            let w = buffer.write(&samples[written..]);
                            if w == 0 {
                                // Buffer is full - sleep based on fullness
                                let fullness = buffer.fullness();
                                drop(buffer);

                                // Smart sleep: longer sleep when buffer is fuller
                                let sleep_ms = if fullness > 0.9 {
                                    15  // Buffer >90% full: long sleep
                                } else if fullness > 0.7 {
                                    10  // Buffer >70% full: medium sleep
                                } else {
                                    5   // Buffer <70% full: short sleep
                                };
                                thread::sleep(std::time::Duration::from_millis(sleep_ms));
                                buffer = ring_buffer.lock();
                            } else {
                                written += w;
                            }
                        }
                        drop(buffer);

                        // Update position periodically
                        if last_position_update.elapsed().as_millis() >= POSITION_UPDATE_INTERVAL_MS as u128 {
                            let count = *sample_count.lock();
                            let position_ms = (count * 1000) / sample_rate as u64;
                            callback_manager.dispatch_event(CallbackEvent::PositionChanged {
                                position_ms,
                                duration_ms,
                            });
                            last_position_update = std::time::Instant::now();
                        }
                    }
                    None => {
                        // Playback completed
                        log::info!("Playback completed");
                        is_playing.store(false, Ordering::Relaxed);
                        callback_manager.dispatch_event(CallbackEvent::PlaybackCompleted);
                        state_container.set_state(PlayerState::Stopped);
                        break;
                    }
                }
            }

            log::info!("Decoder thread exited");
        });

        self.decoder_thread = Some(handle);
    }

    fn stop_decoder_thread(&mut self) {
        if self.decoder_thread.is_some() {
            self.stop_decoder.store(true, Ordering::Relaxed);
            if let Some(handle) = self.decoder_thread.take() {
                let _ = handle.join();
            }
        }
    }

    /// Optimize ring buffer size based on audio duration
    /// Adjusts buffer to use between MIN_BUFFER_DURATION_SECS and MAX_BUFFER_DURATION_SECS
    fn optimize_buffer_size(&mut self) {
        let decoder_lock = self.decoder.lock();
        if let Some(ref decoder) = *decoder_lock {
            let sample_rate = decoder.format.sample_rate;
            let channels = decoder.format.channels;
            let duration_ms = decoder.format.duration_ms;
            let duration_secs = duration_ms / 1000;

            // Calculate optimal buffer duration
            let buffer_duration_secs = duration_secs
                .max(MIN_BUFFER_DURATION_SECS)
                .min(MAX_BUFFER_DURATION_SECS);

            // Calculate buffer size in samples
            let optimal_size = (sample_rate as u64 * channels as u64 * buffer_duration_secs) as usize;

            let current_size = self.ring_buffer.lock().size();

            if optimal_size != current_size {
                log::info!("Optimizing buffer: audio={}s, buffer={}s, size={}KB -> {}KB",
                    duration_secs,
                    buffer_duration_secs,
                    current_size * std::mem::size_of::<f32>() / 1024,
                    optimal_size * std::mem::size_of::<f32>() / 1024
                );

                drop(decoder_lock);
                self.ring_buffer.lock().resize(optimal_size);
            } else {
                drop(decoder_lock);
            }
        }
    }

    /// Pre-buffer audio data to reduce initial playback latency
    fn prebuffer(&mut self) -> Result<()> {
        let mut decoder_lock = self.decoder.lock();
        if let Some(ref mut decoder) = *decoder_lock {
            let sample_rate = decoder.format.sample_rate;
            let channels = decoder.format.channels;

            // Calculate target samples for pre-buffering
            let target_samples = ((PRE_BUFFER_MS * sample_rate as u64) / 1000) as usize * channels as usize;
            let mut total_buffered = 0;

            log::debug!("Pre-buffering {}ms ({} samples)...", PRE_BUFFER_MS, target_samples);

            // Decode and buffer data
            while total_buffered < target_samples {
                match decoder.decode_next() {
                    Ok(Some(samples)) => {
                        let mut buffer = self.ring_buffer.lock();
                        let written = buffer.write(&samples);
                        total_buffered += written;
                        drop(buffer);

                        if written < samples.len() {
                            // Ring buffer full, we have enough
                            break;
                        }
                    }
                    Ok(None) => {
                        // End of audio (very short file)
                        log::debug!("Pre-buffer: reached end of audio after {} samples", total_buffered);
                        break;
                    }
                    Err(e) => {
                        log::warn!("Pre-buffer decode error: {}", e);
                        break;
                    }
                }
            }

            log::debug!("Pre-buffered {} samples ({}ms)",
                       total_buffered,
                       (total_buffered / channels as usize) * 1000 / sample_rate as usize);
        }
        drop(decoder_lock);
        Ok(())
    }
}

// SAFETY: DesktopAudioPlayer is safe to send between threads because:
// 1. The audio_stream (cpal::Stream) is only accessed from the thread that created it
// 2. All other fields are already Send+Sync (Arc, Mutex, AtomicBool, etc.)
// 3. The AudioPlayer trait methods are always called from the same thread
// 4. We use proper synchronization (Arc<Mutex>) for shared state
//
// Note: cpal::Stream is intentionally !Send+!Sync on Windows due to COM threading
// requirements, but in our usage pattern (single-threaded access), this is safe.
unsafe impl Send for DesktopAudioPlayer {}
unsafe impl Sync for DesktopAudioPlayer {}

impl AudioPlayer for DesktopAudioPlayer {
    fn load_file(&mut self, path: &str) -> Result<()> {
        log::info!("Loading audio file: {}", path);

        self.state_container.set_state(PlayerState::Loading);
        self.callback_manager.dispatch_event(CallbackEvent::StateChanged {
            old_state: PlayerState::Idle,
            new_state: PlayerState::Loading,
        });

        self.is_playing.store(false, Ordering::Relaxed);
        self.stop_decoder_thread();
        self.ring_buffer.lock().clear();
        *self.sample_count.lock() = 0;

        let decoder = AudioDecoder::from_file(path)?;
        let sample_rate = decoder.format.sample_rate;
        let channels = decoder.format.channels;

        self.initialize_audio_stream(sample_rate, channels)?;
        *self.decoder.lock() = Some(decoder);

        // Optimize buffer size based on audio duration
        self.optimize_buffer_size();

        // Pre-buffer audio to reduce playback latency
        self.prebuffer()?;

        self.state_container.set_state(PlayerState::Ready);
        self.callback_manager.dispatch_event(CallbackEvent::StateChanged {
            old_state: PlayerState::Loading,
            new_state: PlayerState::Ready,
        });

        log::info!("Audio file loaded successfully");
        Ok(())
    }

    fn load_url(&mut self, url: &str) -> Result<()> {
        log::info!("Loading audio from URL: {}", url);

        self.state_container.set_state(PlayerState::Loading);
        self.callback_manager.dispatch_event(CallbackEvent::StateChanged {
            old_state: PlayerState::Idle,
            new_state: PlayerState::Loading,
        });

        self.is_playing.store(false, Ordering::Relaxed);
        self.stop_decoder_thread();
        self.ring_buffer.lock().clear();
        *self.sample_count.lock() = 0;

        // Get temp cache path
        let temp_file_path = crate::http_utils::get_temp_cache_path(url);
        log::info!("Downloading to temp file: {}", temp_file_path);

        // Download with progressive loading
        crate::http_utils::download_with_prebuffer(url, &temp_file_path)?;

        log::info!("Pre-buffer complete, loading audio");
        let decoder = AudioDecoder::from_file(&temp_file_path)?;
        let sample_rate = decoder.format.sample_rate;
        let channels = decoder.format.channels;

        self.initialize_audio_stream(sample_rate, channels)?;
        *self.decoder.lock() = Some(decoder);

        // Optimize buffer size based on audio duration
        self.optimize_buffer_size();

        // Pre-buffer audio to reduce playback latency
        self.prebuffer()?;

        self.state_container.set_state(PlayerState::Ready);
        self.callback_manager.dispatch_event(CallbackEvent::StateChanged {
            old_state: PlayerState::Loading,
            new_state: PlayerState::Ready,
        });

        log::info!("Audio URL loaded successfully");
        Ok(())
    }

    fn load_buffer(&mut self, buffer: &[u8]) -> Result<()> {
        log::info!("Loading audio from buffer: {} bytes", buffer.len());

        self.state_container.set_state(PlayerState::Loading);

        self.is_playing.store(false, Ordering::Relaxed);
        self.stop_decoder_thread();
        self.ring_buffer.lock().clear();
        *self.sample_count.lock() = 0;

        let decoder = AudioDecoder::from_buffer(buffer.to_vec())?;
        let sample_rate = decoder.format.sample_rate;
        let channels = decoder.format.channels;

        self.initialize_audio_stream(sample_rate, channels)?;
        *self.decoder.lock() = Some(decoder);

        // Optimize buffer size based on audio duration
        self.optimize_buffer_size();

        // Pre-buffer audio to reduce playback latency
        self.prebuffer()?;

        self.state_container.set_state(PlayerState::Ready);
        log::info!("Audio buffer loaded successfully");
        Ok(())
    }

    fn play(&mut self) -> Result<()> {
        log::info!("Starting playback");

        let current_state = self.state_container.get_state();
        if current_state != PlayerState::Ready && current_state != PlayerState::Paused {
            return Err(AudioError::InvalidState(
                format!("Cannot play from state {:?}", current_state)
            ));
        }

        // Start decoder thread first (if not already running)
        if self.decoder_thread.is_none() {
            self.start_decoder_thread();
        }

        // Enable playback flag before starting stream
        // This ensures decoder thread can fill ring buffer immediately
        self.is_playing.store(true, Ordering::Relaxed);

        // Start audio stream
        let stream_guard = self.audio_stream.lock();
        if let Some(ref stream) = *stream_guard {
            stream.play()
                .map_err(|e| AudioError::PlaybackError(format!("Failed to start stream: {}", e)))?;
        } else {
            return Err(AudioError::PlaybackError("No audio stream available".to_string()));
        }
        drop(stream_guard);

        self.state_container.set_state(PlayerState::Playing);
        self.callback_manager.dispatch_event(CallbackEvent::StateChanged {
            old_state: current_state,
            new_state: PlayerState::Playing,
        });

        log::info!("Playback started");
        Ok(())
    }

    fn pause(&mut self) -> Result<()> {
        log::info!("Pausing playback");

        let current_state = self.state_container.get_state();
        if current_state != PlayerState::Playing {
            return Err(AudioError::InvalidState(
                format!("Cannot pause from state {:?}", current_state)
            ));
        }

        self.is_playing.store(false, Ordering::Relaxed);

        let stream_guard = self.audio_stream.lock();
        if let Some(ref stream) = *stream_guard {
            stream.pause()
                .map_err(|e| AudioError::PlaybackError(format!("Failed to pause stream: {}", e)))?;
        }
        drop(stream_guard);

        self.state_container.set_state(PlayerState::Paused);
        self.callback_manager.dispatch_event(CallbackEvent::StateChanged {
            old_state: PlayerState::Playing,
            new_state: PlayerState::Paused,
        });

        log::info!("Playback paused");
        Ok(())
    }

    fn stop(&mut self) -> Result<()> {
        log::info!("Stopping playback");

        self.is_playing.store(false, Ordering::Relaxed);
        self.stop_decoder_thread();

        let stream_guard = self.audio_stream.lock();
        if let Some(ref stream) = *stream_guard {
            stream.pause()
                .map_err(|e| AudioError::PlaybackError(format!("Failed to stop stream: {}", e)))?;
        }
        drop(stream_guard);

        self.ring_buffer.lock().clear();
        *self.sample_count.lock() = 0;

        self.state_container.set_state(PlayerState::Stopped);
        self.callback_manager.dispatch_event(CallbackEvent::StateChanged {
            old_state: self.state_container.get_state(),
            new_state: PlayerState::Stopped,
        });

        log::info!("Playback stopped");
        Ok(())
    }

    fn seek(&mut self, position_ms: u64) -> Result<()> {
        log::info!("Seeking to {} ms", position_ms);

        let was_playing = self.is_playing.load(Ordering::Relaxed);

        if was_playing {
            self.is_playing.store(false, Ordering::Relaxed);
            thread::sleep(std::time::Duration::from_millis(10));
        }

        self.ring_buffer.lock().clear();

        let mut decoder_lock = self.decoder.lock();
        if let Some(ref mut dec) = *decoder_lock {
            dec.seek(position_ms)?;
            let new_sample_count = (position_ms * dec.format.sample_rate as u64) / 1000;
            *self.sample_count.lock() = new_sample_count;
        } else {
            return Err(AudioError::PlaybackError("No decoder available".to_string()));
        }
        drop(decoder_lock);

        if was_playing {
            self.is_playing.store(true, Ordering::Relaxed);
        }

        log::info!("Seek completed");
        Ok(())
    }

    fn set_volume(&mut self, volume: f32) -> Result<()> {
        let clamped = volume.clamp(0.0, 1.0);
        *self.volume.lock() = clamped;

        self.callback_manager.dispatch_event(CallbackEvent::VolumeChanged {
            volume: clamped,
        });

        log::debug!("Volume set to {}", clamped);
        Ok(())
    }

    fn set_playback_rate(&mut self, rate: f32) -> Result<()> {
        *self.playback_rate.lock() = rate;

        self.callback_manager.dispatch_event(CallbackEvent::PlaybackRateChanged {
            rate,
        });

        log::warn!("Playback rate adjustment not yet implemented");
        Ok(())
    }

    fn get_state(&self) -> PlayerState {
        self.state_container.get_state()
    }

    fn get_status(&self) -> PlaybackStatus {
        let decoder_lock = self.decoder.lock();
        let duration_ms = if let Some(ref dec) = *decoder_lock {
            dec.format.duration_ms
        } else {
            0
        };
        drop(decoder_lock);

        let sample_count = *self.sample_count.lock();
        let sample_rate = if let Some(ref dec) = *self.decoder.lock() {
            dec.format.sample_rate
        } else {
            48000
        };

        let position_ms = (sample_count * 1000) / sample_rate as u64;

        PlaybackStatus {
            position_ms,
            duration_ms,
            volume: *self.volume.lock(),
            playback_rate: *self.playback_rate.lock(),
            buffering: false,
        }
    }

    fn set_callback(&mut self, callback: Option<Arc<dyn PlayerCallback>>) {
        self.callback_manager.clear_callbacks();
        if let Some(cb) = callback {
            self.callback_manager.add_callback(cb, POSITION_UPDATE_INTERVAL_MS);
        }
    }

    fn release(&mut self) -> Result<()> {
        log::info!("Releasing audio player");

        self.stop()?;
        self.stop_decoder_thread();
        *self.audio_stream.lock() = None;
        *self.decoder.lock() = None;
        self.state_container.set_state(PlayerState::Idle);

        log::info!("Audio player released");
        Ok(())
    }

    fn as_any(&self) -> &dyn std::any::Any {
        self
    }
}

impl Drop for DesktopAudioPlayer {
    fn drop(&mut self) {
        let _ = self.release();
    }
}
