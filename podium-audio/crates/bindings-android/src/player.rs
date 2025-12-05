// Integrated audio player implementation
// Connects all modular components into a working audio pipeline

use podium_core::{AudioError, PlayerState, Result};
use podium_renderer_api::{AudioRenderer, AudioSpec};
use podium_ringbuffer::AudioRingBuffer;
use podium_source_buffer::NetworkSource;
use podium_demux_symphonia::Demuxer;
use podium_decode_symphonia::AudioDecoder;
use podium_resampler::Resampler;

use parking_lot::Mutex;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;

// Platform-specific renderer imports
#[cfg(feature = "android")]
use podium_renderer_android::OboeRenderer;

#[cfg(feature = "desktop")]
use podium_renderer_ios::CpalRenderer;

/// Size of the ring buffer in samples (1 second at 48kHz stereo)
const RING_BUFFER_SIZE: usize = 48000 * 2;

/// Minimum samples needed before starting playback
const PREBUFFER_SIZE: usize = 48000 / 2; // 0.5 seconds

pub struct PodiumPlayer {
    state: Arc<Mutex<PlayerState>>,
    renderer: Option<Box<dyn AudioRenderer>>,
    ring_buffer: Arc<Mutex<AudioRingBuffer>>,
    volume: f32,
    position_ms: Arc<Mutex<u64>>,
    duration_ms: Arc<Mutex<u64>>,

    // Decode thread control
    decode_thread: Option<thread::JoinHandle<()>>,
    should_stop: Arc<AtomicBool>,
}

impl PodiumPlayer {
    pub fn new() -> Result<Self> {
        log::info!("Creating PodiumPlayer");

        Ok(Self {
            state: Arc::new(Mutex::new(PlayerState::Idle)),
            renderer: None,
            ring_buffer: Arc::new(Mutex::new(AudioRingBuffer::new(RING_BUFFER_SIZE))),
            volume: 1.0,
            position_ms: Arc::new(Mutex::new(0)),
            duration_ms: Arc::new(Mutex::new(0)),
            decode_thread: None,
            should_stop: Arc::new(AtomicBool::new(false)),
        })
    }

    // ========================================================================
    // Loading Methods
    // ========================================================================

    pub fn load_file(&mut self, path: &str) -> Result<()> {
        log::info!("Loading file: {} (not implemented)", path);
        // TODO: Implement file loading using local file source
        Err(AudioError::Other("File loading not yet implemented".to_string()))
    }

    pub fn load_buffer(&mut self, data: &[u8]) -> Result<()> {
        log::info!("Loading buffer: {} bytes (not implemented)", data.len());
        // TODO: Implement buffer loading
        Err(AudioError::Other("Buffer loading not yet implemented".to_string()))
    }

    pub fn load_url(&mut self, url: &str) -> Result<()> {
        log::info!("Loading URL: {}", url);

        // Stop any existing playback
        self.stop_internal()?;

        // Reset state
        self.set_state(PlayerState::Loading);
        *self.position_ms.lock() = 0;
        *self.duration_ms.lock() = 0;
        self.should_stop.store(false, Ordering::SeqCst);

        // Clear ring buffer
        *self.ring_buffer.lock() = AudioRingBuffer::new(RING_BUFFER_SIZE);

        // Create renderer with audio spec
        let spec = AudioSpec {
            sample_rate: 48000,
            channels: 2,
            buffer_size: 1024,
        };

        let mut renderer = self.create_renderer(spec)?;

        // Set up audio callback to read from ring buffer
        let ring_buffer = Arc::clone(&self.ring_buffer);
        renderer.set_audio_callback(Box::new(move |output: &mut [f32]| {
            let mut buffer = ring_buffer.lock();
            let read = buffer.read(output);

            // Zero-fill any remaining space
            if read < output.len() {
                for sample in &mut output[read..] {
                    *sample = 0.0;
                }
            }

            read
        }))?;

        self.renderer = Some(renderer);

        // Start decode thread
        let url = url.to_string();
        let ring_buffer = Arc::clone(&self.ring_buffer);
        let state = Arc::clone(&self.state);
        let should_stop = Arc::clone(&self.should_stop);
        let duration_ms = Arc::clone(&self.duration_ms);

        let decode_thread = thread::spawn(move || {
            if let Err(e) = Self::decode_loop(url, ring_buffer, state, should_stop, duration_ms) {
                log::error!("Decode thread error: {:?}", e);
            }
        });

        self.decode_thread = Some(decode_thread);

        // Wait for prebuffer
        log::info!("Waiting for prebuffer...");
        let mut attempts = 0;
        while attempts < 100 {  // 10 seconds timeout
            {
                let buffer = self.ring_buffer.lock();
                if buffer.available_read() >= PREBUFFER_SIZE {
                    log::info!("Prebuffer complete: {} samples", buffer.available_read());
                    break;
                }
            }
            thread::sleep(std::time::Duration::from_millis(100));
            attempts += 1;
        }

        self.set_state(PlayerState::Ready);
        log::info!("URL loaded and ready to play");
        Ok(())
    }

    // ========================================================================
    // Decode Loop (runs in background thread)
    // ========================================================================

    fn decode_loop(
        url: String,
        ring_buffer: Arc<Mutex<AudioRingBuffer>>,
        state: Arc<Mutex<PlayerState>>,
        should_stop: Arc<AtomicBool>,
        duration_ms: Arc<Mutex<u64>>,
    ) -> Result<()> {
        log::info!("Decode thread started for URL: {}", url);

        // Create network source
        let source = NetworkSource::from_http_range(url.clone())?;

        // Create hint from URL
        let hint = Demuxer::create_hint_from_path(&url);

        // Create demuxer
        let mut demuxer = Demuxer::from_media_source(Box::new(source), hint)?;

        // Get audio info
        let track_info = demuxer.get_track_info()?;
        log::info!("Track info: sample_rate={}, channels={}, duration={}ms",
            track_info.sample_rate, track_info.channels, track_info.duration_ms);

        // Set duration
        *duration_ms.lock() = track_info.duration_ms;

        // Create decoder
        let mut decoder = AudioDecoder::from_demuxer(&demuxer)?;

        // Create resampler if needed
        let resampler = if track_info.sample_rate != 48000 || track_info.channels != 2 {
            log::info!("Creating resampler: {}Hz {}ch -> 48000Hz 2ch",
                track_info.sample_rate, track_info.channels);
            Some(Resampler::new(
                track_info.sample_rate,
                48000,
                track_info.channels,
                2,
            ))
        } else {
            None
        };

        // Decode loop
        loop {
            if should_stop.load(Ordering::SeqCst) {
                log::info!("Decode thread stopping (requested)");
                break;
            }

            // Read next packet
            let packet = match demuxer.next_packet() {
                Ok(packet) => packet,
                Err(e) => {
                    log::info!("End of stream or error: {:?}", e);
                    break;
                }
            };

            // Decode packet
            let decoded_audio = match decoder.decode(&packet) {
                Ok(audio) => audio,
                Err(e) => {
                    log::warn!("Failed to decode packet: {:?}", e);
                    continue;
                }
            };

            // Resample if needed
            let samples = if let Some(ref resampler) = resampler {
                resampler.process(&decoded_audio)
            } else {
                decoded_audio
            };

            // Write to ring buffer (with backpressure)
            loop {
                {
                    let mut buffer = ring_buffer.lock();
                    let written = buffer.write(&samples);
                    if written == samples.len() {
                        break;
                    }
                }

                // Buffer full, wait a bit
                if should_stop.load(Ordering::SeqCst) {
                    log::info!("Decode thread stopping (buffer write interrupted)");
                    return Ok(());
                }
                thread::sleep(std::time::Duration::from_millis(10));
            }
        }

        log::info!("Decode thread finished");
        Ok(())
    }

    // ========================================================================
    // Playback Control
    // ========================================================================

    pub fn play(&mut self) -> Result<()> {
        log::info!("Play command");

        let current_state = *self.state.lock();

        match current_state {
            PlayerState::Ready | PlayerState::Paused | PlayerState::Stopped => {
                if let Some(ref mut renderer) = self.renderer {
                    renderer.start()?;
                    self.set_state(PlayerState::Playing);
                    log::info!("Playback started");
                    Ok(())
                } else {
                    Err(AudioError::InvalidState("No audio loaded".to_string()))
                }
            }
            PlayerState::Playing => {
                log::debug!("Already playing");
                Ok(())
            }
            _ => Err(AudioError::InvalidState(format!("Cannot play from state: {:?}", current_state)))
        }
    }

    pub fn pause(&mut self) -> Result<()> {
        log::info!("Pause command");

        if let Some(ref mut renderer) = self.renderer {
            renderer.pause()?;
            self.set_state(PlayerState::Paused);
            log::info!("Playback paused");
            Ok(())
        } else {
            Err(AudioError::InvalidState("No audio loaded".to_string()))
        }
    }

    pub fn stop(&mut self) -> Result<()> {
        log::info!("Stop command");
        self.stop_internal()
    }

    fn stop_internal(&mut self) -> Result<()> {
        // Stop decode thread
        self.should_stop.store(true, Ordering::SeqCst);

        if let Some(thread) = self.decode_thread.take() {
            log::debug!("Waiting for decode thread to finish...");
            let _ = thread.join();
        }

        // Stop renderer
        if let Some(ref mut renderer) = self.renderer {
            renderer.stop()?;
        }

        *self.position_ms.lock() = 0;
        self.set_state(PlayerState::Stopped);
        log::info!("Playback stopped");
        Ok(())
    }

    pub fn seek(&mut self, position_ms: u64) -> Result<()> {
        log::info!("Seek to {} ms (not implemented)", position_ms);
        // TODO: Implement seeking using demuxer.seek()
        Err(AudioError::Other("Seek not yet implemented".to_string()))
    }

    pub fn set_volume(&mut self, volume: f32) -> Result<()> {
        if volume < 0.0 || volume > 1.0 {
            return Err(AudioError::Other("Volume must be between 0.0 and 1.0".to_string()));
        }

        self.volume = volume;
        log::info!("Set volume to {}", volume);
        // TODO: Apply volume to renderer or ring buffer
        Ok(())
    }

    // ========================================================================
    // State Queries
    // ========================================================================

    pub fn get_state(&self) -> PlayerState {
        *self.state.lock()
    }

    pub fn get_position(&self) -> u64 {
        *self.position_ms.lock()
    }

    pub fn get_duration(&self) -> u64 {
        *self.duration_ms.lock()
    }

    pub fn release(&mut self) -> Result<()> {
        log::info!("Releasing player resources");

        let _ = self.stop_internal();
        self.renderer = None;
        self.set_state(PlayerState::Idle);
        Ok(())
    }

    // ========================================================================
    // Internal Methods
    // ========================================================================

    fn set_state(&self, state: PlayerState) {
        let old_state = *self.state.lock();
        *self.state.lock() = state;
        log::debug!("State transition: {:?} -> {:?}", old_state, state);
    }

    fn create_renderer(&self, spec: AudioSpec) -> Result<Box<dyn AudioRenderer>> {
        #[cfg(feature = "android")]
        {
            log::info!("Creating Oboe renderer for Android");
            Ok(Box::new(OboeRenderer::new(spec)?))
        }

        #[cfg(feature = "desktop")]
        {
            log::info!("Creating cpal renderer for Desktop");
            Ok(Box::new(CpalRenderer::new(spec)?))
        }
    }
}

impl Drop for PodiumPlayer {
    fn drop(&mut self) {
        log::debug!("PodiumPlayer dropping");
        let _ = self.release();
    }
}
