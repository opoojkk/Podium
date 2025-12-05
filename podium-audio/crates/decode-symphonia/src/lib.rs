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
    let num_channels = buffer.spec().channels.count();
    let num_frames = buffer.frames();
    let mut output = Vec::with_capacity(num_frames * num_channels);

    match buffer {
        AudioBufferRef::F32(buf) => {
            // F32: Direct copy and interleave
            for frame_idx in 0..num_frames {
                for ch_idx in 0..num_channels {
                    output.push(buf.chan(ch_idx)[frame_idx]);
                }
            }
        }
        AudioBufferRef::F64(buf) => {
            // F64: Convert to f32 and interleave
            for frame_idx in 0..num_frames {
                for ch_idx in 0..num_channels {
                    output.push(buf.chan(ch_idx)[frame_idx] as f32);
                }
            }
        }
        AudioBufferRef::S8(buf) => {
            // S8: Convert to f32 [-1.0, 1.0] and interleave
            for frame_idx in 0..num_frames {
                for ch_idx in 0..num_channels {
                    let sample = buf.chan(ch_idx)[frame_idx];
                    output.push(sample as f32 / 128.0);
                }
            }
        }
        AudioBufferRef::S16(buf) => {
            // S16: Convert to f32 [-1.0, 1.0] and interleave
            for frame_idx in 0..num_frames {
                for ch_idx in 0..num_channels {
                    let sample = buf.chan(ch_idx)[frame_idx];
                    output.push(sample as f32 / 32768.0);
                }
            }
        }
        AudioBufferRef::S24(buf) => {
            // S24: Stored as i32, convert to f32 [-1.0, 1.0] and interleave
            for frame_idx in 0..num_frames {
                for ch_idx in 0..num_channels {
                    let sample = buf.chan(ch_idx)[frame_idx];
                    // S24 is a newtype around i32, use inner() to get the i32 value
                    output.push(sample.inner() as f32 / 8388608.0); // 2^23
                }
            }
        }
        AudioBufferRef::S32(buf) => {
            // S32: Convert to f32 [-1.0, 1.0] and interleave
            for frame_idx in 0..num_frames {
                for ch_idx in 0..num_channels {
                    let sample = buf.chan(ch_idx)[frame_idx];
                    output.push(sample as f32 / 2147483648.0); // 2^31
                }
            }
        }
        AudioBufferRef::U8(buf) => {
            // U8: Convert to f32 [-1.0, 1.0] and interleave
            for frame_idx in 0..num_frames {
                for ch_idx in 0..num_channels {
                    let sample = buf.chan(ch_idx)[frame_idx];
                    output.push((sample as f32 - 128.0) / 128.0);
                }
            }
        }
        AudioBufferRef::U16(buf) => {
            // U16: Convert to f32 [-1.0, 1.0] and interleave
            for frame_idx in 0..num_frames {
                for ch_idx in 0..num_channels {
                    let sample = buf.chan(ch_idx)[frame_idx];
                    output.push((sample as f32 - 32768.0) / 32768.0);
                }
            }
        }
        AudioBufferRef::U24(buf) => {
            // U24: Stored as u32, convert to f32 [-1.0, 1.0] and interleave
            for frame_idx in 0..num_frames {
                for ch_idx in 0..num_channels {
                    let sample = buf.chan(ch_idx)[frame_idx];
                    // U24 is a newtype around u32, use inner() to get the u32 value
                    output.push((sample.inner() as f32 - 8388608.0) / 8388608.0); // 2^23
                }
            }
        }
        AudioBufferRef::U32(buf) => {
            // U32: Convert to f32 [-1.0, 1.0] and interleave
            for frame_idx in 0..num_frames {
                for ch_idx in 0..num_channels {
                    let sample = buf.chan(ch_idx)[frame_idx];
                    output.push((sample as f32 - 2147483648.0) / 2147483648.0); // 2^31
                }
            }
        }
    }

    output
}
