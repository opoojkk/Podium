// Demuxing audio formats using Symphonia

use podium_core::{AudioError, Result};
use symphonia::core::formats::{FormatOptions, FormatReader};
use symphonia::core::io::{MediaSource, MediaSourceStream};
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;

/// Audio demuxer wrapper
pub struct Demuxer {
    format_reader: Box<dyn FormatReader>,
    track_id: u32,
}

impl Demuxer {
    /// Create demuxer from a media source
    pub fn from_media_source(media_source: Box<dyn MediaSource>, hint: Hint) -> Result<Self> {
        let media_source_stream = MediaSourceStream::new(media_source, Default::default());

        // Probe the media source
        let probe_result = symphonia::default::get_probe()
            .format(
                &hint,
                media_source_stream,
                &FormatOptions::default(),
                &MetadataOptions::default(),
            )
            .map_err(|e| AudioError::LoadError(format!("Failed to probe media: {}", e)))?;

        let format_reader = probe_result.format;

        // Get the default track
        let track = format_reader
            .default_track()
            .ok_or_else(|| AudioError::LoadError("No default track found".to_string()))?;

        let track_id = track.id;

        Ok(Self {
            format_reader,
            track_id,
        })
    }

    /// Create a hint from file extension
    pub fn create_hint_from_path(path: &str) -> Hint {
        let mut hint = Hint::new();
        if let Some(extension) = std::path::Path::new(path)
            .extension()
            .and_then(|e| e.to_str())
        {
            hint.with_extension(extension);
        }
        hint
    }

    /// Get the next packet from the format reader
    pub fn next_packet(&mut self) -> Result<symphonia::core::formats::Packet> {
        loop {
            let packet = self
                .format_reader
                .next_packet()
                .map_err(|e| AudioError::DecodingError(format!("Failed to read packet: {}", e)))?;

            // Only return packets for our track
            if packet.track_id() == self.track_id {
                return Ok(packet);
            }
        }
    }

    /// Seek to a specific time position
    pub fn seek(&mut self, time_ms: u64) -> Result<()> {
        let time_base = self
            .format_reader
            .tracks()
            .iter()
            .find(|t| t.id == self.track_id)
            .and_then(|t| t.codec_params.time_base);

        if let Some(tb) = time_base {
            let timestamp = (time_ms * tb.denom as u64) / (tb.numer as u64 * 1000);
            self.format_reader
                .seek(
                    symphonia::core::formats::SeekMode::Accurate,
                    symphonia::core::formats::SeekTo::TimeStamp { ts: timestamp, track_id: self.track_id },
                )
                .map_err(|e| AudioError::PlaybackError(format!("Seek failed: {}", e)))?;
        }

        Ok(())
    }

    /// Get track information
    pub fn get_track_info(&self) -> Result<TrackInfo> {
        let track = self
            .format_reader
            .tracks()
            .iter()
            .find(|t| t.id == self.track_id)
            .ok_or_else(|| AudioError::LoadError("Track not found".to_string()))?;

        let codec_params = &track.codec_params;

        Ok(TrackInfo {
            sample_rate: codec_params
                .sample_rate
                .ok_or_else(|| AudioError::UnsupportedFormat("Sample rate not specified".to_string()))?,
            channels: codec_params
                .channels
                .map(|c| c.count() as u16)
                .unwrap_or(2),
            duration_ms: codec_params
                .time_base
                .and_then(|tb| codec_params.n_frames.map(|n| (n * 1000 * tb.numer as u64) / tb.denom as u64))
                .unwrap_or(0),
        })
    }

    /// Get reference to format reader
    pub fn format_reader(&self) -> &dyn FormatReader {
        &*self.format_reader
    }

    /// Get mutable reference to format reader
    pub fn format_reader_mut(&mut self) -> &mut Box<dyn FormatReader> {
        &mut self.format_reader
    }

    /// Get track ID
    pub fn track_id(&self) -> u32 {
        self.track_id
    }
}

/// Track information
#[derive(Debug, Clone)]
pub struct TrackInfo {
    pub sample_rate: u32,
    pub channels: u16,
    pub duration_ms: u64,
}
