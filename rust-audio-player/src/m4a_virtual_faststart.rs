// Virtual Fast Start for M4A files
// Dynamically relocates moov atom at runtime without file preprocessing

use crate::error::{AudioError, Result};
use parking_lot::Mutex;
use std::io::{Read, Seek, SeekFrom};
use std::sync::Arc;
use std::time::Duration;
use symphonia::core::io::MediaSource;

/// Maximum size to search for atoms in file header
const HEADER_SEARCH_SIZE: usize = 2 * 1024 * 1024; // 2MB (increased for special files)

/// Maximum size to search for atoms in file tail
const TAIL_SEARCH_SIZE: usize = 1 * 1024 * 1024; // 1MB

/// M4A atom structure
#[derive(Debug, Clone)]
struct Atom {
    atom_type: [u8; 4],
    offset: u64,
    size: u64,
}

/// Parse atom header at position
fn parse_atom_header(data: &[u8], offset: usize) -> Option<Atom> {
    if offset + 8 > data.len() {
        return None;
    }

    let size = u32::from_be_bytes([
        data[offset],
        data[offset + 1],
        data[offset + 2],
        data[offset + 3],
    ]) as u64;

    if size < 8 {
        return None;
    }

    let mut atom_type = [0u8; 4];
    atom_type.copy_from_slice(&data[offset + 4..offset + 8]);

    Some(Atom {
        atom_type,
        offset: offset as u64,
        size,
    })
}

/// Find specific atom in data
fn find_atom(data: &[u8], atom_type: &[u8; 4]) -> Option<Atom> {
    let mut pos = 0;

    while pos + 8 <= data.len() {
        if let Some(atom) = parse_atom_header(data, pos) {
            if &atom.atom_type == atom_type {
                return Some(atom);
            }

            let next_pos = pos + atom.size as usize;
            if next_pos <= pos || next_pos > data.len() {
                break;
            }
            pos = next_pos;
        } else {
            break;
        }
    }

    None
}

/// HTTP helper to fetch range
fn fetch_range(url: &str, start: u64, end: u64) -> Result<Vec<u8>> {
    let agent = ureq::AgentBuilder::new()
        .timeout_connect(Duration::from_secs(30))
        .timeout_read(Duration::from_secs(60))
        .user_agent("Mozilla/5.0 (compatible; RustAudioPlayer/1.0)")
        .redirects(10)
        .build();

    let range_header = format!("bytes={}-{}", start, end - 1);
    let response = agent
        .get(url)
        .set("Range", &range_header)
        .call()
        .map_err(|e| AudioError::NetworkError(format!("Range request failed: {}", e)))?;

    let mut data = Vec::new();
    response
        .into_reader()
        .read_to_end(&mut data)
        .map_err(|e| AudioError::IoError(format!("Failed to read response: {}", e)))?;

    Ok(data)
}

/// Virtual Fast Start source state
struct VirtualFastStartState {
    url: String,
    total_size: u64,
    /// Virtual file layout after moov relocation
    virtual_moov_offset: u64,
    virtual_moov_size: u64,
    moov_data: Vec<u8>,
    /// Real file offsets
    real_moov_offset: u64,
    real_mdat_offset: u64,
    /// Download cache
    cache: Vec<(u64, Vec<u8>)>, // (offset, data) pairs
}

impl VirtualFastStartState {
    /// Fetch data from URL with caching
    fn fetch_with_cache(&mut self, offset: u64, size: usize) -> Result<Vec<u8>> {
        // Check cache first
        for (cache_offset, cache_data) in &self.cache {
            if offset >= *cache_offset && offset + size as u64 <= *cache_offset + cache_data.len() as u64 {
                let start = (offset - *cache_offset) as usize;
                let end = start + size;
                return Ok(cache_data[start..end].to_vec());
            }
        }

        // Fetch from network
        let end = (offset + size as u64).min(self.total_size);
        let data = fetch_range(&self.url, offset, end)?;

        // Add to cache (keep cache simple, max 10 entries)
        if self.cache.len() > 10 {
            self.cache.remove(0);
        }
        self.cache.push((offset, data.clone()));

        Ok(data)
    }

    /// Map virtual offset to real offset
    fn map_virtual_to_real(&self, virtual_offset: u64) -> (u64, bool) {
        // Virtual layout: [ftyp][moov][mdat...]
        // Real layout: [ftyp][mdat...][moov]

        if virtual_offset < self.virtual_moov_offset {
            // Before moov: direct mapping (ftyp area)
            (virtual_offset, false)
        } else if virtual_offset < self.virtual_moov_offset + self.virtual_moov_size {
            // Inside moov: return from cached moov
            (virtual_offset - self.virtual_moov_offset, true)
        } else {
            // After moov: map to mdat (skip ftyp size)
            let offset_in_mdat = virtual_offset - self.virtual_moov_offset - self.virtual_moov_size;
            (self.real_mdat_offset + offset_in_mdat, false)
        }
    }

    /// Read at virtual offset
    fn read_at(&mut self, virtual_offset: u64, buf: &mut [u8]) -> Result<usize> {
        let (real_offset, from_moov) = self.map_virtual_to_real(virtual_offset);

        if from_moov {
            // Read from cached moov data
            let moov_offset = real_offset as usize;
            if moov_offset >= self.moov_data.len() {
                return Ok(0);
            }

            let available = self.moov_data.len() - moov_offset;
            let to_read = available.min(buf.len());
            buf[..to_read].copy_from_slice(&self.moov_data[moov_offset..moov_offset + to_read]);
            Ok(to_read)
        } else {
            // Fetch from network
            let data = self.fetch_with_cache(real_offset, buf.len())?;
            let to_read = data.len().min(buf.len());
            buf[..to_read].copy_from_slice(&data[..to_read]);
            Ok(to_read)
        }
    }
}

/// Virtual Fast Start M4A source
pub struct VirtualFastStartSource {
    state: Arc<Mutex<VirtualFastStartState>>,
    position: u64,
}

impl VirtualFastStartSource {
    /// Create virtual Fast Start source by relocating moov atom
    pub fn new(url: String) -> Result<Self> {
        log::info!("Creating virtual Fast Start source for: {}", url);

        // Step 1: Get file size
        let agent = ureq::AgentBuilder::new()
            .timeout_connect(Duration::from_secs(30))
            .redirects(10)
            .build();

        let response = agent
            .head(&url)
            .call()
            .map_err(|e| AudioError::NetworkError(format!("HEAD request failed: {}", e)))?;

        let total_size = response
            .header("Content-Length")
            .and_then(|s| s.parse::<u64>().ok())
            .ok_or_else(|| AudioError::NetworkError("Content-Length missing".to_string()))?;

        log::info!("File size: {} bytes ({:.2} MB)", total_size, total_size as f64 / 1024.0 / 1024.0);

        // Step 2: Fetch file header (increased to 2MB for special files)
        let header_size = HEADER_SEARCH_SIZE.min(total_size as usize) as u64;
        let header_data = fetch_range(&url, 0, header_size)?;
        log::info!("Fetched {} bytes from file header for atom search", header_data.len());

        // Note: We trust SmartM4ASource has already checked for Fast Start
        // This function should only be called for Non-Fast Start files

        // Step 3: Find ftyp and mdat in header
        let ftyp = find_atom(&header_data, b"ftyp").ok_or_else(|| {
            log::error!("Failed to find ftyp atom in first {} bytes", header_data.len());
            AudioError::UnsupportedFormat("No ftyp atom found in file header".to_string())
        })?;

        let mdat = find_atom(&header_data, b"mdat").ok_or_else(|| {
            log::error!("Failed to find mdat atom in first {} bytes", header_data.len());
            log::error!("File may have unusual structure (mdat very far from start)");
            AudioError::UnsupportedFormat(format!(
                "No mdat atom found in first {} bytes. File structure may be incompatible.",
                header_data.len()
            ))
        })?;

        log::info!("Found ftyp at offset {}, size {}", ftyp.offset, ftyp.size);
        log::info!("Found mdat at offset {}, size {}", mdat.offset, mdat.size);

        // Step 4: Fetch file tail to find moov
        let tail_start = total_size.saturating_sub(TAIL_SEARCH_SIZE as u64);
        let tail_data = fetch_range(&url, tail_start, total_size)?;

        let moov = find_atom(&tail_data, b"moov").ok_or_else(|| {
            AudioError::UnsupportedFormat("No moov atom found in file tail".to_string())
        })?;

        let real_moov_offset = tail_start + moov.offset;
        log::info!(
            "Found moov at offset {}, size {}",
            real_moov_offset,
            moov.size
        );

        // Step 5: Fetch complete moov atom
        let moov_data = fetch_range(&url, real_moov_offset, real_moov_offset + moov.size)?;

        log::info!("Successfully fetched moov atom ({} bytes)", moov_data.len());

        // Step 6: Calculate virtual layout
        // Virtual: [ftyp][moov][mdat...]
        let virtual_moov_offset = ftyp.size;
        let virtual_moov_size = moov.size;

        log::info!("Virtual layout created:");
        log::info!("  ftyp: 0 - {}", ftyp.size);
        log::info!(
            "  moov: {} - {}",
            virtual_moov_offset,
            virtual_moov_offset + virtual_moov_size
        );
        log::info!("  mdat: {} - ...", virtual_moov_offset + virtual_moov_size);

        let state = VirtualFastStartState {
            url,
            total_size,
            virtual_moov_offset,
            virtual_moov_size,
            moov_data,
            real_moov_offset,
            real_mdat_offset: mdat.offset,
            cache: Vec::new(),
        };

        Ok(Self {
            state: Arc::new(Mutex::new(state)),
            position: 0,
        })
    }
}

impl Read for VirtualFastStartSource {
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
                format!("Read error: {}", e),
            )),
        }
    }
}

impl Seek for VirtualFastStartSource {
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

impl MediaSource for VirtualFastStartSource {
    fn is_seekable(&self) -> bool {
        true
    }

    fn byte_len(&self) -> Option<u64> {
        let state = self.state.lock();
        Some(state.total_size)
    }
}

/// Convenience function
pub fn create_virtual_faststart_source(url: String) -> Result<Box<dyn MediaSource>> {
    Ok(Box::new(VirtualFastStartSource::new(url)?))
}
