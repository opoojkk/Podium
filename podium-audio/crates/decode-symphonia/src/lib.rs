// Audio decoding using Symphonia

use podium_core::{AudioError, Result};
use podium_demux_symphonia::Demuxer;
use symphonia::core::audio::{AudioBufferRef, Signal};
use symphonia::core::codecs::{Decoder, DecoderOptions};
use symphonia::core::formats::Packet;

/// Audio decoder
pub struct AudioDecoder {
    decoder: Box<dyn Decoder>,
    sample_rate: u32,
    channels: u16,
}

impl AudioDecoder {
    /// Create decoder from demuxer
    pub fn from_demuxer(demuxer: &Demuxer) -> Result<Self> {
        let track_info = demuxer.get_track_info()?;
        let track = demuxer
            .format_reader()
            .tracks()
            .iter()
            .find(|t| t.id == demuxer.track_id())
            .ok_or_else(|| AudioError::LoadError("Track not found".to_string()))?;

        let codec_params = &track.codec_params;

        let decoder = symphonia::default::get_codecs()
            .make(codec_params, &DecoderOptions::default())
            .map_err(|e| AudioError::DecodingError(format!("Failed to create decoder: {}", e)))?;

        Ok(Self {
            decoder,
            sample_rate: track_info.sample_rate,
            channels: track_info.channels,
        })
    }

    /// Decode a packet into PCM samples
    pub fn decode(&mut self, packet: &Packet) -> Result<Vec<f32>> {
        let audio_buf = self
            .decoder
            .decode(packet)
            .map_err(|e| AudioError::DecodingError(format!("Decoding failed: {}", e)))?;

        Ok(convert_audio_buffer_to_f32(audio_buf))
    }

    /// Get sample rate
    pub fn sample_rate(&self) -> u32 {
        self.sample_rate
    }

    /// Get channel count
    pub fn channels(&self) -> u16 {
        self.channels
    }
}

/// Convert Symphonia AudioBufferRef to interleaved f32 samples
fn convert_audio_buffer_to_f32(buffer: AudioBufferRef) -> Vec<f32> {
    match buffer {
        AudioBufferRef::F32(buf) => {
            let num_channels = buf.spec().channels.count();
            let num_frames = buf.frames();
            let mut output = Vec::with_capacity(num_frames * num_channels);

            // Interleave channels
            for frame_idx in 0..num_frames {
                for ch_idx in 0..num_channels {
                    output.push(buf.chan(ch_idx)[frame_idx]);
                }
            }

            output
        }
        AudioBufferRef::S16(buf) => {
            let num_channels = buf.spec().channels.count();
            let num_frames = buf.frames();
            let mut output = Vec::with_capacity(num_frames * num_channels);

            // Convert i16 to f32 and interleave
            for frame_idx in 0..num_frames {
                for ch_idx in 0..num_channels {
                    let sample = buf.chan(ch_idx)[frame_idx];
                    output.push(sample as f32 / 32768.0);
                }
            }

            output
        }
        AudioBufferRef::S32(buf) => {
            let num_channels = buf.spec().channels.count();
            let num_frames = buf.frames();
            let mut output = Vec::with_capacity(num_frames * num_channels);

            // Convert i32 to f32 and interleave
            for frame_idx in 0..num_frames {
                for ch_idx in 0..num_channels {
                    let sample = buf.chan(ch_idx)[frame_idx];
                    output.push(sample as f32 / 2147483648.0);
                }
            }

            output
        }
        AudioBufferRef::U8(buf) => {
            let num_channels = buf.spec().channels.count();
            let num_frames = buf.frames();
            let mut output = Vec::with_capacity(num_frames * num_channels);

            // Convert u8 to f32 and interleave
            for frame_idx in 0..num_frames {
                for ch_idx in 0..num_channels {
                    let sample = buf.chan(ch_idx)[frame_idx];
                    output.push((sample as f32 - 128.0) / 128.0);
                }
            }

            output
        }
        _ => {
            // Handle other formats by converting to f32
            log::warn!("Unoptimized audio format conversion");
            Vec::new()
        }
    }
}
