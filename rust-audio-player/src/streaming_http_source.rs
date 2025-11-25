// True streaming HTTP source with sliding window buffer
// Releases played data to keep memory usage low

use crate::error::{AudioError, Result};
use parking_lot::{Condvar, Mutex};
use std::io::{Read, Seek, SeekFrom};
use std::sync::Arc;
use std::thread;
use std::time::Duration;
use symphonia::core::io::MediaSource;

/// Sliding window buffer size (4MB should be enough for most cases)
const BUFFER_WINDOW_SIZE: usize = 4 * 1024 * 1024;

/// Minimum data to keep in buffer before downloading more
const BUFFER_LOW_WATERMARK: usize = 1 * 1024 * 1024;

/// Shared state for HTTP streaming with sliding window
struct HttpStreamState {
    /// Sliding window buffer
    buffer: Vec<u8>,
    /// Offset of the buffer start in the file
    buffer_start_offset: u64,
    /// Total file size (if known)
    total_size: Option<u64>,
    /// Current download position in the file
    download_position: u64,
    /// Whether download is complete
    download_complete: bool,
    /// Download error if any
    error: Option<String>,
    /// Whether this source has been closed
    closed: bool,
}

impl HttpStreamState {
    fn new() -> Self {
        Self {
            buffer: Vec::with_capacity(BUFFER_WINDOW_SIZE),
            buffer_start_offset: 0,
            total_size: None,
            download_position: 0,
            download_complete: false,
            error: None,
            closed: false,
        }
    }

    /// Check if a position is available in buffer
    fn is_available(&self, pos: u64) -> bool {
        if pos < self.buffer_start_offset {
            // Position is before buffer (already played and released)
            false
        } else {
            let offset_in_buffer = (pos - self.buffer_start_offset) as usize;
            offset_in_buffer < self.buffer.len()
        }
    }

    /// Read from buffer at absolute file position
    fn read_at(&self, pos: u64, buf: &mut [u8]) -> usize {
        if pos < self.buffer_start_offset {
            return 0; // Data already released
        }

        let offset_in_buffer = (pos - self.buffer_start_offset) as usize;
        if offset_in_buffer >= self.buffer.len() {
            return 0; // Not yet downloaded
        }

        let available = self.buffer.len() - offset_in_buffer;
        let to_read = available.min(buf.len());
        buf[..to_read].copy_from_slice(&self.buffer[offset_in_buffer..offset_in_buffer + to_read]);
        to_read
    }

    /// Release data before position (sliding window)
    fn release_before(&mut self, pos: u64) {
        if pos <= self.buffer_start_offset {
            return; // Nothing to release
        }

        let release_count = ((pos - self.buffer_start_offset) as usize).min(self.buffer.len());
        if release_count == 0 {
            return;
        }

        // Remove released data from buffer
        self.buffer.drain(0..release_count);
        self.buffer_start_offset = pos;

        log::debug!(
            "Released {} bytes, buffer now starts at offset {}, size: {}",
            release_count,
            self.buffer_start_offset,
            self.buffer.len()
        );
    }
}

/// HTTP streaming source with sliding window
pub struct HttpStreamingSource {
    state: Arc<Mutex<HttpStreamState>>,
    data_available: Arc<Condvar>,
    /// Current read position for this reader
    position: u64,
    /// Last position we released data before
    last_release_position: u64,
}

impl HttpStreamingSource {
    /// Create a new HTTP streaming source
    pub fn new() -> Self {
        Self {
            state: Arc::new(Mutex::new(HttpStreamState::new())),
            data_available: Arc::new(Condvar::new()),
            position: 0,
            last_release_position: 0,
        }
    }

    /// Start downloading from URL
    pub fn start_download(&self, url: String) -> Result<()> {
        let state = Arc::clone(&self.state);
        let data_available = Arc::clone(&self.data_available);

        thread::spawn(move || {
            if let Err(e) = Self::download_worker(url, state, data_available) {
                log::error!("Download failed: {}", e);
            }
        });

        Ok(())
    }

    /// Download worker thread
    fn download_worker(
        url: String,
        state: Arc<Mutex<HttpStreamState>>,
        data_available: Arc<Condvar>,
    ) -> Result<()> {
        log::info!("Starting HTTP streaming download from: {}", url);

        // Create HTTP agent
        let agent = ureq::AgentBuilder::new()
            .timeout_connect(Duration::from_secs(30))
            .timeout_read(Duration::from_secs(60))
            .user_agent("Mozilla/5.0 (compatible; RustAudioPlayer/1.0)")
            .redirects(10)
            .build();

        // Make HTTP request
        let response = match agent.get(&url).call() {
            Ok(resp) => resp,
            Err(e) => {
                let mut state = state.lock();
                state.error = Some(format!("HTTP request failed: {}", e));
                data_available.notify_all();
                return Err(AudioError::NetworkError(format!("HTTP request failed: {}", e)));
            }
        };

        // Get content length
        let content_length = response
            .header("Content-Length")
            .and_then(|s| s.parse::<u64>().ok());

        if let Some(len) = content_length {
            log::info!("Content length: {} bytes ({:.2} MB)", len, len as f64 / 1024.0 / 1024.0);
            let mut state = state.lock();
            state.total_size = Some(len);
        }

        // Download in chunks
        let mut reader = response.into_reader();
        let mut chunk_buffer = vec![0u8; 65536]; // 64KB chunks

        loop {
            // Check if closed
            {
                let state = state.lock();
                if state.closed {
                    log::info!("Download cancelled");
                    return Ok(());
                }
            }

            // Read next chunk
            let bytes_read = match reader.read(&mut chunk_buffer) {
                Ok(0) => break, // EOF
                Ok(n) => n,
                Err(e) => {
                    let mut state = state.lock();
                    state.error = Some(format!("Download error: {}", e));
                    data_available.notify_all();
                    return Err(AudioError::NetworkError(format!("Download error: {}", e)));
                }
            };

            // Append to buffer
            {
                let mut state = state.lock();
                state.buffer.extend_from_slice(&chunk_buffer[..bytes_read]);
                state.download_position += bytes_read as u64;

                // Log progress periodically
                if state.download_position % (1024 * 1024) < 65536 {
                    if let Some(total) = state.total_size {
                        let progress = (state.download_position as f64 / total as f64) * 100.0;
                        log::debug!(
                            "Downloaded: {:.2} MB / {:.2} MB ({:.1}%)",
                            state.download_position as f64 / 1024.0 / 1024.0,
                            total as f64 / 1024.0 / 1024.0,
                            progress
                        );
                    }
                }
            }

            data_available.notify_all();
        }

        // Mark complete
        {
            let mut state = state.lock();
            state.download_complete = true;
            log::info!(
                "Download complete: {:.2} MB",
                state.download_position as f64 / 1024.0 / 1024.0
            );
        }
        data_available.notify_all();

        Ok(())
    }

    /// Wait for data at position
    fn wait_for_data(&self, pos: u64, timeout: Duration) -> Result<bool> {
        let mut state = self.state.lock();
        let deadline = std::time::Instant::now() + timeout;

        loop {
            // Check error
            if let Some(ref error) = state.error {
                return Err(AudioError::NetworkError(error.clone()));
            }

            // Check if available
            if state.is_available(pos) {
                return Ok(true);
            }

            // Check if complete
            if state.download_complete {
                return Ok(false);
            }

            // Wait
            let remaining = deadline.saturating_duration_since(std::time::Instant::now());
            if remaining.is_zero() {
                return Err(AudioError::DecodingError("Timeout waiting for data".to_string()));
            }

            self.data_available.wait_for(&mut state, remaining);
        }
    }

    /// Get total size if known
    pub fn total_size(&self) -> Option<u64> {
        let state = self.state.lock();
        state.total_size
    }
}

impl Read for HttpStreamingSource {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        if buf.is_empty() {
            return Ok(0);
        }

        // Wait for data
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
        let bytes_read = {
            let state = self.state.lock();
            state.read_at(self.position, buf)
        };

        if bytes_read > 0 {
            self.position += bytes_read as u64;

            // Release old data when we've moved forward significantly
            if self.position - self.last_release_position > BUFFER_LOW_WATERMARK as u64 {
                let release_pos = self.position.saturating_sub(BUFFER_LOW_WATERMARK as u64);
                let mut state = self.state.lock();
                state.release_before(release_pos);
                drop(state);
                self.last_release_position = release_pos;
            }
        }

        Ok(bytes_read)
    }
}

impl Seek for HttpStreamingSource {
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

impl MediaSource for HttpStreamingSource {
    fn is_seekable(&self) -> bool {
        let state = self.state.lock();
        state.total_size.is_some()
    }

    fn byte_len(&self) -> Option<u64> {
        let state = self.state.lock();
        state.total_size
    }
}

impl Drop for HttpStreamingSource {
    fn drop(&mut self) {
        let mut state = self.state.lock();
        state.closed = true;
        drop(state);
        self.data_available.notify_all();
    }
}
