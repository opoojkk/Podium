// PCM audio ring buffer for smooth playback

use parking_lot::Mutex;
use std::sync::Arc;

/// Thread-safe audio ring buffer for smooth audio playback
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

        if to_write == 0 {
            return 0;
        }

        // Optimize: use slice copy instead of element-by-element
        // Handle wrap-around in two chunks if necessary
        let write_end = self.write_pos + to_write;
        if write_end <= self.size {
            // No wrap-around: single contiguous copy
            self.buffer[self.write_pos..write_end].copy_from_slice(&data[..to_write]);
            self.write_pos = write_end % self.size;
        } else {
            // Wrap-around: copy in two chunks
            let first_chunk = self.size - self.write_pos;
            let second_chunk = to_write - first_chunk;

            self.buffer[self.write_pos..self.size].copy_from_slice(&data[..first_chunk]);
            self.buffer[..second_chunk].copy_from_slice(&data[first_chunk..to_write]);
            self.write_pos = second_chunk;
        }

        to_write
    }

    pub fn read(&mut self, output: &mut [f32]) -> usize {
        let available = self.available_read();
        let to_read = output.len().min(available);

        if to_read == 0 {
            return 0;
        }

        // Optimize: use slice copy instead of element-by-element
        // Handle wrap-around in two chunks if necessary
        let read_end = self.read_pos + to_read;
        if read_end <= self.size {
            // No wrap-around: single contiguous copy
            output[..to_read].copy_from_slice(&self.buffer[self.read_pos..read_end]);
            self.read_pos = read_end % self.size;
        } else {
            // Wrap-around: copy in two chunks
            let first_chunk = self.size - self.read_pos;
            let second_chunk = to_read - first_chunk;

            output[..first_chunk].copy_from_slice(&self.buffer[self.read_pos..self.size]);
            output[first_chunk..to_read].copy_from_slice(&self.buffer[..second_chunk]);
            self.read_pos = second_chunk;
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

    /// Resize the ring buffer to a new size
    /// This clears all existing data and resets positions
    pub fn resize(&mut self, new_size: usize) {
        if new_size != self.size {
            self.buffer = vec![0.0; new_size];
            self.size = new_size;
            self.write_pos = 0;
            self.read_pos = 0;
        }
    }

    /// Get current buffer size
    pub fn size(&self) -> usize {
        self.size
    }

    /// Get buffer fullness as a percentage (0.0 to 1.0)
    pub fn fullness(&self) -> f32 {
        let used = self.available_read();
        used as f32 / self.size as f32
    }
}

/// Thread-safe wrapper for AudioRingBuffer
#[derive(Clone)]
pub struct SharedRingBuffer {
    inner: Arc<Mutex<AudioRingBuffer>>,
}

impl SharedRingBuffer {
    pub fn new(size: usize) -> Self {
        Self {
            inner: Arc::new(Mutex::new(AudioRingBuffer::new(size))),
        }
    }

    pub fn write(&self, data: &[f32]) -> usize {
        self.inner.lock().write(data)
    }

    pub fn read(&self, output: &mut [f32]) -> usize {
        self.inner.lock().read(output)
    }

    pub fn available_write(&self) -> usize {
        self.inner.lock().available_write()
    }

    pub fn available_read(&self) -> usize {
        self.inner.lock().available_read()
    }

    pub fn clear(&self) {
        self.inner.lock().clear()
    }

    pub fn resize(&self, new_size: usize) {
        self.inner.lock().resize(new_size)
    }

    pub fn size(&self) -> usize {
        self.inner.lock().size()
    }

    pub fn fullness(&self) -> f32 {
        self.inner.lock().fullness()
    }
}
