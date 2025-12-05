// Progressive download with prebuffering

use crate::client::{create_http_agent, is_m4a_format, retry_request};
use podium_core::{AudioError, Result};
use std::fs::File;
use std::io::Write;
use std::thread;

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

    let content_length = response
        .header("Content-Length")
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
        log::info!(
            "Prebuffer target: {} bytes ({:.1}%)",
            prebuffer_size,
            if content_length > 0 {
                (prebuffer_size as f64 / content_length as f64) * 100.0
            } else {
                0.0
            }
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
                continue_download_in_background(url_owned, dest_owned, already_downloaded)
            });

            return Ok(());
        }
    }

    log::info!("Download complete: {} bytes", total_downloaded);
    Ok(())
}

fn continue_download_in_background(url: String, dest_path: String, start_from: u64) {
    log::info!("Background download continuing from byte {}", start_from);

    let agent = create_http_agent();

    match agent
        .get(&url)
        .set("Range", &format!("bytes={}-", start_from))
        .call()
    {
        Ok(response) => {
            // Validate that server actually honored the Range request
            let status = response.status();
            let content_range = response.header("Content-Range");

            if status != 206 {
                log::error!(
                    "Background download failed: Server returned {} instead of 206 Partial Content. \
                    Range requests not supported. This would corrupt the file.",
                    status
                );
                log::warn!(
                    "Falling back: You may need to restart the full download or implement fallback logic."
                );
                return;
            }

            if content_range.is_none() {
                log::error!(
                    "Background download failed: Server returned 206 but no Content-Range header. \
                    Cannot verify correct range."
                );
                return;
            }

            // Verify Content-Range starts at the expected position
            if let Some(range_header) = content_range {
                if !range_header.starts_with(&format!("bytes {}-", start_from)) {
                    log::error!(
                        "Background download failed: Content-Range '{}' doesn't match requested start position {}",
                        range_header,
                        start_from
                    );
                    return;
                }
            }

            log::info!("Range request validated, continuing download from byte {}", start_from);

            let mut reader = response.into_reader();
            match std::fs::OpenOptions::new().append(true).open(&dest_path) {
                Ok(mut file) => {
                    let mut buffer = vec![0u8; 65536];
                    let mut bg_downloaded = start_from;
                    loop {
                        match std::io::Read::read(&mut reader, &mut buffer) {
                            Ok(0) => break,
                            Ok(bytes_read) => {
                                if file.write_all(&buffer[..bytes_read]).is_err() {
                                    break;
                                }
                                bg_downloaded += bytes_read as u64;
                                let bg_mb = bg_downloaded / (1024 * 1024);
                                if bg_mb % 5 == 0 {
                                    log::info!("Background download: {} MB total", bg_mb);
                                }
                            }
                            Err(_) => break,
                        }
                    }
                    log::info!("Background download complete: {} bytes", bg_downloaded);
                }
                Err(e) => log::error!("Failed to open file for append: {}", e),
            }
        }
        Err(e) => log::error!("Background download request failed: {}", e),
    }
}
