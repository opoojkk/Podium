// Network source buffer that bridges HTTP transport to Symphonia MediaSource

use parking_lot::Mutex;
use podium_core::Result;
use podium_transport_http::HttpRangeSource;
use std::io::{Read, Seek, SeekFrom};
use std::sync::Arc;
use symphonia::core::io::MediaSource;

/// Network source that provides a MediaSource interface for HTTP streaming
pub struct NetworkSource {
    inner: Box<dyn MediaSource>,
}

impl NetworkSource {
    /// Create from HTTP Range source
    pub fn from_http_range(url: String) -> Result<Self> {
        let source = HttpRangeSource::new(url)?;
        Ok(Self {
            inner: Box::new(source),
        })
    }

    /// Create from a generic MediaSource
    pub fn from_media_source(source: Box<dyn MediaSource>) -> Self {
        Self { inner: source }
    }
}

impl MediaSource for NetworkSource {
    fn is_seekable(&self) -> bool {
        self.inner.is_seekable()
    }

    fn byte_len(&self) -> Option<u64> {
        self.inner.byte_len()
    }
}

impl Read for NetworkSource {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        self.inner.read(buf)
    }
}

impl Seek for NetworkSource {
    fn seek(&mut self, pos: SeekFrom) -> std::io::Result<u64> {
        self.inner.seek(pos)
    }
}

/// Streaming source that buffers data progressively
pub struct StreamingSource {
    buffer: Arc<Mutex<Vec<u8>>>,
    position: usize,
    complete: Arc<Mutex<bool>>,
}

impl StreamingSource {
    pub fn new() -> Self {
        Self {
            buffer: Arc::new(Mutex::new(Vec::new())),
            position: 0,
            complete: Arc::new(Mutex::new(false)),
        }
    }

    /// Write data to the buffer (called by download thread)
    pub fn write(&self, data: &[u8]) {
        let mut buffer = self.buffer.lock();
        buffer.extend_from_slice(data);
    }

    /// Mark the source as complete
    pub fn set_complete(&self) {
        *self.complete.lock() = true;
    }

    /// Check if download is complete
    pub fn is_complete(&self) -> bool {
        *self.complete.lock()
    }

    /// Get current buffer size
    pub fn buffer_len(&self) -> usize {
        self.buffer.lock().len()
    }
}

impl Default for StreamingSource {
    fn default() -> Self {
        Self::new()
    }
}

impl Read for StreamingSource {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        let buffer = self.buffer.lock();
        let available = buffer.len().saturating_sub(self.position);

        if available == 0 {
            if *self.complete.lock() {
                return Ok(0); // EOF
            } else {
                return Err(std::io::Error::new(
                    std::io::ErrorKind::WouldBlock,
                    "Waiting for more data",
                ));
            }
        }

        let to_read = buf.len().min(available);
        buf[..to_read].copy_from_slice(&buffer[self.position..self.position + to_read]);
        self.position += to_read;

        Ok(to_read)
    }
}

impl Seek for StreamingSource {
    fn seek(&mut self, pos: SeekFrom) -> std::io::Result<u64> {
        let buffer = self.buffer.lock();
        let buffer_len = buffer.len() as u64;

        let new_pos = match pos {
            SeekFrom::Start(pos) => pos,
            SeekFrom::Current(offset) => {
                if offset >= 0 {
                    self.position as u64 + offset as u64
                } else {
                    (self.position as u64).saturating_sub((-offset) as u64)
                }
            }
            SeekFrom::End(offset) => {
                if offset >= 0 {
                    buffer_len + offset as u64
                } else {
                    buffer_len.saturating_sub((-offset) as u64)
                }
            }
        };

        self.position = new_pos as usize;
        Ok(new_pos)
    }
}

impl MediaSource for StreamingSource {
    fn is_seekable(&self) -> bool {
        true
    }

    fn byte_len(&self) -> Option<u64> {
        if *self.complete.lock() {
            Some(self.buffer.lock().len() as u64)
        } else {
            None
        }
    }
}
