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

/// Download audio from URL with progressive buffering
/// Downloads enough data to start playback, then continues in background
/// Returns the path to the temporary file
pub fn download_with_prebuffer(url: &str, dest_path: &str) -> Result<()> {
    log::info!("Starting download from: {}", url);

    // Create HTTP agent with proper configuration
    let agent = create_http_agent();

    // Make HTTP GET request with retries
    let response = retry_request(&agent, url, 3)?;

    let content_length = response.header("Content-Length")
        .and_then(|s| s.parse::<u64>().ok())
        .unwrap_or(0);

    log::info!("Content length: {} bytes", content_length);

    // Calculate prebuffer size: min 5MB or 30% of file, max 15MB
    let prebuffer_size = if content_length > 0 {
        let thirty_percent = (content_length as f64 * 0.3) as u64;
        thirty_percent.max(5 * 1024 * 1024).min(15 * 1024 * 1024)
    } else {
        5 * 1024 * 1024 // Default 5MB
    };

    log::info!("Prebuffer target: {} bytes ({:.1}%)",
        prebuffer_size,
        if content_length > 0 { (prebuffer_size as f64 / content_length as f64) * 100.0 } else { 0.0 }
    );

    // Open destination file
    let mut file = File::create(dest_path)
        .map_err(|e| AudioError::IoError(format!("Failed to create temp file: {}", e)))?;

    // Read and write data
    let mut reader = response.into_reader();
    let mut buffer = vec![0u8; 8192];
    let mut total_downloaded = 0u64;

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
        if total_downloaded % (1024 * 1024) == 0 || total_downloaded >= prebuffer_size {
            log::info!("Downloaded: {} MB", total_downloaded / (1024 * 1024));
        }

        // Return when prebuffer is complete
        if total_downloaded >= prebuffer_size {
            log::info!("Prebuffer complete: {} bytes downloaded", total_downloaded);

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
                                let mut buffer = vec![0u8; 8192];
                                let mut bg_downloaded = already_downloaded;
                                loop {
                                    match std::io::Read::read(&mut reader, &mut buffer) {
                                        Ok(0) => break, // EOF
                                        Ok(bytes_read) => {
                                            if file.write_all(&buffer[..bytes_read]).is_err() {
                                                break;
                                            }
                                            bg_downloaded += bytes_read as u64;
                                            if bg_downloaded % (1024 * 1024) == 0 {
                                                log::info!("Background download: {} MB total", bg_downloaded / (1024 * 1024));
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

    // If file is smaller than prebuffer size, download is complete
    log::info!("Download complete (file smaller than prebuffer): {} bytes", total_downloaded);
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
