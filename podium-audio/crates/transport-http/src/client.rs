// HTTP client configuration and utilities

use podium_core::{AudioError, Result};
use std::time::Duration;

/// Create a configured HTTP agent with proper timeouts and settings
pub fn create_http_agent() -> ureq::Agent {
    ureq::AgentBuilder::new()
        .timeout_connect(Duration::from_secs(30))
        .timeout_read(Duration::from_secs(60))
        .timeout_write(Duration::from_secs(30))
        .user_agent("Mozilla/5.0 (compatible; PodiumAudioPlayer/2.0)")
        .redirects(10)
        .build()
}

/// HTTP client wrapper
pub struct HttpClient {
    agent: ureq::Agent,
}

impl HttpClient {
    pub fn new() -> Self {
        Self {
            agent: create_http_agent(),
        }
    }

    pub fn get(&self, url: &str) -> Result<ureq::Response> {
        self.agent
            .get(url)
            .call()
            .map_err(|e| AudioError::NetworkError(format!("HTTP GET failed: {}", e)))
    }

    pub fn get_with_range(&self, url: &str, start: u64, end: Option<u64>) -> Result<ureq::Response> {
        let range = match end {
            Some(e) => format!("bytes={}-{}", start, e),
            None => format!("bytes={}-", start),
        };

        self.agent
            .get(url)
            .set("Range", &range)
            .call()
            .map_err(|e| AudioError::NetworkError(format!("HTTP Range GET failed: {}", e)))
    }

    pub fn head(&self, url: &str) -> Result<ureq::Response> {
        self.agent
            .head(url)
            .call()
            .map_err(|e| AudioError::NetworkError(format!("HTTP HEAD failed: {}", e)))
    }
}

impl Default for HttpClient {
    fn default() -> Self {
        Self::new()
    }
}

/// Retry a request with exponential backoff
pub fn retry_request(agent: &ureq::Agent, url: &str, max_retries: u32) -> Result<ureq::Response> {
    let mut last_error = None;

    for attempt in 0..=max_retries {
        match agent.get(url).call() {
            Ok(response) => return Ok(response),
            Err(e) => {
                last_error = Some(e);
                if attempt < max_retries {
                    let delay = Duration::from_millis(500 * 2u64.pow(attempt));
                    log::warn!("Request failed (attempt {}), retrying after {:?}", attempt + 1, delay);
                    std::thread::sleep(delay);
                }
            }
        }
    }

    Err(AudioError::NetworkError(format!(
        "Request failed after {} attempts: {:?}",
        max_retries + 1,
        last_error
    )))
}

/// Check if URL is for M4A/MP4 format (needs special handling)
pub fn is_m4a_format(url: &str) -> bool {
    let url_lower = url.to_lowercase();
    url_lower.contains(".m4a") || url_lower.contains(".mp4") || url_lower.contains(".m4b")
}
