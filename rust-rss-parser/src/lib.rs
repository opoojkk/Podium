use feed_rs::parser;
use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use serde::{Deserialize, Serialize};
use std::time::{SystemTime, UNIX_EPOCH};

/// Chapter information for podcast episodes
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Chapter {
    #[serde(rename = "startTimeMs")]
    pub start_time_ms: i64,
    pub title: String,
    #[serde(rename = "imageUrl")]
    pub image_url: Option<String>,
    pub url: Option<String>,
}

/// Individual episode from RSS feed
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RssEpisode {
    pub id: String,
    pub title: String,
    pub description: String,
    #[serde(rename = "audioUrl")]
    pub audio_url: String,
    #[serde(rename = "publishDate")]
    pub publish_date: i64, // Unix timestamp in milliseconds
    pub duration: Option<i64>,
    #[serde(rename = "imageUrl")]
    pub image_url: Option<String>,
    pub chapters: Vec<Chapter>,
}

/// Parsed podcast feed data
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PodcastFeed {
    pub id: String,
    pub title: String,
    pub description: String,
    #[serde(rename = "artworkUrl")]
    pub artwork_url: Option<String>,
    #[serde(rename = "feedUrl")]
    pub feed_url: String,
    #[serde(rename = "lastUpdated")]
    pub last_updated: i64, // Unix timestamp in milliseconds
    pub episodes: Vec<RssEpisode>,
}

/// Parse RSS feed from XML content
pub fn parse_rss(feed_url: &str, xml_content: &str) -> Result<PodcastFeed, String> {
    let feed = parser::parse(xml_content.as_bytes())
        .map_err(|e| format!("Failed to parse RSS feed: {}", e))?;

    // Get current timestamp in milliseconds
    let now_ms = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_err(|e| format!("System time error: {}", e))?
        .as_millis() as i64;

    // Extract feed metadata
    let title = feed.title.as_ref()
        .map(|t| t.content.to_string())
        .unwrap_or_else(|| "Untitled Podcast".to_string());

    let description = feed.description.as_ref()
        .map(|d| d.content.to_string())
        .unwrap_or_default();

    // Extract artwork URL from feed logo or icon
    let artwork_url = feed.logo.as_ref()
        .map(|logo| logo.uri.clone())
        .or_else(|| feed.icon.as_ref().map(|icon| icon.uri.clone()));

    // Generate feed ID from URL
    let feed_id = generate_id(feed_url);

    // Parse episodes
    let mut episodes = Vec::new();
    for entry in feed.entries {
        // Extract episode title
        let episode_title = entry.title.as_ref()
            .map(|t| t.content.to_string())
            .unwrap_or_else(|| "Untitled Episode".to_string());

        // Extract description
        let episode_description = entry.summary.as_ref()
            .map(|s| s.content.to_string())
            .or_else(|| entry.content.as_ref()
                .and_then(|c| c.body.as_ref())
                .map(|s| s.to_string()))
            .unwrap_or_default();

        // Find audio URL from media content or links
        let audio_url = entry.media.iter()
            .filter_map(|m| {
                m.content.iter()
                    .filter(|c| c.content_type.as_ref()
                        .map(|ct| ct.essence().ty == "audio")
                        .unwrap_or(false))
                    .filter_map(|c| c.url.as_ref())
                    .next()
            })
            .next()
            .map(|url| url.to_string())
            .or_else(|| {
                // Look for enclosure links with audio mime type
                entry.links.iter()
                    .find(|link| {
                        if let Some(rel) = &link.rel {
                            rel == "enclosure"
                        } else {
                            false
                        }
                    })
                    .map(|link| link.href.clone())
            });

        // Skip episodes without audio URL
        if audio_url.is_none() {
            continue;
        }

        // Extract publish date
        let publish_date = entry.published.or(entry.updated)
            .map(|dt| dt.timestamp_millis())
            .unwrap_or(now_ms);

        // Extract duration from media content
        let duration = entry.media.iter()
            .filter_map(|m| {
                m.content.iter()
                    .filter_map(|c| c.duration.as_ref())
                    .map(|d| d.as_secs() as i64 * 1000) // Convert to milliseconds
                    .next()
            })
            .next();

        // Extract image URL
        let image_url = entry.media.iter()
            .filter_map(|m| {
                m.thumbnails.iter()
                    .map(|t| t.image.uri.clone())
                    .next()
            })
            .next()
            .or_else(|| {
                // Look for image links
                entry.links.iter()
                    .find(|link| link.rel.as_ref().map(|r| r == "image").unwrap_or(false))
                    .map(|link| link.href.clone())
            });

        // Generate episode ID
        let episode_id = generate_id(&format!("{}_{}", feed_url, episode_title));

        let episode = RssEpisode {
            id: episode_id,
            title: episode_title,
            description: episode_description,
            audio_url: audio_url.unwrap(),
            publish_date,
            duration,
            image_url,
            chapters: Vec::new(), // Chapters would need custom parsing if available
        };

        episodes.push(episode);
    }

    Ok(PodcastFeed {
        id: feed_id,
        title,
        description,
        artwork_url,
        feed_url: feed_url.to_string(),
        last_updated: now_ms,
        episodes,
    })
}

/// Generate a simple hash-based ID from a string
fn generate_id(input: &str) -> String {
    use std::collections::hash_map::DefaultHasher;
    use std::hash::{Hash, Hasher};

    let mut hasher = DefaultHasher::new();
    input.hash(&mut hasher);
    format!("{:x}", hasher.finish())
}

/// JNI function to parse RSS feed from Kotlin/Java
#[no_mangle]
pub extern "system" fn Java_com_opoojkk_podium_data_rss_RustRssParser_parseRss(
    mut env: JNIEnv,
    _class: JClass,
    feed_url: JString,
    xml_content: JString,
) -> jstring {
    // Convert Java strings to Rust strings
    let feed_url: String = match env.get_string(&feed_url) {
        Ok(s) => s.into(),
        Err(e) => {
            let error_json = serde_json::json!({
                "error": format!("Failed to get feed_url: {}", e)
            });
            let error_str = env.new_string(error_json.to_string()).unwrap();
            return error_str.into_raw();
        }
    };

    let xml_content: String = match env.get_string(&xml_content) {
        Ok(s) => s.into(),
        Err(e) => {
            let error_json = serde_json::json!({
                "error": format!("Failed to get xml_content: {}", e)
            });
            let error_str = env.new_string(error_json.to_string()).unwrap();
            return error_str.into_raw();
        }
    };

    // Parse the RSS feed
    let result = match parse_rss(&feed_url, &xml_content) {
        Ok(feed) => {
            match serde_json::to_string(&feed) {
                Ok(json) => json,
                Err(e) => {
                    let error_json = serde_json::json!({
                        "error": format!("Failed to serialize feed: {}", e)
                    });
                    error_json.to_string()
                }
            }
        }
        Err(e) => {
            let error_json = serde_json::json!({
                "error": e
            });
            error_json.to_string()
        }
    };

    // Return result as Java string
    match env.new_string(result) {
        Ok(s) => s.into_raw(),
        Err(e) => {
            let error_json = serde_json::json!({
                "error": format!("Failed to create return string: {}", e)
            });
            let error_str = env.new_string(error_json.to_string()).unwrap();
            error_str.into_raw()
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_simple_rss() {
        let xml = r#"<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
    <channel>
        <title>Test Podcast</title>
        <description>A test podcast</description>
        <item>
            <title>Episode 1</title>
            <description>First episode</description>
            <enclosure url="https://example.com/ep1.mp3" type="audio/mpeg" />
            <pubDate>Mon, 01 Jan 2024 00:00:00 GMT</pubDate>
        </item>
    </channel>
</rss>"#;

        let result = parse_rss("https://example.com/feed.xml", xml);
        assert!(result.is_ok());

        let feed = result.unwrap();
        assert_eq!(feed.title, "Test Podcast");
        assert_eq!(feed.episodes.len(), 1);
        assert_eq!(feed.episodes[0].title, "Episode 1");
    }

    #[test]
    fn test_generate_id() {
        let id1 = generate_id("test");
        let id2 = generate_id("test");
        let id3 = generate_id("different");

        assert_eq!(id1, id2);
        assert_ne!(id1, id3);
    }
}
