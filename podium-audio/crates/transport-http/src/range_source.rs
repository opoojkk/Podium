// HTTP Range-based media source for on-demand streaming

use crate::client::create_http_agent;
use parking_lot::Mutex;
use podium_core::{AudioError, Result};
use std::io::{Read, Seek, SeekFrom};
use std::sync::Arc;

/// Chunk size for Range requests (256KB)
const CHUNK_SIZE: usize = 256 * 1024;

/// Maximum cache size (10MB)
const MAX_CACHE_SIZE: usize = 10 * 1024 * 1024;

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
    current_position: u64,
    cache: Vec<CacheEntry>,
    agent: ureq::Agent,
}

impl HttpRangeState {
    fn new(url: String) -> Self {
        Self {
            url,
            total_size: None,
            current_position: 0,
            cache: Vec::new(),
            agent: create_http_agent(),
        }
    }

    fn initialize(&mut self) -> Result<()> {
        if self.total_size.is_some() {
            return Ok(());
        }

        // Try HEAD request to get content length
        match self.agent.head(&self.url).call() {
            Ok(response) => {
                self.total_size = response
                    .header("Content-Length")
                    .and_then(|s| s.parse::<u64>().ok());
            }
            Err(_) => {
                // Fallback: try a small range request
                if let Ok(size) = self.try_get_size_with_range_request() {
                    self.total_size = size;
                }
            }
        }

        if let Some(size) = self.total_size {
            log::info!("HTTP Range source initialized: {} bytes ({:.2} MB)", size, size as f64 / 1024.0 / 1024.0);
        }

        Ok(())
    }

    fn try_get_size_with_range_request(&self) -> Result<Option<u64>> {
        let response = self
            .agent
            .get(&self.url)
            .set("Range", "bytes=0-0")
            .call()
            .map_err(|e| AudioError::NetworkError(format!("Range request failed: {}", e)))?;

        if let Some(range) = response.header("Content-Range") {
            if let Some(total) = Self::parse_total_from_content_range(range) {
                return Ok(Some(total));
            }
        }

        Ok(response
            .header("Content-Length")
            .and_then(|s| s.parse::<u64>().ok()))
    }

    fn parse_total_from_content_range(header: &str) -> Option<u64> {
        header.split('/').last()?.parse::<u64>().ok()
    }

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

    fn fetch_range(&mut self, offset: u64, size: usize) -> Result<Vec<u8>> {
        if let Some(data) = self.get_from_cache(offset, size) {
            return Ok(data);
        }

        // Check if we're trying to read past EOF
        if let Some(total) = self.total_size {
            if offset >= total {
                // At or past EOF, return empty
                return Ok(Vec::new());
            }
        }

        // Fetch a chunk (at least CHUNK_SIZE or the requested size, whichever is larger)
        // But don't go past the known file size
        let chunk_size = size.max(CHUNK_SIZE);
        let end = if let Some(total) = self.total_size {
            // Ensure we don't request past EOF
            let max_end = total.saturating_sub(1);
            (offset + chunk_size as u64).saturating_sub(1).min(max_end)
        } else {
            offset + chunk_size as u64 - 1
        };

        // Validate that start <= end
        if offset > end {
            log::warn!(
                "Invalid range: offset {} > end {}. Returning empty data.",
                offset,
                end
            );
            return Ok(Vec::new());
        }

        log::debug!("Fetching range: bytes={}-{}", offset, end);

        let response = self
            .agent
            .get(&self.url)
            .set("Range", &format!("bytes={}-{}", offset, end))
            .call()
            .map_err(|e| AudioError::NetworkError(format!("Range request failed: {}", e)))?;

        let mut data = Vec::new();
        response
            .into_reader()
            .read_to_end(&mut data)
            .map_err(|e| AudioError::NetworkError(format!("Failed to read response: {}", e)))?;

        // Add to cache
        self.cache.push(CacheEntry {
            offset,
            data: data.clone(),
        });

        // Limit cache size
        let total_cache_size: usize = self.cache.iter().map(|e| e.data.len()).sum();
        if total_cache_size > MAX_CACHE_SIZE {
            self.cache.remove(0);
        }

        // Return only the requested size, not the entire chunk
        Ok(data[..size.min(data.len())].to_vec())
    }
}

/// HTTP Range source that implements MediaSource
pub struct HttpRangeSource {
    state: Arc<Mutex<HttpRangeState>>,
}

impl HttpRangeSource {
    pub fn new(url: String) -> Result<Self> {
        let mut state = HttpRangeState::new(url);
        state.initialize()?;

        Ok(Self {
            state: Arc::new(Mutex::new(state)),
        })
    }

    pub fn byte_len(&self) -> Option<u64> {
        self.state.lock().total_size
    }
}

impl Read for HttpRangeSource {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        let mut state = self.state.lock();
        let offset = state.current_position;

        // Check if we're at EOF when total size is known
        if let Some(total) = state.total_size {
            if offset >= total {
                // Already at or past EOF
                return Ok(0);
            }
        }

        let size = buf.len();

        match state.fetch_range(offset, size) {
            Ok(data) => {
                let bytes_read = data.len();
                buf[..bytes_read].copy_from_slice(&data);
                state.current_position += bytes_read as u64;
                Ok(bytes_read)
            }
            Err(e) => Err(std::io::Error::new(std::io::ErrorKind::Other, e.to_string())),
        }
    }
}

impl Seek for HttpRangeSource {
    fn seek(&mut self, pos: SeekFrom) -> std::io::Result<u64> {
        let mut state = self.state.lock();

        let new_pos = match pos {
            SeekFrom::Start(pos) => pos,
            SeekFrom::Current(offset) => {
                if offset >= 0 {
                    state.current_position + offset as u64
                } else {
                    state.current_position.saturating_sub((-offset) as u64)
                }
            }
            SeekFrom::End(offset) => {
                if let Some(total) = state.total_size {
                    if offset >= 0 {
                        total + offset as u64
                    } else {
                        total.saturating_sub((-offset) as u64)
                    }
                } else {
                    return Err(std::io::Error::new(
                        std::io::ErrorKind::Other,
                        "Cannot seek from end: total size unknown",
                    ));
                }
            }
        };

        state.current_position = new_pos;
        Ok(new_pos)
    }
}

// Implement MediaSource for HttpRangeSource
impl symphonia::core::io::MediaSource for HttpRangeSource {
    fn is_seekable(&self) -> bool {
        true
    }

    fn byte_len(&self) -> Option<u64> {
        self.state.lock().total_size
    }
}
