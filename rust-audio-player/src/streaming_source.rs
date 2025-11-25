// Streaming media source for progressive download and playback
// Allows audio playback to start before the entire file is downloaded

use crate::error::{AudioError, Result};
use parking_lot::{Condvar, Mutex};
use std::io::{Read, Seek, SeekFrom};
use std::sync::Arc;
use std::thread;
use std::time::Duration;
use symphonia::core::io::MediaSource;

/// Shared state between the streaming source and download thread
struct StreamingState {
    /// Downloaded data buffer
    buffer: Vec<u8>,
    /// Current read position
    read_pos: usize,
    /// Total size of the file (if known)
    total_size: Option<u64>,
    /// Whether download is complete
    download_complete: bool,
    /// Download error if any
    error: Option<String>,
    /// Whether this source has been closed
    closed: bool,
}

/// A media source that supports progressive download and playback
pub struct StreamingMediaSource {
    state: Arc<Mutex<StreamingState>>,
    /// Condition variable to signal when new data is available
    data_available: Arc<Condvar>,
    /// Current position for this reader
    position: u64,
}

impl StreamingMediaSource {
    /// Create a new streaming media source
    pub fn new() -> Self {
        Self {
            state: Arc::new(Mutex::new(StreamingState {
                buffer: Vec::new(),
                read_pos: 0,
                total_size: None,
                download_complete: false,
                error: None,
                closed: false,
            })),
            data_available: Arc::new(Condvar::new()),
            position: 0,
        }
    }

    /// Start downloading from URL in a background thread
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

    /// Worker thread that downloads data
    fn download_worker(
        url: String,
        state: Arc<Mutex<StreamingState>>,
        data_available: Arc<Condvar>,
    ) -> Result<()> {
        log::info!("Starting streaming download from: {}", url);

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
            // Pre-allocate buffer for better performance
            state.buffer.reserve(len as usize);
        }

        // Download in chunks
        let mut reader = response.into_reader();
        let mut chunk_buffer = vec![0u8; 65536]; // 64KB chunks
        let mut total_downloaded = 0u64;

        loop {
            // Check if source was closed
            {
                let state = state.lock();
                if state.closed {
                    log::info!("Download cancelled (source closed)");
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
                total_downloaded += bytes_read as u64;

                // Log progress
                if total_downloaded % (1024 * 1024) < 65536 {
                    // Log every ~1MB
                    if let Some(total) = state.total_size {
                        let progress = (total_downloaded as f64 / total as f64) * 100.0;
                        log::info!(
                            "Downloaded: {:.2} MB / {:.2} MB ({:.1}%)",
                            total_downloaded as f64 / 1024.0 / 1024.0,
                            total as f64 / 1024.0 / 1024.0,
                            progress
                        );
                    } else {
                        log::info!(
                            "Downloaded: {:.2} MB",
                            total_downloaded as f64 / 1024.0 / 1024.0
                        );
                    }
                }
            }

            // Notify waiting readers that new data is available
            data_available.notify_all();
        }

        // Mark download as complete
        {
            let mut state = state.lock();
            state.download_complete = true;
            log::info!(
                "Download complete: {:.2} MB",
                total_downloaded as f64 / 1024.0 / 1024.0
            );
        }
        data_available.notify_all();

        Ok(())
    }

    /// Get the total size if known
    pub fn total_size(&self) -> Option<u64> {
        let state = self.state.lock();
        state.total_size
    }

    /// Check if download is complete
    pub fn is_download_complete(&self) -> bool {
        let state = self.state.lock();
        state.download_complete
    }

    /// Wait for data to be available at the current position
    /// Returns true if data is available, false if download completed without reaching position
    fn wait_for_data(&self, required_pos: usize, timeout: Duration) -> Result<bool> {
        let mut state = self.state.lock();

        let deadline = std::time::Instant::now() + timeout;

        loop {
            // Check if we have an error
            if let Some(ref error) = state.error {
                return Err(AudioError::NetworkError(error.clone()));
            }

            // Check if data is available at required position
            if required_pos < state.buffer.len() {
                return Ok(true);
            }

            // Check if download is complete
            if state.download_complete {
                return Ok(false);
            }

            // Wait for new data with timeout
            let remaining = deadline.saturating_duration_since(std::time::Instant::now());
            if remaining.is_zero() {
                return Err(AudioError::DecodingError(
                    "Timeout waiting for data".to_string(),
                ));
            }

            self.data_available.wait_for(&mut state, remaining);
        }
    }
}

impl Read for StreamingMediaSource {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        if buf.is_empty() {
            return Ok(0);
        }

        let required_end = self.position as usize + buf.len();

        // Wait for data to be available (with 30 second timeout)
        match self.wait_for_data(required_end, Duration::from_secs(30)) {
            Ok(true) => {
                // Data available
            }
            Ok(false) => {
                // Download complete but not enough data
                // Return what we have
            }
            Err(e) => {
                return Err(std::io::Error::new(
                    std::io::ErrorKind::Other,
                    format!("Download error: {}", e),
                ));
            }
        }

        // Read from buffer
        let state = self.state.lock();
        let start = self.position as usize;
        let available = state.buffer.len().saturating_sub(start);

        if available == 0 {
            // EOF
            return Ok(0);
        }

        let to_read = available.min(buf.len());
        buf[..to_read].copy_from_slice(&state.buffer[start..start + to_read]);

        drop(state);

        self.position += to_read as u64;
        Ok(to_read)
    }
}

impl Seek for StreamingMediaSource {
    fn seek(&mut self, pos: SeekFrom) -> std::io::Result<u64> {
        let state = self.state.lock();

        let new_pos = match pos {
            SeekFrom::Start(offset) => offset as i64,
            SeekFrom::Current(offset) => self.position as i64 + offset,
            SeekFrom::End(offset) => {
                if let Some(size) = state.total_size {
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

impl MediaSource for StreamingMediaSource {
    fn is_seekable(&self) -> bool {
        // We support seeking if we know the total size
        let state = self.state.lock();
        state.total_size.is_some()
    }

    fn byte_len(&self) -> Option<u64> {
        let state = self.state.lock();
        state.total_size
    }
}

impl Drop for StreamingMediaSource {
    fn drop(&mut self) {
        // Signal download thread to stop
        let mut state = self.state.lock();
        state.closed = true;
        drop(state);
        self.data_available.notify_all();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_streaming_source_creation() {
        let source = StreamingMediaSource::new();
        assert!(!source.is_download_complete());
        assert_eq!(source.total_size(), None);
    }
}
