// HTTP Range-based media source
// Downloads data on-demand using HTTP Range requests
// Perfect for M4A files where moov atom might be anywhere

use crate::error::{AudioError, Result};
use parking_lot::Mutex;
use std::io::{Read, Seek, SeekFrom};
use std::sync::Arc;
use std::time::Duration;
use symphonia::core::io::MediaSource;

/// Chunk size for Range requests (256KB)
const CHUNK_SIZE: usize = 256 * 1024;

/// Cache entry
#[derive(Clone)]
struct CacheEntry {
    offset: u64,
    data: Vec<u8>,
}

/// HTTP Range source state
struct HttpRangeState {
    url: String,
    total_size: Option<u64>,
    /// Cache of downloaded chunks
    cache: Vec<CacheEntry>,
    /// Agent for HTTP requests
    agent: ureq::Agent,
}

impl HttpRangeState {
    fn new(url: String) -> Self {
        let agent = ureq::AgentBuilder::new()
            .timeout_connect(Duration::from_secs(30))
            .timeout_read(Duration::from_secs(60))
            .user_agent("Mozilla/5.0 (compatible; RustAudioPlayer/1.0)")
            .redirects(10)
            .build();

        Self {
            url,
            total_size: None,
            cache: Vec::new(),
            agent,
        }
    }

    /// Initialize by getting file size
    fn initialize(&mut self) -> Result<()> {
        if self.total_size.is_some() {
            return Ok(());
        }

        let response = self
            .agent
            .head(&self.url)
            .call()
            .map_err(|e| AudioError::NetworkError(format!("HEAD request failed: {}", e)))?;

        self.total_size = response
            .header("Content-Length")
            .and_then(|s| s.parse::<u64>().ok());

        if let Some(size) = self.total_size {
            log::info!(
                "HTTP Range source initialized: {} bytes ({:.2} MB)",
                size,
                size as f64 / 1024.0 / 1024.0
            );
        } else {
            log::warn!("Content-Length not available, will try anyway");
        }

        Ok(())
    }

    /// Check if data is in cache
    fn get_from_cache(&self, offset: u64, size: usize) -> Option<Vec<u8>> {
        for entry in &self.cache {
            if offset >= entry.offset && offset + size as u64 <= entry.offset + entry.data.len() as u64 {
                let start = (offset - entry.offset) as usize;
                let end = start + size;
                return Some(entry.data[start..end].to_vec());
            }
        }
        None
    }

    /// Fetch data from URL using Range request
    fn fetch_range(&mut self, offset: u64, size: usize) -> Result<Vec<u8>> {
        // Check cache first
        if let Some(data) = self.get_from_cache(offset, size) {
            return Ok(data);
        }

        // Fetch a chunk (at least CHUNK_SIZE or the requested size, whichever is larger)
        let chunk_size = size.max(CHUNK_SIZE);
        let end = if let Some(total) = self.total_size {
            (offset + chunk_size as u64).min(total)
        } else {
            offset + chunk_size as u64
        };

        log::debug!(
            "Fetching range: {}-{} ({} bytes)",
            offset,
            end - 1,
            end - offset
        );

        let range_header = format!("bytes={}-{}", offset, end - 1);
        let response = self
            .agent
            .get(&self.url)
            .set("Range", &range_header)
            .call()
            .map_err(|e| AudioError::NetworkError(format!("Range request failed: {}", e)))?;

        let mut data = Vec::new();
        response
            .into_reader()
            .read_to_end(&mut data)
            .map_err(|e| AudioError::IoError(format!("Failed to read response: {}", e)))?;

        // Add to cache
        self.cache.push(CacheEntry {
            offset,
            data: data.clone(),
        });

        // Limit cache size (keep last 20 chunks = ~5MB)
        if self.cache.len() > 20 {
            self.cache.remove(0);
        }

        // Return requested slice
        let requested_size = size.min(data.len());
        Ok(data[..requested_size].to_vec())
    }

    /// Read data at offset
    fn read_at(&mut self, offset: u64, buf: &mut [u8]) -> Result<usize> {
        if buf.is_empty() {
            return Ok(0);
        }

        // Check bounds
        if let Some(total) = self.total_size {
            if offset >= total {
                return Ok(0); // EOF
            }
        }

        let data = self.fetch_range(offset, buf.len())?;
        let to_copy = data.len().min(buf.len());
        buf[..to_copy].copy_from_slice(&data[..to_copy]);
        Ok(to_copy)
    }
}

/// HTTP Range-based media source
pub struct HttpRangeSource {
    state: Arc<Mutex<HttpRangeState>>,
    position: u64,
}

impl HttpRangeSource {
    /// Create a new HTTP Range source
    pub fn new(url: String) -> Result<Self> {
        let mut state = HttpRangeState::new(url);
        state.initialize()?;

        Ok(Self {
            state: Arc::new(Mutex::new(state)),
            position: 0,
        })
    }

    /// Get total size if known
    pub fn total_size(&self) -> Option<u64> {
        let state = self.state.lock();
        state.total_size
    }
}

impl Read for HttpRangeSource {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        if buf.is_empty() {
            return Ok(0);
        }

        let mut state = self.state.lock();
        match state.read_at(self.position, buf) {
            Ok(n) => {
                self.position += n as u64;
                Ok(n)
            }
            Err(e) => Err(std::io::Error::new(
                std::io::ErrorKind::Other,
                format!("HTTP Range read error: {}", e),
            )),
        }
    }
}

impl Seek for HttpRangeSource {
    fn seek(&mut self, pos: SeekFrom) -> std::io::Result<u64> {
        let state = self.state.lock();
        let total_size = state.total_size;
        drop(state);

        let new_pos = match pos {
            SeekFrom::Start(offset) => offset as i64,
            SeekFrom::Current(offset) => self.position as i64 + offset,
            SeekFrom::End(offset) => {
                if let Some(size) = total_size {
                    size as i64 + offset
                } else {
                    return Err(std::io::Error::new(
                        std::io::ErrorKind::Unsupported,
                        "Cannot seek from end: total size unknown",
                    ));
                }
            }
        };

        if new_pos < 0 {
            return Err(std::io::Error::new(
                std::io::ErrorKind::InvalidInput,
                "Cannot seek to negative position",
            ));
        }

        self.position = new_pos as u64;
        Ok(self.position)
    }
}

impl MediaSource for HttpRangeSource {
    fn is_seekable(&self) -> bool {
        true
    }

    fn byte_len(&self) -> Option<u64> {
        let state = self.state.lock();
        state.total_size
    }
}
