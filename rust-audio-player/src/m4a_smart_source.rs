// Smart M4A source that handles both Fast Start and non-Fast Start files
// For Fast Start files: direct HTTP streaming (minimal memory)
// For non-Fast Start: virtual Fast Start (runtime moov relocation)

use crate::error::Result;
use crate::streaming_http_source::HttpStreamingSource;
use crate::m4a_virtual_faststart::VirtualFastStartSource;
use std::io::{Read, Seek, SeekFrom};
use std::time::Duration;
use std::thread;
use symphonia::core::io::MediaSource;

/// Size to check for moov atom location
const MOOV_CHECK_SIZE: usize = 256 * 1024; // 256KB should be enough

/// Check if moov atom is in the beginning of data
fn has_moov_at_start(data: &[u8]) -> bool {
    let mut pos = 0;

    while pos + 8 <= data.len() {
        if pos > MOOV_CHECK_SIZE {
            break; // Checked enough
        }

        let size = u32::from_be_bytes([
            data[pos],
            data[pos + 1],
            data[pos + 2],
            data[pos + 3],
        ]) as usize;

        if size < 8 || pos + size > data.len() {
            break;
        }

        let atom_type = &data[pos + 4..pos + 8];
        if atom_type == b"moov" {
            log::info!("Found moov atom at position {} (Fast Start)", pos);
            return true;
        }

        pos += size;
    }

    false
}

/// Smart M4A source that auto-detects Fast Start
pub enum SmartM4ASource {
    /// Fast Start m4a: direct HTTP streaming (best case)
    FastStart(HttpStreamingSource),
    /// Non-Fast Start: virtual Fast Start with runtime moov relocation
    VirtualFastStart(VirtualFastStartSource),
}

impl SmartM4ASource {
    /// Create a smart M4A source that detects Fast Start
    pub fn new(url: String) -> Result<Self> {
        log::info!("Creating smart M4A source for: {}", url);

        // Create HTTP streaming source
        let mut source = HttpStreamingSource::new();
        source.start_download(url.clone())?;

        // Wait for enough data to check (retry logic)
        let is_fast_start = {
            let mut check_buffer = vec![0u8; MOOV_CHECK_SIZE];
            let mut attempts = 0;
            let max_attempts = 20; // 20 * 250ms = 5 seconds max wait

            loop {
                thread::sleep(Duration::from_millis(250));

                match source.read(&mut check_buffer) {
                    Ok(n) if n >= 8192 => {
                        // Got enough data to check (at least 8KB)
                        let has_moov = has_moov_at_start(&check_buffer[..n]);
                        // Reset position
                        let _ = source.seek(SeekFrom::Start(0));
                        log::info!("Fast Start detection: has_moov={}, checked {} bytes", has_moov, n);
                        break has_moov;
                    }
                    Ok(n) => {
                        attempts += 1;
                        if attempts >= max_attempts {
                            log::warn!("Timeout waiting for data, only got {} bytes, assuming Fast Start", n);
                            let _ = source.seek(SeekFrom::Start(0));
                            break true;
                        }
                        // Not enough data yet, wait more
                        let _ = source.seek(SeekFrom::Start(0));
                    }
                    Err(e) => {
                        log::warn!("Could not read for Fast Start check: {}, assuming Fast Start", e);
                        break true;
                    }
                }
            }
        };

        if is_fast_start {
            log::info!("✅ Fast Start M4A detected - using direct streaming");
            Ok(Self::FastStart(source))
        } else {
            log::info!("⚠️  Non-Fast Start M4A detected");
            log::info!("🔧 Attempting virtual Fast Start (runtime moov relocation)");

            // Try to create virtual Fast Start source
            match VirtualFastStartSource::new(url) {
                Ok(vfs) => {
                    log::info!("✅ Virtual Fast Start created successfully");
                    // Virtual Fast Start succeeded, drop the streaming source
                    drop(source);
                    Ok(Self::VirtualFastStart(vfs))
                }
                Err(e) => {
                    log::warn!("⚠️  Virtual Fast Start failed: {}", e);
                    log::warn!("📥 Falling back to direct HTTP streaming (may need full download for moov)");
                    // Fallback: use the original streaming source
                    // This may not work perfectly for Non-Fast Start files,
                    // but at least we try to play it
                    Ok(Self::FastStart(source))
                }
            }
        }
    }
}

impl Read for SmartM4ASource {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        match self {
            Self::FastStart(source) => source.read(buf),
            Self::VirtualFastStart(source) => source.read(buf),
        }
    }
}

impl Seek for SmartM4ASource {
    fn seek(&mut self, pos: SeekFrom) -> std::io::Result<u64> {
        match self {
            Self::FastStart(source) => source.seek(pos),
            Self::VirtualFastStart(source) => source.seek(pos),
        }
    }
}

impl MediaSource for SmartM4ASource {
    fn is_seekable(&self) -> bool {
        match self {
            Self::FastStart(source) => source.is_seekable(),
            Self::VirtualFastStart(source) => source.is_seekable(),
        }
    }

    fn byte_len(&self) -> Option<u64> {
        match self {
            Self::FastStart(source) => source.byte_len(),
            Self::VirtualFastStart(source) => source.byte_len(),
        }
    }
}

/// Convenience function to create M4A source
pub fn create_m4a_source(url: String) -> Result<Box<dyn MediaSource>> {
    Ok(Box::new(SmartM4ASource::new(url)?))
}
