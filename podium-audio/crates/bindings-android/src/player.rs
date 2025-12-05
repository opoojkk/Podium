// Integrated player that uses all modular components
// Acts as a facade over the modular architecture

use std::sync::Arc;
use parking_lot::Mutex;
use std::io::{Cursor, Read};

use podium_core::{AudioError, PlayerState};
use podium_transport_http::{HttpRangeSource, download_file_progressive};
use podium_source_buffer::SourceBuffer;
use podium_demux_symphonia::SymphoniaDemuxer;
use podium_decode_symphonia::SymphoniaDecoder;
use podium_resampler::Resampler;
use podium_ringbuffer::RingBuffer;
use podium_renderer_api::{AudioRenderer, AudioSpec};

// Platform-specific renderer imports
#[cfg(target_os = "android")]
use podium_renderer_android::OboeRenderer;

#[cfg(not(target_os = "android"))]
use podium_renderer_ios::CpalRenderer;

pub struct PodiumPlayer {
    state: PlayerState,
    renderer: Option<Box<dyn AudioRenderer>>,
    ring_buffer: Option<Arc<RingBuffer>>,
    decoder: Option<Arc<Mutex<DecoderPipeline>>>,

    // Playback state
    position_ms: u64,
    duration_ms: u64,
    volume: f32,
}

struct DecoderPipeline {
    demuxer: SymphoniaDemuxer,
    decoder: SymphoniaDecoder,
    resampler: Resampler,
    source_buffer: Option<SourceBuffer>,

    // Audio format info
    sample_rate: u32,
    channels: usize,
}

impl PodiumPlayer {
    pub fn new() -> Result<Self, AudioError> {
        log::info!("Creating PodiumPlayer with modular architecture");

        Ok(Self {
            state: PlayerState::Idle,
            renderer: None,
            ring_buffer: None,
            decoder: None,
            position_ms: 0,
            duration_ms: 0,
            volume: 1.0,
        })
    }

    // ========================================================================
    // Loading Methods
    // ========================================================================

    pub fn load_file(&mut self, path: &str) -> Result<(), AudioError> {
        log::info!("Loading file: {}", path);
        self.set_state(PlayerState::Loading);

        // Read file into memory
        let data = std::fs::read(path)
            .map_err(|e| AudioError::LoadError(format!("Failed to read file: {}", e)))?;

        self.load_from_bytes(data)
    }

    pub fn load_buffer(&mut self, data: &[u8]) -> Result<(), AudioError> {
        log::info!("Loading buffer: {} bytes", data.len());
        self.set_state(PlayerState::Loading);

        self.load_from_bytes(data.to_vec())
    }

    pub fn load_url(&mut self, url: &str) -> Result<(), AudioError> {
        log::info!("Loading URL: {}", url);
        self.set_state(PlayerState::Loading);

        // Create HTTP range source for streaming
        let range_source = HttpRangeSource::new(url.to_string())
            .map_err(|e| AudioError::NetworkError(format!("Failed to create HTTP source: {}", e)))?;

        self.load_from_source(Box::new(range_source))
    }

    fn load_from_bytes(&mut self, data: Vec<u8>) -> Result<(), AudioError> {
        let cursor = Cursor::new(data);
        self.load_from_source(Box::new(cursor))
    }

    fn load_from_source(&mut self, source: Box<dyn Read + Send>) -> Result<(), AudioError> {
        // Create source buffer
        let mut source_buffer = SourceBuffer::new(source);

        // Initialize demuxer
        let mut demuxer = SymphoniaDemuxer::new(&mut source_buffer)
            .map_err(|e| AudioError::LoadError(format!("Failed to create demuxer: {:?}", e)))?;

        // Get format info
        let format_info = demuxer.get_format_info();
        let sample_rate = format_info.sample_rate;
        let channels = format_info.channels;

        log::info!("Audio format: {}Hz, {} channels", sample_rate, channels);

        // Initialize decoder
        let decoder = SymphoniaDecoder::new(&mut demuxer)
            .map_err(|e| AudioError::LoadError(format!("Failed to create decoder: {:?}", e)))?;

        // Get duration
        self.duration_ms = format_info.duration_ms;

        // Create resampler (target 48kHz for renderer)
        let target_sample_rate = 48000;
        let resampler = Resampler::new(sample_rate, target_sample_rate, channels)
            .map_err(|e| AudioError::InitializationError(format!("Failed to create resampler: {:?}", e)))?;

        // Create ring buffer (2 seconds of audio)
        let ring_buffer_size = target_sample_rate as usize * channels * 2;
        let ring_buffer = Arc::new(RingBuffer::new(ring_buffer_size));

        // Create renderer
        let spec = AudioSpec {
            sample_rate: target_sample_rate,
            channels: channels as u16,
            buffer_size: 1024,
        };

        // Use platform-specific renderer
        #[cfg(target_os = "android")]
        let mut renderer = OboeRenderer::new(spec)
            .map_err(|e| AudioError::InitializationError(format!("Failed to create renderer: {:?}", e)))?;

        #[cfg(not(target_os = "android"))]
        let mut renderer = CpalRenderer::new(spec)
            .map_err(|e| AudioError::InitializationError(format!("Failed to create renderer: {:?}", e)))?;

        // Set up audio callback to read from ring buffer
        let ring_buffer_clone = ring_buffer.clone();
        renderer.set_audio_callback(Box::new(move |buffer: &mut [f32]| -> usize {
            ring_buffer_clone.read(buffer)
        }))
        .map_err(|e| AudioError::InitializationError(format!("Failed to set audio callback: {:?}", e)))?;

        // Set initial volume
        renderer.set_volume(self.volume)
            .map_err(|e| AudioError::InitializationError(format!("Failed to set volume: {:?}", e)))?;

        // Store components
        let pipeline = DecoderPipeline {
            demuxer,
            decoder,
            resampler,
            source_buffer: Some(source_buffer),
            sample_rate: target_sample_rate,
            channels,
        };

        self.decoder = Some(Arc::new(Mutex::new(pipeline)));
        self.ring_buffer = Some(ring_buffer);
        self.renderer = Some(Box::new(renderer));

        // Start decoding thread
        self.start_decoder_thread()?;

        self.set_state(PlayerState::Ready);
        Ok(())
    }

    // ========================================================================
    // Playback Control
    // ========================================================================

    pub fn play(&mut self) -> Result<(), AudioError> {
        log::info!("Play");

        match self.state {
            PlayerState::Ready | PlayerState::Paused | PlayerState::Stopped => {
                if let Some(ref mut renderer) = self.renderer {
                    renderer.start()
                        .map_err(|e| AudioError::PlaybackError(format!("Failed to start renderer: {:?}", e)))?;

                    self.set_state(PlayerState::Playing);
                    Ok(())
                } else {
                    Err(AudioError::InvalidState("No audio loaded".to_string()))
                }
            }
            PlayerState::Playing => {
                log::debug!("Already playing");
                Ok(())
            }
            _ => Err(AudioError::InvalidState(format!("Cannot play from state: {:?}", self.state)))
        }
    }

    pub fn pause(&mut self) -> Result<(), AudioError> {
        log::info!("Pause");

        if let Some(ref mut renderer) = self.renderer {
            renderer.pause()
                .map_err(|e| AudioError::PlaybackError(format!("Failed to pause renderer: {:?}", e)))?;

            self.set_state(PlayerState::Paused);
            Ok(())
        } else {
            Err(AudioError::InvalidState("No audio loaded".to_string()))
        }
    }

    pub fn stop(&mut self) -> Result<(), AudioError> {
        log::info!("Stop");

        if let Some(ref mut renderer) = self.renderer {
            renderer.stop()
                .map_err(|e| AudioError::PlaybackError(format!("Failed to stop renderer: {:?}", e)))?;

            // Clear ring buffer
            if let Some(ref ring_buffer) = self.ring_buffer {
                ring_buffer.clear();
            }

            self.position_ms = 0;
            self.set_state(PlayerState::Stopped);
            Ok(())
        } else {
            Err(AudioError::InvalidState("No audio loaded".to_string()))
        }
    }

    pub fn seek(&mut self, position_ms: u64) -> Result<(), AudioError> {
        log::info!("Seek to {} ms", position_ms);

        if position_ms > self.duration_ms {
            return Err(AudioError::PlaybackError("Seek position exceeds duration".to_string()));
        }

        // TODO: Implement seeking in decoder pipeline
        // This requires seeking in the demuxer and clearing the ring buffer

        self.position_ms = position_ms;
        Ok(())
    }

    pub fn set_volume(&mut self, volume: f32) -> Result<(), AudioError> {
        if volume < 0.0 || volume > 1.0 {
            return Err(AudioError::Other("Volume must be between 0.0 and 1.0".to_string()));
        }

        self.volume = volume;

        if let Some(ref mut renderer) = self.renderer {
            renderer.set_volume(volume)
                .map_err(|e| AudioError::PlaybackError(format!("Failed to set volume: {:?}", e)))?;
        }

        Ok(())
    }

    // ========================================================================
    // State Queries
    // ========================================================================

    pub fn get_state(&self) -> PlayerState {
        self.state
    }

    pub fn get_position(&self) -> u64 {
        // TODO: Get actual position from decoder/renderer
        self.position_ms
    }

    pub fn get_duration(&self) -> u64 {
        self.duration_ms
    }

    pub fn release(&mut self) -> Result<(), AudioError> {
        log::info!("Releasing player resources");

        // Stop playback
        let _ = self.stop();

        // Clear renderer
        self.renderer = None;
        self.ring_buffer = None;
        self.decoder = None;

        self.set_state(PlayerState::Idle);
        Ok(())
    }

    // ========================================================================
    // Internal Methods
    // ========================================================================

    fn set_state(&mut self, state: PlayerState) {
        log::debug!("State transition: {:?} -> {:?}", self.state, state);
        self.state = state;
    }

    fn start_decoder_thread(&self) -> Result<(), AudioError> {
        let decoder_arc = self.decoder.as_ref()
            .ok_or_else(|| AudioError::InvalidState("No decoder".to_string()))?
            .clone();

        let ring_buffer = self.ring_buffer.as_ref()
            .ok_or_else(|| AudioError::InvalidState("No ring buffer".to_string()))?
            .clone();

        // Spawn decoder thread
        std::thread::spawn(move || {
            log::info!("Decoder thread started");

            loop {
                let mut pipeline = decoder_arc.lock();

                // Decode next packet
                match pipeline.decoder.decode_next_packet(&mut pipeline.demuxer) {
                    Ok(Some(pcm_data)) => {
                        // Resample if needed
                        let resampled = match pipeline.resampler.process(&pcm_data) {
                            Ok(data) => data,
                            Err(e) => {
                                log::error!("Resampling error: {:?}", e);
                                continue;
                            }
                        };

                        // Write to ring buffer (will block if buffer is full)
                        ring_buffer.write(&resampled);
                    }
                    Ok(None) => {
                        // End of stream
                        log::info!("End of audio stream");
                        break;
                    }
                    Err(e) => {
                        log::error!("Decoding error: {:?}", e);
                        break;
                    }
                }

                drop(pipeline);
            }

            log::info!("Decoder thread finished");
        });

        Ok(())
    }
}
