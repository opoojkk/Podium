// M4A/MP4 streaming support with moov atom prefetching
// M4A files may have metadata (moov atom) at the end of the file
// This module handles fetching the moov atom first for seamless streaming

use crate::error::{AudioError, Result};
use parking_lot::{Condvar, Mutex};
use std::io::{Read, Seek, SeekFrom};
use std::sync::Arc;
use std::thread;
use std::time::Duration;
use symphonia::core::io::MediaSource;

/// Minimum size to check for moov atom at file end
const MOOV_CHECK_SIZE: usize = 1024 * 1024; // 1MB

/// Structure to manage M4A streaming with moov atom handling
struct M4AStreamingState {
    /// Downloaded data buffer
    buffer: Vec<u8>,
    /// Total size of the file
    total_size: u64,
    /// Whether moov atom has been fetched and relocated
    moov_ready: bool,
    /// Whether the file download is complete
    download_complete: bool,
    /// Download error if any
    error: Option<String>,
    /// Whether this source has been closed
    closed: bool,
    /// Ranges that have been downloaded (start, end)
    downloaded_ranges: Vec<(u64, u64)>,
}

impl M4AStreamingState {
    /// Check if a position has been downloaded
    fn is_downloaded(&self, pos: u64) -> bool {
        for &(start, end) in &self.downloaded_ranges {
            if pos >= start && pos < end {
                return true;
            }
        }
        false
    }

    /// Add a downloaded range
    fn add_range(&mut self, start: u64, end: u64) {
        self.downloaded_ranges.push((start, end));
        // Sort and merge overlapping ranges
        self.downloaded_ranges.sort_by_key(|&(s, _)| s);

        let mut merged = Vec::new();
        let mut current: Option<(u64, u64)> = None;

        for &(start, end) in &self.downloaded_ranges {
            match current {
                None => current = Some((start, end)),
                Some((cs, ce)) => {
                    if start <= ce {
                        // Overlapping or adjacent, merge
                        current = Some((cs, ce.max(end)));
                    } else {
                        // Non-overlapping, save current and start new
                        merged.push((cs, ce));
                        current = Some((start, end));
                    }
                }
            }
        }

        if let Some(range) = current {
            merged.push(range);
        }

        self.downloaded_ranges = merged;
    }
}

/// M4A streaming media source
pub struct M4AStreamingSource {
    state: Arc<Mutex<M4AStreamingState>>,
    data_available: Arc<Condvar>,
    position: u64,
}

impl M4AStreamingSource {
    /// Create a new M4A streaming source and start downloading
    pub fn new(url: String) -> Result<Self> {
        // First, get the file size
        let agent = Self::create_agent();
        let response = agent
            .head(&url)
            .call()
            .map_err(|e| AudioError::NetworkError(format!("HEAD request failed: {}", e)))?;

        let total_size = response
            .header("Content-Length")
            .and_then(|s| s.parse::<u64>().ok())
            .ok_or_else(|| {
                AudioError::NetworkError("Content-Length header missing".to_string())
            })?;

        log::info!("M4A file size: {} bytes ({:.2} MB)", total_size, total_size as f64 / 1024.0 / 1024.0);

        // Initialize buffer with zeros
        let buffer = vec![0u8; total_size as usize];

        let state = Arc::new(Mutex::new(M4AStreamingState {
            buffer,
            total_size,
            moov_ready: false,
            download_complete: false,
            error: None,
            closed: false,
            downloaded_ranges: Vec::new(),
        }));

        let data_available = Arc::new(Condvar::new());

        let source = Self {
            state: Arc::clone(&state),
            data_available: Arc::clone(&data_available),
            position: 0,
        };

        // Start download in background
        let state_clone = Arc::clone(&state);
        let data_available_clone = Arc::clone(&data_available);
        let url_clone = url.clone();

        thread::spawn(move || {
            if let Err(e) = Self::download_worker(url_clone, state_clone, data_available_clone) {
                log::error!("M4A download failed: {}", e);
            }
        });

        Ok(source)
    }

    fn create_agent() -> ureq::Agent {
        ureq::AgentBuilder::new()
            .timeout_connect(Duration::from_secs(30))
            .timeout_read(Duration::from_secs(60))
            .user_agent("Mozilla/5.0 (compatible; RustAudioPlayer/1.0)")
            .redirects(10)
            .build()
    }

    /// Download worker that handles moov atom prefetching and sequential download
    fn download_worker(
        url: String,
        state: Arc<Mutex<M4AStreamingState>>,
        data_available: Arc<Condvar>,
    ) -> Result<()> {
        log::info!("Starting M4A streaming download");

        let total_size = {
            let state = state.lock();
            state.total_size
        };

        // Step 1: Fetch the beginning of the file to check for moov
        log::info!("Fetching file header...");
        let header_size = MOOV_CHECK_SIZE.min(total_size as usize);
        Self::fetch_range(&url, 0, header_size as u64, &state, &data_available)?;

        // Step 2: Check if moov is at the beginning
        let moov_at_start = {
            let state = state.lock();
            Self::check_moov_at_start(&state.buffer[..header_size])
        };

        if moov_at_start {
            log::info!("moov atom found at beginning - optimized for streaming");
        } else {
            log::info!("moov atom not at beginning - fetching from end");

            // Step 3: Fetch the end of the file to get moov
            let end_start = total_size.saturating_sub(MOOV_CHECK_SIZE as u64);
            Self::fetch_range(&url, end_start, total_size, &state, &data_available)?;

            // Check if we got the moov atom
            let has_moov = {
                let state = state.lock();
                Self::check_moov_in_range(&state.buffer[end_start as usize..])
            };

            if has_moov {
                log::info!("moov atom fetched from end");
            } else {
                log::warn!("moov atom not found in expected locations - continuing anyway");
            }
        }

        // Mark moov as ready
        {
            let mut state = state.lock();
            state.moov_ready = true;
        }
        data_available.notify_all();

        // Step 4: Download the rest of the file sequentially
        log::info!("Starting sequential download of audio data");

        // Download in chunks, skipping already downloaded ranges
        let chunk_size = 256 * 1024; // 256KB chunks
        let mut current_pos = 0u64;

        while current_pos < total_size {
            // Check if source was closed
            {
                let state = state.lock();
                if state.closed {
                    log::info!("Download cancelled");
                    return Ok(());
                }
            }

            // Check if this range is already downloaded
            let is_downloaded = {
                let state = state.lock();
                state.is_downloaded(current_pos)
            };

            if !is_downloaded {
                let chunk_end = (current_pos + chunk_size).min(total_size);
                Self::fetch_range(&url, current_pos, chunk_end, &state, &data_available)?;
            }

            current_pos += chunk_size;

            // Log progress
            if current_pos % (5 * 1024 * 1024) < chunk_size {
                let progress = (current_pos as f64 / total_size as f64) * 100.0;
                log::info!("Download progress: {:.1}%", progress);
            }
        }

        // Mark download as complete
        {
            let mut state = state.lock();
            state.download_complete = true;
            log::info!("M4A download complete");
        }
        data_available.notify_all();

        Ok(())
    }

    /// Fetch a range of bytes from the URL
    fn fetch_range(
        url: &str,
        start: u64,
        end: u64,
        state: &Arc<Mutex<M4AStreamingState>>,
        data_available: &Arc<Condvar>,
    ) -> Result<()> {
        let agent = Self::create_agent();

        let range_header = format!("bytes={}-{}", start, end - 1);
        let response = agent
            .get(url)
            .set("Range", &range_header)
            .call()
            .map_err(|e| AudioError::NetworkError(format!("Range request failed: {}", e)))?;

        let mut reader = response.into_reader();
        let mut buffer = Vec::new();
        reader
            .read_to_end(&mut buffer)
            .map_err(|e| AudioError::NetworkError(format!("Failed to read response: {}", e)))?;

        // Write to state buffer
        {
            let mut state = state.lock();
            let write_start = start as usize;
            let write_end = (start + buffer.len() as u64) as usize;
            state.buffer[write_start..write_end].copy_from_slice(&buffer);
            state.add_range(start, write_end as u64);
        }
        data_available.notify_all();

        Ok(())
    }

    /// Check if moov atom is at the beginning of the file
    fn check_moov_at_start(data: &[u8]) -> bool {
        if data.len() < 8 {
            return false;
        }

        // Look for ftyp and moov atoms near the beginning
        let mut pos = 0;
        while pos + 8 <= data.len() {
            let size = u32::from_be_bytes([data[pos], data[pos + 1], data[pos + 2], data[pos + 3]]) as usize;
            let atom_type = &data[pos + 4..pos + 8];

            if atom_type == b"moov" {
                log::debug!("Found moov atom at position {}", pos);
                return true;
            }

            if size < 8 || pos + size > data.len() {
                break;
            }

            pos += size;

            // Only check first few atoms
            if pos > MOOV_CHECK_SIZE / 2 {
                break;
            }
        }

        false
    }

    /// Check if moov atom exists in the given range
    fn check_moov_in_range(data: &[u8]) -> bool {
        // Search for moov atom signature
        for i in 0..data.len().saturating_sub(8) {
            if &data[i + 4..i + 8] == b"moov" {
                log::debug!("Found moov atom in range at offset {}", i);
                return true;
            }
        }
        false
    }

    /// Wait for data at position to be available
    fn wait_for_data(&self, required_pos: u64, timeout: Duration) -> Result<bool> {
        let mut state = self.state.lock();
        let deadline = std::time::Instant::now() + timeout;

        loop {
            // Check for error
            if let Some(ref error) = state.error {
                return Err(AudioError::NetworkError(error.clone()));
            }

            // Check if moov is ready
            if !state.moov_ready {
                let remaining = deadline.saturating_duration_since(std::time::Instant::now());
                if remaining.is_zero() {
                    return Err(AudioError::DecodingError("Timeout waiting for moov atom".to_string()));
                }
                self.data_available.wait_for(&mut state, remaining);
                continue;
            }

            // Check if position is downloaded
            if state.is_downloaded(required_pos) {
                return Ok(true);
            }

            // Check if download is complete
            if state.download_complete {
                return Ok(required_pos < state.total_size);
            }

            // Wait for more data
            let remaining = deadline.saturating_duration_since(std::time::Instant::now());
            if remaining.is_zero() {
                return Err(AudioError::DecodingError("Timeout waiting for data".to_string()));
            }

            self.data_available.wait_for(&mut state, remaining);
        }
    }
}

impl Read for M4AStreamingSource {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        if buf.is_empty() {
            return Ok(0);
        }

        // Wait for data to be available
        match self.wait_for_data(self.position, Duration::from_secs(30)) {
            Ok(true) => {}
            Ok(false) => return Ok(0), // EOF
            Err(e) => {
                return Err(std::io::Error::new(
                    std::io::ErrorKind::Other,
                    format!("Download error: {}", e),
                ))
            }
        }

        // Read from buffer
        let state = self.state.lock();
        let start = self.position as usize;
        let end = (self.position + buf.len() as u64).min(state.total_size) as usize;
        let available = end.saturating_sub(start);

        if available == 0 {
            return Ok(0); // EOF
        }

        let to_read = available.min(buf.len());
        buf[..to_read].copy_from_slice(&state.buffer[start..start + to_read]);

        drop(state);

        self.position += to_read as u64;
        Ok(to_read)
    }
}

impl Seek for M4AStreamingSource {
    fn seek(&mut self, pos: SeekFrom) -> std::io::Result<u64> {
        let state = self.state.lock();
        let total_size = state.total_size;
        drop(state);

        let new_pos = match pos {
            SeekFrom::Start(offset) => offset as i64,
            SeekFrom::Current(offset) => self.position as i64 + offset,
            SeekFrom::End(offset) => total_size as i64 + offset,
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

impl MediaSource for M4AStreamingSource {
    fn is_seekable(&self) -> bool {
        true
    }

    fn byte_len(&self) -> Option<u64> {
        let state = self.state.lock();
        Some(state.total_size)
    }
}

impl Drop for M4AStreamingSource {
    fn drop(&mut self) {
        let mut state = self.state.lock();
        state.closed = true;
        drop(state);
        self.data_available.notify_all();
    }
}
