// Audio decoding using Symphonia
// Handles various audio formats (MP3, AAC, FLAC, WAV, etc.)

use crate::error::{AudioError, Result};
use crate::metadata::{AudioMetadata, AudioTags, Chapter, CoverArt, FormatInfo, QualityParams};
use symphonia::core::audio::{AudioBufferRef, Signal};
use symphonia::core::codecs::{Decoder, DecoderOptions};
use symphonia::core::errors::Error as SymphoniaError;
use symphonia::core::formats::{FormatOptions, FormatReader, SeekMode, SeekTo};
use symphonia::core::io::{MediaSourceStream, MediaSource};
use symphonia::core::meta::{MetadataOptions, StandardTagKey, Value, Visual, MetadataRevision};
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
    pub metadata: AudioMetadata,
    cover_art: Option<CoverArt>,
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
        let mut probe_result = symphonia::default::get_probe()
            .format(&hint, media_source_stream, &FormatOptions::default(), &MetadataOptions::default())
            .map_err(|e| AudioError::LoadError(format!("Failed to probe media: {}", e)))?;

        let mut format_reader = probe_result.format;

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

        // Extract comprehensive metadata
        let metadata = Self::extract_metadata(
            &mut format_reader,
            &probe_result.metadata,
            codec_params,
            sample_rate,
            channels,
            duration_ms,
        );

        // Extract cover art if available
        let cover_art = Self::extract_cover_art(&probe_result.metadata);

        log::info!("Loaded audio: {}Hz, {} channels, {} ms",
                   format.sample_rate, format.channels, format.duration_ms);
        log::info!("Metadata: {}", metadata.summary());

        Ok(Self {
            format_reader,
            decoder,
            track_id,
            format,
            metadata,
            cover_art,
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

    /// Extract comprehensive metadata from the audio file
    fn extract_metadata(
        format_reader: &mut Box<dyn FormatReader>,
        probe_metadata: &symphonia::core::probe::ProbedMetadata,
        codec_params: &symphonia::core::codecs::CodecParameters,
        sample_rate: u32,
        channels: u16,
        duration_ms: u64,
    ) -> AudioMetadata {
        let mut metadata = AudioMetadata::new();

        // Set format information
        metadata.format_info = FormatInfo {
            duration_ms,
            sample_rate,
            channels,
            codec: codec_params
                .codec
                .to_string()
                .to_uppercase(),
            bitrate_bps: None, // Symphonia 0.5 doesn't expose bitrate directly in CodecParameters
            total_frames: codec_params.n_frames,
        };

        // Set quality parameters
        metadata.quality = QualityParams {
            bit_depth: codec_params.bits_per_sample.map(|b| b as u16),
            is_vbr: false, // VBR detection not available in current Symphonia version
            compression_quality: None,
            instantaneous_bitrate_bps: None,
        };

        // Extract tags from probe metadata
        if let Some(metadata_rev) = probe_metadata.get() {
            if let Some(current) = metadata_rev.current() {
                metadata.tags = Self::extract_tags(current.tags());
            }
        }

        // Also try to get metadata from format reader
        if let Some(metadata_rev) = format_reader.metadata().current() {
            let format_tags = Self::extract_tags(metadata_rev.tags());
            // Merge tags (format_tags take precedence if present)
            Self::merge_tags(&mut metadata.tags, format_tags);
        }

        // Extract chapters if available
        metadata.chapters = Self::extract_chapters(format_reader);

        metadata
    }

    /// Extract tags from Symphonia tag collection
    fn extract_tags(tags: &[symphonia::core::meta::Tag]) -> AudioTags {
        let mut audio_tags = AudioTags::new();

        for tag in tags {
            let value_str = match &tag.value {
                Value::String(s) => s.clone(),
                Value::Binary(_) => continue, // Skip binary values for text tags
                Value::SignedInt(i) => i.to_string(),
                Value::UnsignedInt(u) => u.to_string(),
                Value::Float(f) => f.to_string(),
                Value::Boolean(b) => b.to_string(),
                Value::Flag => "true".to_string(),
            };

            // Map standard tag keys
            if let Some(std_key) = &tag.std_key {
                match std_key {
                    StandardTagKey::TrackTitle => audio_tags.title = Some(value_str),
                    StandardTagKey::Artist => audio_tags.artist = Some(value_str),
                    StandardTagKey::Album => audio_tags.album = Some(value_str),
                    StandardTagKey::AlbumArtist => audio_tags.album_artist = Some(value_str),
                    StandardTagKey::Genre => audio_tags.genre = Some(value_str),
                    StandardTagKey::Date => audio_tags.date = Some(value_str),
                    StandardTagKey::Composer => audio_tags.composer = Some(value_str),
                    StandardTagKey::Comment => audio_tags.comment = Some(value_str),
                    StandardTagKey::Lyrics => audio_tags.lyrics = Some(value_str),
                    StandardTagKey::Copyright => audio_tags.copyright = Some(value_str),
                    StandardTagKey::Encoder => audio_tags.encoder = Some(value_str),
                    StandardTagKey::Label => audio_tags.publisher = Some(value_str),
                    StandardTagKey::IdentIsrc => audio_tags.isrc = Some(value_str),
                    StandardTagKey::Language => audio_tags.language = Some(value_str),
                    StandardTagKey::TrackNumber => {
                        if let Ok(num) = value_str.parse::<u32>() {
                            audio_tags.track_number = Some(num);
                        }
                    }
                    StandardTagKey::TrackTotal => {
                        if let Ok(num) = value_str.parse::<u32>() {
                            audio_tags.track_total = Some(num);
                        }
                    }
                    StandardTagKey::DiscNumber => {
                        if let Ok(num) = value_str.parse::<u32>() {
                            audio_tags.disc_number = Some(num);
                        }
                    }
                    StandardTagKey::DiscTotal => {
                        if let Ok(num) = value_str.parse::<u32>() {
                            audio_tags.disc_total = Some(num);
                        }
                    }
                    _ => {
                        // Store other standard keys as custom tags
                        audio_tags.custom_tags.insert(tag.key.clone(), value_str);
                    }
                }
            } else {
                // Non-standard tags go into custom_tags
                audio_tags.custom_tags.insert(tag.key.clone(), value_str);
            }
        }

        audio_tags
    }

    /// Merge tags, preferring non-None values from source
    fn merge_tags(dest: &mut AudioTags, source: AudioTags) {
        if source.title.is_some() { dest.title = source.title; }
        if source.artist.is_some() { dest.artist = source.artist; }
        if source.album.is_some() { dest.album = source.album; }
        if source.album_artist.is_some() { dest.album_artist = source.album_artist; }
        if source.track_number.is_some() { dest.track_number = source.track_number; }
        if source.track_total.is_some() { dest.track_total = source.track_total; }
        if source.disc_number.is_some() { dest.disc_number = source.disc_number; }
        if source.disc_total.is_some() { dest.disc_total = source.disc_total; }
        if source.date.is_some() { dest.date = source.date; }
        if source.genre.is_some() { dest.genre = source.genre; }
        if source.composer.is_some() { dest.composer = source.composer; }
        if source.comment.is_some() { dest.comment = source.comment; }
        if source.lyrics.is_some() { dest.lyrics = source.lyrics; }
        if source.copyright.is_some() { dest.copyright = source.copyright; }
        if source.encoder.is_some() { dest.encoder = source.encoder; }
        if source.publisher.is_some() { dest.publisher = source.publisher; }
        if source.isrc.is_some() { dest.isrc = source.isrc; }
        if source.language.is_some() { dest.language = source.language; }

        // Merge custom tags
        for (key, value) in source.custom_tags {
            dest.custom_tags.insert(key, value);
        }
    }

    /// Extract cover art from metadata
    fn extract_cover_art(probe_metadata: &symphonia::core::probe::ProbedMetadata) -> Option<CoverArt> {
        if let Some(metadata_rev) = probe_metadata.get() {
            if let Some(current) = metadata_rev.current() {
                // Look for visual (cover art) in metadata
                for visual in current.visuals() {
                    // Prefer front cover, but accept any cover if that's all we have
                    if let Some(usage) = visual.usage {
                        if usage == symphonia::core::meta::StandardVisualKey::FrontCover
                            || usage == symphonia::core::meta::StandardVisualKey::OtherIcon
                        {
                            return Some(Self::visual_to_cover_art(visual));
                        }
                    }
                }

                // If no front cover found, return the first visual
                if let Some(visual) = current.visuals().first() {
                    return Some(Self::visual_to_cover_art(visual));
                }
            }
        }
        None
    }

    /// Convert Symphonia Visual to our CoverArt structure
    fn visual_to_cover_art(visual: &Visual) -> CoverArt {
        CoverArt {
            mime_type: visual.media_type.clone(),
            data: visual.data.to_vec(), // Convert Box<[u8]> to Vec<u8>
            description: visual.tags.iter().find_map(|tag| {
                if tag.std_key == Some(StandardTagKey::Description) {
                    if let Value::String(s) = &tag.value {
                        return Some(s.clone());
                    }
                }
                None
            }),
            picture_type: visual.usage.map(|u| u as u8).unwrap_or(0),
        }
    }

    /// Extract chapter information from the audio file
    fn extract_chapters(_format_reader: &Box<dyn FormatReader>) -> Vec<Chapter> {
        let chapters = Vec::new();

        // Symphonia doesn't have a direct chapter API yet
        // This is a placeholder for future implementation
        // Would need format-specific parsing for MP3 CHAP, MP4 chapters, etc.

        chapters
    }

    /// Get reference to cover art
    pub fn get_cover_art(&self) -> Option<&CoverArt> {
        self.cover_art.as_ref()
    }

    /// Take ownership of cover art (useful for transferring to another structure)
    pub fn take_cover_art(&mut self) -> Option<CoverArt> {
        self.cover_art.take()
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
