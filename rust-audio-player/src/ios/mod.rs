// iOS audio player implementation using CoreAudio
// Uses Audio Queue Services for playback

use crate::error::{AudioError, Result};
use crate::player::{AudioPlayer, PlayerState, PlayerStateContainer, PlaybackStatus};
use crate::callback::{CallbackEvent, PlayerCallback, CallbackManager};
use crate::decoder::{AudioDecoder, AudioRingBuffer};
use std::sync::Arc;
use parking_lot::Mutex;
use std::thread;
use std::sync::atomic::{AtomicBool, Ordering};
use coreaudio_rs::audio_unit::{AudioUnit, SampleFormat, StreamFormat};
use coreaudio_rs::audio_unit::render_callback::{self, data};

/// Ring buffer size (in samples)
const RING_BUFFER_SIZE: usize = 48000 * 2 * 4;

/// Position update interval (milliseconds)
const POSITION_UPDATE_INTERVAL_MS: u64 = 100;

/// iOS audio player
pub struct IOSAudioPlayer {
    state_container: PlayerStateContainer,
    callback_manager: Arc<CallbackManager>,
    audio_unit: Option<AudioUnit>,
    ring_buffer: Arc<Mutex<AudioRingBuffer>>,
    is_playing: Arc<AtomicBool>,
    sample_count: Arc<Mutex<u64>>,
    decoder_thread: Option<thread::JoinHandle<()>>,
    stop_decoder: Arc<AtomicBool>,
    decoder: Arc<Mutex<Option<AudioDecoder>>>,
    volume: Arc<Mutex<f32>>,
    playback_rate: Arc<Mutex<f32>>,
}

impl IOSAudioPlayer {
    pub fn new() -> Result<Self> {
        log::info!("Initializing iOS audio player");

        Ok(Self {
            state_container: PlayerStateContainer::new(),
            callback_manager: Arc::new(CallbackManager::new()),
            audio_unit: None,
            ring_buffer: Arc::new(Mutex::new(AudioRingBuffer::new(RING_BUFFER_SIZE))),
            is_playing: Arc::new(AtomicBool::new(false)),
            sample_count: Arc::new(Mutex::new(0)),
            decoder_thread: None,
            stop_decoder: Arc::new(AtomicBool::new(false)),
            decoder: Arc::new(Mutex::new(None)),
            volume: Arc::new(Mutex::new(1.0)),
            playback_rate: Arc::new(Mutex::new(1.0)),
        })
    }

    fn initialize_audio_unit(&mut self, sample_rate: u32, channels: u16) -> Result<()> {
        log::info!("Initializing audio unit: {}Hz, {} channels", sample_rate, channels);

        // Close existing audio unit if any
        if let Some(mut unit) = self.audio_unit.take() {
            let _ = unit.stop();
            let _ = unit.uninitialize();
        }

        // Create audio unit
        let mut audio_unit = AudioUnit::new(coreaudio_rs::audio_unit::IOType::RemoteIO)
            .map_err(|e| AudioError::InitializationError(format!("Failed to create audio unit: {:?}", e)))?;

        // Set stream format
        let stream_format = StreamFormat {
            sample_rate: sample_rate as f64,
            sample_format: SampleFormat::F32,
            flags: coreaudio_rs::audio_unit::LinearPCMFlags::IS_FLOAT | coreaudio_rs::audio_unit::LinearPCMFlags::IS_PACKED,
            channels: channels as u32,
        };

        audio_unit
            .set_property(
                coreaudio_rs::audio_unit::PropertyId::StreamFormat,
                coreaudio_rs::audio_unit::Scope::Input,
                coreaudio_rs::audio_unit::Element::Output,
                Some(&stream_format),
            )
            .map_err(|e| AudioError::InitializationError(format!("Failed to set stream format: {:?}", e)))?;

        // Set render callback
        let ring_buffer = self.ring_buffer.clone();
        let is_playing = self.is_playing.clone();
        let sample_count = self.sample_count.clone();
        let volume = self.volume.clone();

        type Args = render_callback::Args<data::NonInterleaved<f32>>;

        audio_unit.set_render_callback(move |args: Args| -> std::result::Result<(), ()> {
            if !is_playing.load(Ordering::Relaxed) {
                // Fill with silence
                for channel in args.data.channels_mut() {
                    channel.fill(0.0);
                }
                return Ok(());
            }

            let vol = *volume.lock();
            let frames = args.num_frames;

            // Read from ring buffer (interleaved)
            let mut interleaved = vec![0.0f32; frames * args.data.channels().len()];
            let mut buffer = ring_buffer.lock();
            let read = buffer.read(&mut interleaved);
            drop(buffer);

            // Convert to non-interleaved and apply volume
            let num_channels = args.data.channels().len();
            for (ch_idx, channel) in args.data.channels_mut().iter_mut().enumerate() {
                for frame_idx in 0..frames {
                    let sample_idx = frame_idx * num_channels + ch_idx;
                    if sample_idx < read {
                        channel[frame_idx] = interleaved[sample_idx] * vol;
                    } else {
                        channel[frame_idx] = 0.0;
                    }
                }
            }

            // Update sample count
            let mut count = sample_count.lock();
            *count += (read / num_channels) as u64;

            Ok(())
        })
        .map_err(|e| AudioError::InitializationError(format!("Failed to set render callback: {:?}", e)))?;

        // Initialize audio unit
        audio_unit
            .initialize()
            .map_err(|e| AudioError::InitializationError(format!("Failed to initialize audio unit: {:?}", e)))?;

        self.audio_unit = Some(audio_unit);

        log::info!("Audio unit initialized successfully");
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
        let volume = self.volume.clone();
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

                let mut decoder_lock = decoder.lock();
                if let Some(ref mut dec) = *decoder_lock {
                    match dec.decode_next() {
                        Ok(Some(samples)) => {
                            // Write to ring buffer
                            let mut buffer = ring_buffer.lock();
                            let mut written = 0;
                            while written < samples.len() {
                                let w = buffer.write(&samples[written..]);
                                if w == 0 {
                                    drop(buffer);
                                    thread::sleep(std::time::Duration::from_millis(5));
                                    buffer = ring_buffer.lock();
                                } else {
                                    written += w;
                                }
                            }
                            drop(buffer);

                            // Update position periodically
                            if last_position_update.elapsed().as_millis() >= POSITION_UPDATE_INTERVAL_MS as u128 {
                                let count = *sample_count.lock();
                                let position_ms = (count * 1000) / dec.format.sample_rate as u64;
                                callback_manager.dispatch_event(CallbackEvent::PositionChanged {
                                    position_ms,
                                    duration_ms: dec.format.duration_ms,
                                });
                                last_position_update = std::time::Instant::now();
                            }
                        }
                        Ok(None) => {
                            log::info!("Playback completed");
                            is_playing.store(false, Ordering::Relaxed);
                            callback_manager.dispatch_event(CallbackEvent::PlaybackCompleted);
                            state_container.set_state(PlayerState::Stopped);
                            break;
                        }
                        Err(e) => {
                            log::error!("Decoding error: {}", e);
                            callback_manager.dispatch_event(CallbackEvent::Error {
                                message: e.to_string(),
                            });
                            is_playing.store(false, Ordering::Relaxed);
                            state_container.set_state(PlayerState::Error);
                            break;
                        }
                    }
                } else {
                    break;
                }

                drop(decoder_lock);
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
}

impl AudioPlayer for IOSAudioPlayer {
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

        self.initialize_audio_unit(sample_rate, channels)?;
        *self.decoder.lock() = Some(decoder);

        self.state_container.set_state(PlayerState::Ready);
        self.callback_manager.dispatch_event(CallbackEvent::StateChanged {
            old_state: PlayerState::Loading,
            new_state: PlayerState::Ready,
        });

        log::info!("Audio file loaded successfully");
        Ok(())
    }

    fn load_url(&mut self, _url: &str) -> Result<()> {
        Err(AudioError::Other("URL loading not yet implemented".to_string()))
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

        self.initialize_audio_unit(sample_rate, channels)?;
        *self.decoder.lock() = Some(decoder);

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

        if let Some(ref mut unit) = self.audio_unit {
            unit.start()
                .map_err(|e| AudioError::PlaybackError(format!("Failed to start audio unit: {:?}", e)))?;
        } else {
            return Err(AudioError::PlaybackError("No audio unit available".to_string()));
        }

        if self.decoder_thread.is_none() {
            self.start_decoder_thread();
        }

        self.is_playing.store(true, Ordering::Relaxed);
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

        if let Some(ref mut unit) = self.audio_unit {
            unit.stop()
                .map_err(|e| AudioError::PlaybackError(format!("Failed to stop audio unit: {:?}", e)))?;
        }

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

        if let Some(ref mut unit) = self.audio_unit {
            unit.stop()
                .map_err(|e| AudioError::PlaybackError(format!("Failed to stop audio unit: {:?}", e)))?;
        }

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

        if let Some(mut unit) = self.audio_unit.take() {
            let _ = unit.uninitialize();
        }

        *self.decoder.lock() = None;
        self.state_container.set_state(PlayerState::Idle);

        log::info!("Audio player released");
        Ok(())
    }
}

impl Drop for IOSAudioPlayer {
    fn drop(&mut self) {
        let _ = self.release();
    }
}
