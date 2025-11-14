// Audio metadata extraction and management
// Supports ID3, Vorbis Comments, and other formats

use std::collections::HashMap;

/// Comprehensive audio metadata
#[derive(Debug, Clone, Default)]
pub struct AudioMetadata {
    // Basic format information
    pub format_info: FormatInfo,

    // ID3/Tag information
    pub tags: AudioTags,

    // Audio quality parameters
    pub quality: QualityParams,

    // Chapter information (for podcasts)
    pub chapters: Vec<Chapter>,
}

/// Basic audio format information
#[derive(Debug, Clone, Default)]
pub struct FormatInfo {
    /// Duration in milliseconds
    pub duration_ms: u64,

    /// Sample rate (e.g., 44100Hz, 48000Hz)
    pub sample_rate: u32,

    /// Number of channels (1=mono, 2=stereo, etc.)
    pub channels: u16,

    /// Codec name (e.g., "MP3", "AAC", "Opus", "Vorbis", "FLAC")
    pub codec: String,

    /// Average bitrate in bits per second (bps)
    pub bitrate_bps: Option<u32>,

    /// Total number of frames
    pub total_frames: Option<u64>,
}

/// Audio quality parameters
#[derive(Debug, Clone, Default)]
pub struct QualityParams {
    /// Bit depth (bits per sample, e.g., 16, 24, 32)
    pub bit_depth: Option<u16>,

    /// Whether this is Variable Bit Rate (VBR)
    pub is_vbr: bool,

    /// Compression quality level (0-10 for some formats)
    pub compression_quality: Option<u8>,

    /// Instantaneous bitrate (for VBR files)
    pub instantaneous_bitrate_bps: Option<u32>,
}

/// Audio tags (ID3, Vorbis comments, etc.)
#[derive(Debug, Clone, Default)]
pub struct AudioTags {
    /// Track title
    pub title: Option<String>,

    /// Artist name
    pub artist: Option<String>,

    /// Album name
    pub album: Option<String>,

    /// Album artist
    pub album_artist: Option<String>,

    /// Track number
    pub track_number: Option<u32>,

    /// Total tracks in album
    pub track_total: Option<u32>,

    /// Disc number
    pub disc_number: Option<u32>,

    /// Total discs
    pub disc_total: Option<u32>,

    /// Release date (YYYY-MM-DD or just YYYY)
    pub date: Option<String>,

    /// Genre
    pub genre: Option<String>,

    /// Composer
    pub composer: Option<String>,

    /// Comment
    pub comment: Option<String>,

    /// Lyrics/Text
    pub lyrics: Option<String>,

    /// Copyright
    pub copyright: Option<String>,

    /// Encoder used
    pub encoder: Option<String>,

    /// Publisher
    pub publisher: Option<String>,

    /// ISRC (International Standard Recording Code)
    pub isrc: Option<String>,

    /// Language
    pub language: Option<String>,

    /// Additional custom tags
    pub custom_tags: HashMap<String, String>,
}

/// Album art / Cover image
#[derive(Debug, Clone)]
pub struct CoverArt {
    /// MIME type (e.g., "image/jpeg", "image/png")
    pub mime_type: String,

    /// Image data
    pub data: Vec<u8>,

    /// Description (e.g., "Front Cover", "Back Cover")
    pub description: Option<String>,

    /// Picture type (e.g., 3 = Cover (front))
    pub picture_type: u8,
}

/// Chapter marker (important for podcasts)
#[derive(Debug, Clone)]
pub struct Chapter {
    /// Chapter start time in milliseconds
    pub start_time_ms: u64,

    /// Chapter end time in milliseconds
    pub end_time_ms: u64,

    /// Chapter title
    pub title: Option<String>,

    /// Chapter description
    pub description: Option<String>,

    /// Chapter URL
    pub url: Option<String>,
}

impl AudioMetadata {
    /// Create a new empty metadata instance
    pub fn new() -> Self {
        Self::default()
    }

    /// Check if metadata has any tag information
    pub fn has_tags(&self) -> bool {
        self.tags.title.is_some()
            || self.tags.artist.is_some()
            || self.tags.album.is_some()
    }

    /// Get a summary string of the metadata
    pub fn summary(&self) -> String {
        let mut parts = Vec::new();

        if let Some(ref title) = self.tags.title {
            parts.push(format!("Title: {}", title));
        }
        if let Some(ref artist) = self.tags.artist {
            parts.push(format!("Artist: {}", artist));
        }
        if let Some(ref album) = self.tags.album {
            parts.push(format!("Album: {}", album));
        }

        parts.push(format!(
            "Format: {} @ {}Hz, {} ch, {} ms",
            self.format_info.codec,
            self.format_info.sample_rate,
            self.format_info.channels,
            self.format_info.duration_ms
        ));

        if let Some(bitrate) = self.format_info.bitrate_bps {
            parts.push(format!("Bitrate: {} kbps", bitrate / 1000));
        }

        if self.quality.is_vbr {
            parts.push("VBR".to_string());
        }

        parts.join(", ")
    }
}

impl AudioTags {
    /// Create new empty tags
    pub fn new() -> Self {
        Self::default()
    }

    /// Get tag value by standard key name
    pub fn get_tag(&self, key: &str) -> Option<&str> {
        match key.to_lowercase().as_str() {
            "title" => self.title.as_deref(),
            "artist" => self.artist.as_deref(),
            "album" => self.album.as_deref(),
            "albumartist" | "album_artist" => self.album_artist.as_deref(),
            "genre" => self.genre.as_deref(),
            "date" | "year" => self.date.as_deref(),
            "composer" => self.composer.as_deref(),
            "comment" => self.comment.as_deref(),
            "lyrics" => self.lyrics.as_deref(),
            "copyright" => self.copyright.as_deref(),
            "encoder" => self.encoder.as_deref(),
            "publisher" => self.publisher.as_deref(),
            "isrc" => self.isrc.as_deref(),
            "language" => self.language.as_deref(),
            _ => self.custom_tags.get(key).map(|s| s.as_str()),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_metadata_creation() {
        let mut metadata = AudioMetadata::new();
        metadata.tags.title = Some("Test Song".to_string());
        metadata.tags.artist = Some("Test Artist".to_string());
        metadata.format_info.codec = "MP3".to_string();
        metadata.format_info.sample_rate = 44100;

        assert!(metadata.has_tags());
        assert!(metadata.summary().contains("Test Song"));
    }

    #[test]
    fn test_tags_get() {
        let mut tags = AudioTags::new();
        tags.title = Some("My Song".to_string());
        tags.artist = Some("My Artist".to_string());

        assert_eq!(tags.get_tag("title"), Some("My Song"));
        assert_eq!(tags.get_tag("TITLE"), Some("My Song"));
        assert_eq!(tags.get_tag("artist"), Some("My Artist"));
        assert_eq!(tags.get_tag("unknown"), None);
    }
}
