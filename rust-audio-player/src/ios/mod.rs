// iOS audio player implementation using cpal
// cpal 0.15+ supports iOS via CoreAudio backend

use crate::callback::{CallbackEvent, CallbackManager, PlayerCallback};
use crate::decoder::{AudioDecoder, AudioRingBuffer};
use crate::error::{AudioError, Result};
use crate::output_rate::effective_output_rate;
use crate::player::{AudioPlayer, PlaybackStatus, PlayerState, PlayerStateContainer};
use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use cpal::{Device, Host, SampleRate, Stream, StreamConfig};
use parking_lot::Mutex;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;

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

/// iOS audio player using cpal
pub struct IOSAudioPlayer {
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
    /// Actual output sample rate selected for the audio device. This may differ from the decoder's sample rate
    /// if the device does not support it, in which case we resample to this rate to avoid speed/pitch issues.
    output_sample_rate: Arc<Mutex<u32>>,
    host: Host,
    device: Option<Device>,
}

impl IOSAudioPlayer {
    pub fn new() -> Result<Self> {
        log::info!("Initializing iOS audio player with cpal");

        // Get default host and output device
        let host = cpal::default_host();
        let device = host
            .default_output_device()
            .ok_or_else(|| AudioError::DeviceError("No output device available".to_string()))?;

        log::info!(
            "Using audio device: {}",
            device.name().unwrap_or_else(|_| "Unknown".to_string())
        );

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
            output_sample_rate: Arc::new(Mutex::new(0)),
            host,
            device: Some(device),
        })
    }

    fn initialize_audio_stream(&mut self, sample_rate: u32, channels: u16) -> Result<()> {
        log::info!(
            "Initializing audio stream: {}Hz, {} channels",
            sample_rate,
            channels
        );

        // Drop existing stream
        *self.audio_stream.lock() = None;

        let device = self
            .device
            .as_ref()
            .ok_or_else(|| AudioError::DeviceError("No audio device".to_string()))?;

        // Configure stream with a sample rate supported by the device (clamp if necessary)
        let config = self.pick_stream_config(device, sample_rate, channels);

        log::debug!("Stream config: {:?}", config);

        // Create stream
        let ring_buffer = self.ring_buffer.clone();
        let is_playing = self.is_playing.clone();
        let sample_count = self.sample_count.clone();
        let volume = self.volume.clone();
        let output_sample_rate = self.output_sample_rate.clone();

        let err_fn = |err| {
            log::error!("Audio stream error: {}", err);
        };

        let stream = device
            .build_output_stream(
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
                    *count += (read as u64 / channels as u64);
                },
                err_fn,
                None,
            )
            .map_err(|e| {
                AudioError::InitializationError(format!("Failed to build output stream: {}", e))
            })?;

        *self.audio_stream.lock() = Some(stream);
        *self.output_sample_rate.lock() = config.sample_rate.0;

        log::info!("Audio stream initialized successfully");
        Ok(())
    }

    /// Pick a stream config that best matches the decoder output while being supported by the device.
    fn pick_stream_config(
        &self,
        device: &Device,
        decoder_sample_rate: u32,
        channels: u16,
    ) -> StreamConfig {
        // Default to decoder sample rate
        let default_config = StreamConfig {
            channels,
            sample_rate: SampleRate(decoder_sample_rate),
            buffer_size: cpal::BufferSize::Default,
        };

        // Try to find a supported range that matches the channel count
        match device.supported_output_configs() {
            Ok(mut configs) => {
                let mut chosen: Option<StreamConfig> = None;
                while let Some(cfg_range) = configs.next() {
                    if cfg_range.channels() != channels {
                        continue;
                    }

                    let min = cfg_range.min_sample_rate().0;
                    let max = cfg_range.max_sample_rate().0;
                    let target = decoder_sample_rate.clamp(min, max);

                    let stream_cfg = cfg_range.with_sample_rate(SampleRate(target)).config();

                    chosen = Some(stream_cfg);

                    // Prefer exact match
                    if target == decoder_sample_rate {
                        break;
                    }
                }

                if let Some(cfg) = chosen {
                    if cfg.sample_rate.0 != decoder_sample_rate {
                        log::warn!(
                            "Decoder sample rate {}Hz not supported; using closest supported {}Hz",
                            decoder_sample_rate,
                            cfg.sample_rate.0
                        );
                    }
                    cfg
                } else {
                    log::warn!(
                        "No supported config found for {} channels; using decoder sample rate {}Hz",
                        channels,
                        decoder_sample_rate
                    );
                    default_config
                }
            }
            Err(err) => {
                log::warn!(
                    "Failed to query supported output configs ({}); using decoder sample rate {}Hz",
                    err,
                    decoder_sample_rate
                );
                default_config
            }
        }
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
        let output_sample_rate = self.output_sample_rate.clone();

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
                        let channels = dec.format.channels;
                        match dec.decode_next() {
                            Ok(Some(samples)) => {
                                Some((samples, sample_rate, duration_ms, channels))
                            }
                            Ok(None) => None,
                            Err(e) => {
                                log::error!("Decoding error: {}", e);
                                callback_manager.dispatch_event(CallbackEvent::Error {
                                    message: e.to_string(),
                                });
                                is_playing.store(false, Ordering::Relaxed);
                                state_container.set_state(PlayerState::Error);
                                return; // Exit decode_result block with implicit None
                            }
                        }
                    } else {
                        return; // No decoder, exit decode_result block
                    }
                }; // decoder_lock is released here

                match decode_result {
                    Some((samples, sample_rate, duration_ms, channels)) => {
                        // Resample if device sample rate differs from decoded audio
                        let target_rate = effective_output_rate(
                            *output_sample_rate.lock(),
                            Some(sample_rate),
                            sample_rate,
                        );
                        let processed = if sample_rate != target_rate {
                            log::debug!(
                                "Resampling from {}Hz to {}Hz to match device",
                                sample_rate,
                                target_rate
                            );
                            Self::resample_linear(&samples, sample_rate, target_rate, channels)
                        } else {
                            samples
                        };

                        // Write to ring buffer (decoder lock already released)
                        let mut buffer = ring_buffer.lock();
                        let mut written = 0;
                        while written < processed.len() {
                            let w = buffer.write(&processed[written..]);
                            if w == 0 {
                                // Buffer is full - sleep based on fullness
                                let fullness = buffer.fullness();
                                drop(buffer);

                                // Smart sleep: longer sleep when buffer is fuller
                                let sleep_ms = if fullness > 0.9 {
                                    15 // Buffer >90% full: long sleep
                                } else if fullness > 0.7 {
                                    10 // Buffer >70% full: medium sleep
                                } else {
                                    5 // Buffer <70% full: short sleep
                                };
                                thread::sleep(std::time::Duration::from_millis(sleep_ms));
                                buffer = ring_buffer.lock();
                            } else {
                                written += w;
                            }
                        }
                        drop(buffer);

                        // Update position periodically
                        if last_position_update.elapsed().as_millis()
                            >= POSITION_UPDATE_INTERVAL_MS as u128
                        {
                            let count = *sample_count.lock();
                            let effective_rate = effective_output_rate(
                                *output_sample_rate.lock(),
                                Some(sample_rate),
                                sample_rate,
                            ) as u64;
                            let position_ms = if effective_rate > 0 {
                                (count * 1000) / effective_rate
                            } else {
                                0
                            };
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
            let target_rate = effective_output_rate(
                *self.output_sample_rate.lock(),
                Some(decoder.format.sample_rate),
                decoder.format.sample_rate,
            );
            let channels = decoder.format.channels;
            let duration_ms = decoder.format.duration_ms;
            let duration_secs = duration_ms / 1000;

            // Calculate optimal buffer duration
            let buffer_duration_secs = duration_secs
                .max(MIN_BUFFER_DURATION_SECS)
                .min(MAX_BUFFER_DURATION_SECS);

            // Calculate buffer size in samples
            let optimal_size =
                (target_rate as u64 * channels as u64 * buffer_duration_secs) as usize;

            let current_size = self.ring_buffer.lock().size();

            if optimal_size != current_size {
                log::info!(
                    "Optimizing buffer: audio={}s, buffer={}s, size={}KB -> {}KB",
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

    /// Simple linear resampler to convert decoded samples to the device sample rate.
    fn resample_linear(
        samples: &[f32],
        input_rate: u32,
        output_rate: u32,
        channels: u16,
    ) -> Vec<f32> {
        if input_rate == 0 || output_rate == 0 || samples.is_empty() {
            return samples.to_vec();
        }

        let channels = channels.max(1) as usize;
        let input_frames = samples.len() / channels;
        let output_frames =
            ((input_frames as u64 * output_rate as u64) / input_rate as u64) as usize;

        if input_frames == 0 || output_frames == 0 {
            return Vec::new();
        }

        let mut output = Vec::with_capacity(output_frames * channels);
        let ratio = input_rate as f64 / output_rate as f64;

        for out_index in 0..output_frames {
            let input_pos = out_index as f64 * ratio;
            let base_idx = input_pos.floor() as usize;
            let frac = input_pos - base_idx as f64;
            let next_idx = (base_idx + 1).min(input_frames - 1);

            for ch in 0..channels {
                let s0 = samples[base_idx * channels + ch];
                let s1 = samples[next_idx * channels + ch];
                let interpolated = s0 + (s1 - s0) * frac as f32;
                output.push(interpolated);
            }
        }

        output
    }

    /// Pre-buffer audio data to reduce initial playback latency
    fn prebuffer(&mut self) -> Result<()> {
        let mut decoder_lock = self.decoder.lock();
        if let Some(ref mut decoder) = *decoder_lock {
            let sample_rate = decoder.format.sample_rate;
            let channels = decoder.format.channels;
            let target_rate = effective_output_rate(
                *self.output_sample_rate.lock(),
                Some(sample_rate),
                sample_rate,
            );

            // Calculate target samples for pre-buffering at the output rate
            let target_samples =
                ((PRE_BUFFER_MS * target_rate as u64) / 1000) as usize * channels as usize;
            let mut total_buffered = 0;

            log::debug!(
                "Pre-buffering {}ms ({} samples)...",
                PRE_BUFFER_MS,
                target_samples
            );

            // Decode and buffer data
            while total_buffered < target_samples {
                match decoder.decode_next() {
                    Ok(Some(samples)) => {
                        let processed = if sample_rate != target_rate {
                            Self::resample_linear(&samples, sample_rate, target_rate, channels)
                        } else {
                            samples
                        };

                        let mut buffer = self.ring_buffer.lock();
                        let written = buffer.write(&processed);
                        total_buffered += written;
                        drop(buffer);

                        if written < processed.len() {
                            // Ring buffer full, we have enough
                            break;
                        }
                    }
                    Ok(None) => {
                        // End of audio (very short file)
                        log::debug!(
                            "Pre-buffer: reached end of audio after {} samples",
                            total_buffered
                        );
                        break;
                    }
                    Err(e) => {
                        log::warn!("Pre-buffer decode error: {}", e);
                        break;
                    }
                }
            }

            log::debug!(
                "Pre-buffered {} samples ({}ms)",
                total_buffered,
                (total_buffered / channels as usize) * 1000 / target_rate as usize
            );
        }
        drop(decoder_lock);
        Ok(())
    }
}

// SAFETY: IOSAudioPlayer is safe to send between threads because:
// 1. The audio_stream (cpal::Stream) is only accessed from the thread that created it
// 2. All other fields are already Send+Sync (Arc, Mutex, AtomicBool, etc.)
// 3. The AudioPlayer trait methods are always called from the same thread
// 4. We use proper synchronization (Arc<Mutex>) for shared state
unsafe impl Send for IOSAudioPlayer {}
unsafe impl Sync for IOSAudioPlayer {}

impl AudioPlayer for IOSAudioPlayer {
    fn load_file(&mut self, path: &str) -> Result<()> {
        log::info!("Loading audio file: {}", path);

        self.state_container.set_state(PlayerState::Loading);
        self.callback_manager
            .dispatch_event(CallbackEvent::StateChanged {
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
        self.callback_manager
            .dispatch_event(CallbackEvent::StateChanged {
                old_state: PlayerState::Loading,
                new_state: PlayerState::Ready,
            });

        log::info!("Audio file loaded successfully");
        Ok(())
    }

    fn load_url(&mut self, url: &str) -> Result<()> {
        log::info!("Loading audio from URL (streaming): {}", url);

        self.state_container.set_state(PlayerState::Loading);
        self.callback_manager
            .dispatch_event(CallbackEvent::StateChanged {
                old_state: PlayerState::Idle,
                new_state: PlayerState::Loading,
            });

        self.is_playing.store(false, Ordering::Relaxed);
        self.stop_decoder_thread();
        self.ring_buffer.lock().clear();
        *self.sample_count.lock() = 0;

        // Create hint from URL
        let hint = AudioDecoder::create_hint_from_url(url);

        // Use HTTP Range-based source for true streaming without downloading entire file
        // This supports both Fast Start and Non-Fast Start M4A files
        log::info!("Using HTTP Range source (on-demand download)");
        let source = crate::http_range_source::HttpRangeSource::new(url.to_string())?;
        let decoder = AudioDecoder::from_streaming_source(Box::new(source), hint)?;

        let sample_rate = decoder.format.sample_rate;
        let channels = decoder.format.channels;

        log::info!(
            "Streaming decoder created: {}Hz, {} channels",
            sample_rate,
            channels
        );

        self.initialize_audio_stream(sample_rate, channels)?;
        *self.decoder.lock() = Some(decoder);

        // Optimize buffer size based on audio duration
        self.optimize_buffer_size();

        // Pre-buffer audio to reduce playback latency
        self.prebuffer()?;

        self.state_container.set_state(PlayerState::Ready);
        self.callback_manager
            .dispatch_event(CallbackEvent::StateChanged {
                old_state: PlayerState::Loading,
                new_state: PlayerState::Ready,
            });

        log::info!("Audio URL loaded successfully (streaming mode)");
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
            return Err(AudioError::InvalidState(format!(
                "Cannot play from state {:?}",
                current_state
            )));
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
            stream
                .play()
                .map_err(|e| AudioError::PlaybackError(format!("Failed to start stream: {}", e)))?;
        } else {
            return Err(AudioError::PlaybackError(
                "No audio stream available".to_string(),
            ));
        }
        drop(stream_guard);

        self.state_container.set_state(PlayerState::Playing);
        self.callback_manager
            .dispatch_event(CallbackEvent::StateChanged {
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
            return Err(AudioError::InvalidState(format!(
                "Cannot pause from state {:?}",
                current_state
            )));
        }

        self.is_playing.store(false, Ordering::Relaxed);

        let stream_guard = self.audio_stream.lock();
        if let Some(ref stream) = *stream_guard {
            stream
                .pause()
                .map_err(|e| AudioError::PlaybackError(format!("Failed to pause stream: {}", e)))?;
        }
        drop(stream_guard);

        self.state_container.set_state(PlayerState::Paused);
        self.callback_manager
            .dispatch_event(CallbackEvent::StateChanged {
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
            stream
                .pause()
                .map_err(|e| AudioError::PlaybackError(format!("Failed to stop stream: {}", e)))?;
        }
        drop(stream_guard);

        self.ring_buffer.lock().clear();
        *self.sample_count.lock() = 0;

        self.state_container.set_state(PlayerState::Stopped);
        self.callback_manager
            .dispatch_event(CallbackEvent::StateChanged {
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
            let effective_rate = effective_output_rate(
                *self.output_sample_rate.lock(),
                Some(dec.format.sample_rate),
                dec.format.sample_rate,
            ) as u64;
            let new_sample_count = (position_ms * effective_rate) / 1000;
            *self.sample_count.lock() = new_sample_count;
        } else {
            return Err(AudioError::PlaybackError(
                "No decoder available".to_string(),
            ));
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

        self.callback_manager
            .dispatch_event(CallbackEvent::VolumeChanged { volume: clamped });

        log::debug!("Volume set to {}", clamped);
        Ok(())
    }

    fn set_playback_rate(&mut self, rate: f32) -> Result<()> {
        *self.playback_rate.lock() = rate;

        self.callback_manager
            .dispatch_event(CallbackEvent::PlaybackRateChanged { rate });

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
        let selected_rate = *self.output_sample_rate.lock();
        let decoder_rate = self
            .decoder
            .lock()
            .as_ref()
            .map(|dec| dec.format.sample_rate);
        let sample_rate = effective_output_rate(selected_rate, decoder_rate, 48000) as u64;

        let position_ms = if sample_rate > 0 {
            (sample_count * 1000) / sample_rate
        } else {
            0
        };

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
            self.callback_manager
                .add_callback(cb, POSITION_UPDATE_INTERVAL_MS);
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

impl Drop for IOSAudioPlayer {
    fn drop(&mut self) {
        let _ = self.release();
    }
}
