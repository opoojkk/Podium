// Audio decoding using Symphonia
// Handles various audio formats (MP3, AAC, FLAC, WAV, etc.)

use crate::error::{AudioError, Result};
use symphonia::core::audio::{AudioBufferRef, Signal};
use symphonia::core::codecs::{Decoder, DecoderOptions};
use symphonia::core::errors::Error as SymphoniaError;
use symphonia::core::formats::{FormatOptions, FormatReader, SeekMode, SeekTo};
use symphonia::core::io::{MediaSourceStream, MediaSource};
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;
use std::fs::File;
use std::io::Cursor;
use std::path::Path;

/// Audio format information
#[derive(Debug, Clone)]
pub struct AudioFormat {
    pub sample_rate: u32,
    pub channels: u16,
    pub bits_per_sample: u16,
    pub duration_ms: u64,
}

/// Audio decoder wrapper
pub struct AudioDecoder {
    format_reader: Box<dyn FormatReader>,
    decoder: Box<dyn Decoder>,
    track_id: u32,
    pub format: AudioFormat,
}

impl AudioDecoder {
    /// Create decoder from file path
    pub fn from_file(path: &str) -> Result<Self> {
        let file = File::open(path)
            .map_err(|e| AudioError::LoadError(format!("Failed to open file: {}", e)))?;

        let media_source = Box::new(file);
        let hint = Self::create_hint_from_path(path);

        Self::from_media_source(media_source, hint)
    }

    /// Create decoder from memory buffer
    pub fn from_buffer(buffer: Vec<u8>) -> Result<Self> {
        let cursor = Cursor::new(buffer);
        let media_source = Box::new(cursor);
        let hint = Hint::new();

        Self::from_media_source(media_source, hint)
    }

    /// Create decoder from media source
    fn from_media_source(
        media_source: Box<dyn MediaSource>,
        hint: Hint,
    ) -> Result<Self> {
        let media_source_stream = MediaSourceStream::new(media_source, Default::default());

        // Probe the media source
        let probe_result = symphonia::default::get_probe()
            .format(&hint, media_source_stream, &FormatOptions::default(), &MetadataOptions::default())
            .map_err(|e| AudioError::LoadError(format!("Failed to probe media: {}", e)))?;

        let format_reader = probe_result.format;

        // Get the default track
        let track = format_reader
            .default_track()
            .ok_or_else(|| AudioError::LoadError("No default track found".to_string()))?;

        let track_id = track.id;

        // Create decoder for the track
        let decoder = symphonia::default::get_codecs()
            .make(&track.codec_params, &DecoderOptions::default())
            .map_err(|e| AudioError::DecodingError(format!("Failed to create decoder: {}", e)))?;

        // Extract audio format information
        let codec_params = &track.codec_params;
        let sample_rate = codec_params.sample_rate
            .ok_or_else(|| AudioError::UnsupportedFormat("Sample rate not specified".to_string()))?;
        let channels = codec_params.channels
            .ok_or_else(|| AudioError::UnsupportedFormat("Channels not specified".to_string()))?
            .count() as u16;

        // Calculate duration
        let duration_ms = if let Some(n_frames) = codec_params.n_frames {
            (n_frames * 1000) / sample_rate as u64
        } else {
            0 // Unknown duration (streaming)
        };

        let format = AudioFormat {
            sample_rate,
            channels,
            bits_per_sample: 16, // Default to 16-bit
            duration_ms,
        };

        log::info!("Loaded audio: {}Hz, {} channels, {} ms",
                   format.sample_rate, format.channels, format.duration_ms);

        Ok(Self {
            format_reader,
            decoder,
            track_id,
            format,
        })
    }

    /// Decode next packet and return audio samples
    pub fn decode_next(&mut self) -> Result<Option<Vec<f32>>> {
        // Get the next packet
        let packet = match self.format_reader.next_packet() {
            Ok(packet) => packet,
            Err(SymphoniaError::IoError(e)) if e.kind() == std::io::ErrorKind::UnexpectedEof => {
                return Ok(None); // End of stream
            }
            Err(e) => {
                return Err(AudioError::DecodingError(format!("Failed to read packet: {}", e)));
            }
        };

        // Skip packets that don't belong to our track
        if packet.track_id() != self.track_id {
            return self.decode_next();
        }

        // Decode the packet
        let decoded = self.decoder.decode(&packet)
            .map_err(|e| AudioError::DecodingError(format!("Failed to decode packet: {}", e)))?;

        // Convert audio buffer to f32 samples
        let mut samples = Self::convert_to_f32(&decoded)?;

        // Convert mono to stereo if needed
        if self.format.channels == 1 {
            samples = Self::mono_to_stereo(samples);
        }

        Ok(Some(samples))
    }

    /// Convert mono samples to stereo by duplicating each sample
    fn mono_to_stereo(mono_samples: Vec<f32>) -> Vec<f32> {
        let mut stereo_samples = Vec::with_capacity(mono_samples.len() * 2);
        for sample in mono_samples {
            stereo_samples.push(sample); // Left channel
            stereo_samples.push(sample); // Right channel (same as left)
        }
        stereo_samples
    }

    /// Seek to a specific time position
    pub fn seek(&mut self, position_ms: u64) -> Result<()> {
        let sample_position = (position_ms * self.format.sample_rate as u64) / 1000;

        self.format_reader
            .seek(
                SeekMode::Accurate,
                SeekTo::TimeStamp { ts: sample_position, track_id: self.track_id }
            )
            .map_err(|e| AudioError::PlaybackError(format!("Seek failed: {}", e)))?;

        // Reset decoder after seek
        self.decoder.reset();

        Ok(())
    }

    /// Convert AudioBufferRef to f32 samples (interleaved)
    fn convert_to_f32(buffer: &AudioBufferRef) -> Result<Vec<f32>> {
        use symphonia::core::conv::IntoSample;

        let mut samples = Vec::new();

        match buffer {
            AudioBufferRef::F32(buf) => {
                // Already f32, just interleave channels
                for frame_idx in 0..buf.frames() {
                    for ch in 0..buf.spec().channels.count() {
                        samples.push(buf.chan(ch)[frame_idx]);
                    }
                }
            }
            AudioBufferRef::U8(buf) => {
                for frame_idx in 0..buf.frames() {
                    for ch in 0..buf.spec().channels.count() {
                        samples.push(buf.chan(ch)[frame_idx].into_sample());
                    }
                }
            }
            AudioBufferRef::U16(buf) => {
                for frame_idx in 0..buf.frames() {
                    for ch in 0..buf.spec().channels.count() {
                        samples.push(buf.chan(ch)[frame_idx].into_sample());
                    }
                }
            }
            AudioBufferRef::U24(buf) => {
                for frame_idx in 0..buf.frames() {
                    for ch in 0..buf.spec().channels.count() {
                        samples.push(buf.chan(ch)[frame_idx].into_sample());
                    }
                }
            }
            AudioBufferRef::U32(buf) => {
                for frame_idx in 0..buf.frames() {
                    for ch in 0..buf.spec().channels.count() {
                        samples.push(buf.chan(ch)[frame_idx].into_sample());
                    }
                }
            }
            AudioBufferRef::S8(buf) => {
                for frame_idx in 0..buf.frames() {
                    for ch in 0..buf.spec().channels.count() {
                        samples.push(buf.chan(ch)[frame_idx].into_sample());
                    }
                }
            }
            AudioBufferRef::S16(buf) => {
                for frame_idx in 0..buf.frames() {
                    for ch in 0..buf.spec().channels.count() {
                        samples.push(buf.chan(ch)[frame_idx].into_sample());
                    }
                }
            }
            AudioBufferRef::S24(buf) => {
                for frame_idx in 0..buf.frames() {
                    for ch in 0..buf.spec().channels.count() {
                        samples.push(buf.chan(ch)[frame_idx].into_sample());
                    }
                }
            }
            AudioBufferRef::S32(buf) => {
                for frame_idx in 0..buf.frames() {
                    for ch in 0..buf.spec().channels.count() {
                        samples.push(buf.chan(ch)[frame_idx].into_sample());
                    }
                }
            }
            AudioBufferRef::F64(buf) => {
                for frame_idx in 0..buf.frames() {
                    for ch in 0..buf.spec().channels.count() {
                        samples.push(buf.chan(ch)[frame_idx] as f32);
                    }
                }
            }
        }

        Ok(samples)
    }

    /// Create a hint from file path
    fn create_hint_from_path(path: &str) -> Hint {
        let mut hint = Hint::new();
        if let Some(extension) = Path::new(path).extension() {
            if let Some(ext_str) = extension.to_str() {
                hint.with_extension(ext_str);
            }
        }
        hint
    }
}

// Sample ring buffer for smooth audio playback
pub struct AudioRingBuffer {
    buffer: Vec<f32>,
    write_pos: usize,
    read_pos: usize,
    size: usize,
}

impl AudioRingBuffer {
    pub fn new(size: usize) -> Self {
        Self {
            buffer: vec![0.0; size],
            write_pos: 0,
            read_pos: 0,
            size,
        }
    }

    pub fn write(&mut self, data: &[f32]) -> usize {
        let available = self.available_write();
        let to_write = data.len().min(available);

        for i in 0..to_write {
            self.buffer[self.write_pos] = data[i];
            self.write_pos = (self.write_pos + 1) % self.size;
        }

        to_write
    }

    pub fn read(&mut self, output: &mut [f32]) -> usize {
        let available = self.available_read();
        let to_read = output.len().min(available);

        for i in 0..to_read {
            output[i] = self.buffer[self.read_pos];
            self.read_pos = (self.read_pos + 1) % self.size;
        }

        to_read
    }

    pub fn available_write(&self) -> usize {
        if self.write_pos >= self.read_pos {
            self.size - (self.write_pos - self.read_pos) - 1
        } else {
            self.read_pos - self.write_pos - 1
        }
    }

    pub fn available_read(&self) -> usize {
        if self.write_pos >= self.read_pos {
            self.write_pos - self.read_pos
        } else {
            self.size - (self.read_pos - self.write_pos)
        }
    }

    pub fn clear(&mut self) {
        self.write_pos = 0;
        self.read_pos = 0;
    }
}
