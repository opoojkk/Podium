// HTTP utilities for streaming audio
// Shared across all platforms

use crate::error::{AudioError, Result};
use std::fs::File;
use std::io::Write;
use std::thread;
use std::time::Duration;

/// Create a configured HTTP agent with proper timeouts and settings
fn create_http_agent() -> ureq::Agent {
    ureq::AgentBuilder::new()
        .timeout_connect(Duration::from_secs(30))
        .timeout_read(Duration::from_secs(60))
        .timeout_write(Duration::from_secs(30))
        .user_agent("Mozilla/5.0 (compatible; RustAudioPlayer/1.0)")
        .redirects(10)
        .build()
}

/// Check if URL is for M4A/MP4 format (needs full download)
fn is_m4a_format(url: &str) -> bool {
    let url_lower = url.to_lowercase();
    url_lower.contains(".m4a") || url_lower.contains(".mp4") || url_lower.contains(".m4b")
}

/// Download audio from URL with progressive buffering
/// For M4A/MP4 files, downloads the complete file since metadata may be at the end
/// For other formats, downloads enough to start playback then continues in background
/// Returns the path to the temporary file
pub fn download_with_prebuffer(url: &str, dest_path: &str) -> Result<()> {
    log::info!("Starting download from: {}", url);

    // Check if this is M4A format
    let needs_full_download = is_m4a_format(url);
    if needs_full_download {
        log::info!("M4A format detected - will download complete file");
    }

    // Create HTTP agent with proper configuration
    let agent = create_http_agent();

    // Make HTTP GET request with retries
    let response = retry_request(&agent, url, 3)?;

    let content_length = response.header("Content-Length")
        .and_then(|s| s.parse::<u64>().ok())
        .unwrap_or(0);

    log::info!("Content length: {} bytes", content_length);

    // Calculate prebuffer size for non-M4A formats: min 5MB or 30% of file, max 15MB
    let prebuffer_size = if !needs_full_download && content_length > 0 {
        let thirty_percent = (content_length as f64 * 0.3) as u64;
        thirty_percent.max(5 * 1024 * 1024).min(15 * 1024 * 1024)
    } else if !needs_full_download {
        5 * 1024 * 1024 // Default 5MB for unknown size
    } else {
        u64::MAX // M4A needs full download
    };

    if !needs_full_download {
        log::info!("Prebuffer target: {} bytes ({:.1}%)",
            prebuffer_size,
            if content_length > 0 { (prebuffer_size as f64 / content_length as f64) * 100.0 } else { 0.0 }
        );
    }

    // Open destination file
    let mut file = File::create(dest_path)
        .map_err(|e| AudioError::IoError(format!("Failed to create temp file: {}", e)))?;

    // Read and write data
    let mut reader = response.into_reader();
    let mut buffer = vec![0u8; 65536]; // 64KB buffer
    let mut total_downloaded = 0u64;
    let mut last_log_mb = 0u64;

    loop {
        let bytes_read = std::io::Read::read(&mut reader, &mut buffer)
            .map_err(|e| AudioError::NetworkError(format!("Download failed: {}", e)))?;

        if bytes_read == 0 {
            break; // EOF
        }

        file.write_all(&buffer[..bytes_read])
            .map_err(|e| AudioError::IoError(format!("Write failed: {}", e)))?;

        total_downloaded += bytes_read as u64;

        // Log progress every MB
        let current_mb = total_downloaded / (1024 * 1024);
        if current_mb > last_log_mb {
            let progress = if content_length > 0 {
                format!("{:.1}%", (total_downloaded as f64 / content_length as f64) * 100.0)
            } else {
                "unknown".to_string()
            };
            log::info!("Downloaded: {} MB ({})", current_mb, progress);
            last_log_mb = current_mb;
        }

        // For non-M4A formats: return when prebuffer is complete and spawn background download
        if !needs_full_download && total_downloaded >= prebuffer_size {
            log::info!("Prebuffer complete: {} bytes downloaded", total_downloaded);

            // Flush before spawning background thread
            file.flush()
                .map_err(|e| AudioError::IoError(format!("Failed to flush file: {}", e)))?;

            // Spawn background thread to continue downloading
            let url_owned = url.to_string();
            let dest_owned = dest_path.to_string();
            let already_downloaded = total_downloaded;

            thread::spawn(move || {
                log::info!("Background download continuing from byte {}", already_downloaded);

                // Create new agent for background thread
                let bg_agent = create_http_agent();

                // Continue downloading in background with Range request
                match bg_agent.get(&url_owned)
                    .set("Range", &format!("bytes={}-", already_downloaded))
                    .call()
                {
                    Ok(response) => {
                        let mut reader = response.into_reader();
                        match std::fs::OpenOptions::new()
                            .append(true)
                            .open(&dest_owned)
                        {
                            Ok(mut file) => {
                                let mut buffer = vec![0u8; 65536];
                                let mut bg_downloaded = already_downloaded;
                                loop {
                                    match std::io::Read::read(&mut reader, &mut buffer) {
                                        Ok(0) => break, // EOF
                                        Ok(bytes_read) => {
                                            if file.write_all(&buffer[..bytes_read]).is_err() {
                                                break;
                                            }
                                            bg_downloaded += bytes_read as u64;
                                            let bg_mb = bg_downloaded / (1024 * 1024);
                                            if bg_mb % 5 == 0 && bg_mb * 1024 * 1024 <= bg_downloaded && bg_downloaded < bg_mb * 1024 * 1024 + 65536 {
                                                log::info!("Background download: {} MB total", bg_mb);
                                            }
                                        }
                                        Err(_) => break,
                                    }
                                }
                                log::info!("Background download complete: {} bytes total", bg_downloaded);
                            }
                            Err(e) => log::error!("Failed to open file for appending: {}", e),
                        }
                    }
                    Err(e) => log::error!("Background download request failed: {}", e),
                }
            });

            return Ok(());
        }
    }

    // Flush to ensure all data is written
    file.flush()
        .map_err(|e| AudioError::IoError(format!("Failed to flush file: {}", e)))?;

    // Full download complete (either M4A or file smaller than prebuffer)
    log::info!("Download complete: {} bytes ({})",
        total_downloaded,
        if content_length > 0 && total_downloaded == content_length {
            "verified"
        } else if content_length > 0 {
            "size mismatch!"
        } else {
            "no size check"
        }
    );

    Ok(())
}

/// Retry HTTP request with exponential backoff
fn retry_request(agent: &ureq::Agent, url: &str, max_retries: u32) -> Result<ureq::Response> {
    let mut last_error = None;

    for attempt in 0..=max_retries {
        if attempt > 0 {
            let delay = Duration::from_millis(500 * (1 << (attempt - 1))); // 500ms, 1s, 2s
            log::info!("Retry attempt {} after {:?}", attempt, delay);
            thread::sleep(delay);
        }

        match agent.get(url).call() {
            Ok(response) => {
                if attempt > 0 {
                    log::info!("Request succeeded on attempt {}", attempt + 1);
                }
                return Ok(response);
            }
            Err(e) => {
                log::warn!("Request attempt {} failed: {}", attempt + 1, e);
                last_error = Some(e);
            }
        }
    }

    Err(AudioError::NetworkError(format!(
        "HTTP request failed after {} attempts: {}",
        max_retries + 1,
        last_error.map(|e| e.to_string()).unwrap_or_else(|| "unknown error".to_string())
    )))
}

/// Get a temporary file path for caching a URL
pub fn get_temp_cache_path(url: &str) -> String {
    // Create a hash of the URL for a unique filename
    use std::collections::hash_map::DefaultHasher;
    use std::hash::{Hash, Hasher};

    let mut hasher = DefaultHasher::new();
    url.hash(&mut hasher);
    let hash = hasher.finish();

    // Use system temp directory
    let temp_dir = std::env::temp_dir();
    format!("{}/rust_audio_stream_{:x}.tmp", temp_dir.display(), hash)
}
