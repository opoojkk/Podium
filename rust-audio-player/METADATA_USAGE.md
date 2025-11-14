# Audio Metadata Extraction Guide

## Feature Overview

The rust-audio-player now supports comprehensive audio metadata extraction, including:

### Basic Metadata
- ✅ **Duration** (duration_ms) - Precise to milliseconds
- ✅ **Sample Rate** (sample_rate) - e.g., 44100Hz, 48000Hz
- ✅ **Channels** (channels) - Mono/Stereo/Multi-channel
- ✅ **Bitrate** (bitrate_bps) - Average bitrate
- ✅ **Codec** (codec) - MP3, AAC, Opus, Vorbis, FLAC, etc.

### Audio Quality Parameters
- ✅ **Bit Depth** (bit_depth) - e.g., 16, 24, 32 bits
- ✅ **VBR Detection** (is_vbr) - Variable bitrate detection
- ✅ **Instantaneous Bitrate** (instantaneous_bitrate_bps) - Instantaneous bitrate for VBR files

### ID3/Metadata Tags
- ✅ **Title** (title) - Song/podcast title
- ✅ **Artist** (artist) - Performer
- ✅ **Album** (album) - Album name
- ✅ **Album Artist** (album_artist)
- ✅ **Track Number** (track_number/track_total)
- ✅ **Disc Number** (disc_number/disc_total)
- ✅ **Release Date** (date)
- ✅ **Genre** (genre)
- ✅ **Composer** (composer)
- ✅ **Comment** (comment)
- ✅ **Lyrics** (lyrics)
- ✅ **Copyright** (copyright)
- ✅ **Encoder** (encoder)
- ✅ **Publisher** (publisher)
- ✅ **ISRC** (International Standard Recording Code)
- ✅ **Language** (language)
- ✅ **Custom Tags** (custom_tags)

### Cover Art
- ✅ **Cover Data** (cover_art.data) - Image byte data
- ✅ **MIME Type** (cover_art.mime_type) - e.g., "image/jpeg", "image/png"
- ✅ **Picture Description** (cover_art.description)
- ✅ **Picture Type** (cover_art.picture_type)

### Chapter Information (Important for Podcasts)
- ⚠️ **Chapter Markers** (chapters) - Framework ready, awaiting format-specific implementation

## Rust API Usage Examples

```rust
use rust_audio_player::decoder::AudioDecoder;

// Load audio file
let decoder = AudioDecoder::from_file("path/to/audio.mp3")?;

// Access basic format information
println!("Duration: {} ms", decoder.metadata.format_info.duration_ms);
println!("Sample Rate: {} Hz", decoder.metadata.format_info.sample_rate);
println!("Channels: {}", decoder.metadata.format_info.channels);
println!("Codec: {}", decoder.metadata.format_info.codec);

if let Some(bitrate) = decoder.metadata.format_info.bitrate_bps {
    println!("Bitrate: {} kbps", bitrate / 1000);
}

// Access audio quality parameters
if let Some(bit_depth) = decoder.metadata.quality.bit_depth {
    println!("Bit Depth: {} bit", bit_depth);
}

if decoder.metadata.quality.is_vbr {
    println!("This is a VBR file");
}

// Access tag information
if let Some(title) = &decoder.metadata.tags.title {
    println!("Title: {}", title);
}

if let Some(artist) = &decoder.metadata.tags.artist {
    println!("Artist: {}", artist);
}

if let Some(album) = &decoder.metadata.tags.album {
    println!("Album: {}", album);
}

if let Some(lyrics) = &decoder.metadata.tags.lyrics {
    println!("Lyrics:\n{}", lyrics);
}

// Get cover art
if let Some(cover) = decoder.get_cover_art() {
    println!("Cover Format: {}", cover.mime_type);
    println!("Cover Size: {} bytes", cover.data.len());

    // Save cover to file
    std::fs::write("cover.jpg", &cover.data)?;
}

// Get metadata summary
println!("\nMetadata Summary:\n{}", decoder.metadata.summary());
```

## Android JNI API Usage Examples

### Kotlin/Java Code

```kotlin
// 1. Create player and load file
val player = RustAudioPlayer()
player.loadFile("/path/to/audio.mp3")

// 2. Get metadata (JSON format)
val metadataJson = player.getMetadataJson()
val metadata = JSONObject(metadataJson)

// 3. Parse basic information
val formatInfo = metadata.getJSONObject("formatInfo")
val durationMs = formatInfo.getLong("durationMs")
val sampleRate = formatInfo.getInt("sampleRate")
val channels = formatInfo.getInt("channels")
val codec = formatInfo.getString("codec")
val bitrateBps = formatInfo.optInt("bitrateBps", -1)

Log.i("Metadata", "Duration: ${durationMs}ms")
Log.i("Metadata", "Sample Rate: ${sampleRate}Hz")
Log.i("Metadata", "Channels: $channels")
Log.i("Metadata", "Codec: $codec")

if (bitrateBps > 0) {
    Log.i("Metadata", "Bitrate: ${bitrateBps / 1000}kbps")
}

// 4. Parse audio quality
val quality = metadata.getJSONObject("quality")
val bitDepth = quality.optInt("bitDepth", -1)
val isVbr = quality.getBoolean("isVbr")

if (bitDepth > 0) {
    Log.i("Metadata", "Bit Depth: ${bitDepth}bit")
}

if (isVbr) {
    Log.i("Metadata", "VBR encoding")
}

// 5. Parse tag information
val tags = metadata.getJSONObject("tags")
val title = tags.optString("title", null)
val artist = tags.optString("artist", null)
val album = tags.optString("album", null)
val genre = tags.optString("genre", null)
val lyrics = tags.optString("lyrics", null)

title?.let { Log.i("Metadata", "Title: $it") }
artist?.let { Log.i("Metadata", "Artist: $it") }
album?.let { Log.i("Metadata", "Album: $it") }
genre?.let { Log.i("Metadata", "Genre: $it") }
lyrics?.let { Log.i("Metadata", "Lyrics:\n$it") }

// 6. Get cover art
if (metadata.getBoolean("hasCoverArt")) {
    val coverData = player.getCoverArt()
    val coverMimeType = player.getCoverArtMimeType()

    Log.i("Metadata", "Cover Format: $coverMimeType")
    Log.i("Metadata", "Cover Size: ${coverData.size} bytes")

    // Convert cover to Bitmap
    val bitmap = BitmapFactory.decodeByteArray(coverData, 0, coverData.size)
    imageView.setImageBitmap(bitmap)
}
```

### Java Native Methods Declaration

Add the following methods to `RustAudioPlayer.java`:

```java
public class RustAudioPlayer {
    // ... existing methods ...

    /**
     * Get audio metadata (JSON format)
     * @return JSON string containing all metadata information
     */
    public native String nativeGetMetadataJson(long playerId);

    /**
     * Get cover art data
     * @return Image byte array, or null if no cover art
     */
    public native byte[] nativeGetCoverArt(long playerId);

    /**
     * Get cover art MIME type
     * @return MIME type string (e.g., "image/jpeg"), or null if no cover art
     */
    public native String nativeGetCoverArtMimeType(long playerId);

    // Wrapper methods
    public String getMetadataJson() {
        return nativeGetMetadataJson(this.playerId);
    }

    public byte[] getCoverArt() {
        return nativeGetCoverArt(this.playerId);
    }

    public String getCoverArtMimeType() {
        return nativeGetCoverArtMimeType(this.playerId);
    }
}
```

## Supported Audio Formats

Metadata extraction supports the following formats:

| Format | Extension | Metadata Standard | Cover Support |
|--------|-----------|-------------------|---------------|
| MP3 | .mp3 | ID3v1, ID3v2 | ✅ |
| AAC | .m4a, .aac | iTunes metadata | ✅ |
| FLAC | .flac | Vorbis Comments | ✅ |
| OGG Vorbis | .ogg | Vorbis Comments | ✅ |
| Opus | .opus | Vorbis Comments | ✅ |
| WAV | .wav | RIFF INFO | ⚠️ |
| ALAC | .m4a | iTunes metadata | ✅ |

## JSON Response Format

```json
{
  "formatInfo": {
    "durationMs": 245000,
    "sampleRate": 44100,
    "channels": 2,
    "codec": "MP3",
    "bitrateBps": 320000,
    "totalFrames": 10808100
  },
  "quality": {
    "bitDepth": 16,
    "isVbr": false,
    "compressionQuality": null,
    "instantaneousBitrateBps": 320000
  },
  "tags": {
    "title": "Example Song",
    "artist": "Example Artist",
    "album": "Example Album",
    "albumArtist": "Example Album Artist",
    "trackNumber": 1,
    "trackTotal": 12,
    "discNumber": 1,
    "discTotal": 1,
    "date": "2024",
    "genre": "Pop",
    "composer": null,
    "comment": null,
    "lyrics": "Lyrics content...",
    "copyright": "© 2024",
    "encoder": "LAME 3.100",
    "publisher": null,
    "isrc": "USRC12345678",
    "language": "en"
  },
  "hasCoverArt": true
}
```

## Implementation Details

### Architecture

1. **metadata.rs** - Defines all metadata structures
   - `AudioMetadata` - Top-level metadata container
   - `FormatInfo` - Format information
   - `QualityParams` - Quality parameters
   - `AudioTags` - Tag information
   - `CoverArt` - Cover art
   - `Chapter` - Chapter information

2. **decoder.rs** - Metadata extraction logic
   - `extract_metadata()` - Extract complete metadata
   - `extract_tags()` - Extract from Symphonia tags
   - `extract_cover_art()` - Extract cover art
   - `extract_chapters()` - Extract chapters (to be implemented)

3. **jni_bindings.rs** - Android JNI interface
   - `nativeGetMetadataJson()` - Return metadata in JSON format
   - `nativeGetCoverArt()` - Return cover byte array
   - `nativeGetCoverArtMimeType()` - Return MIME type

### Libraries Used

- **Symphonia 0.5** - Powerful Rust audio decoding library
  - Supports metadata standards for multiple formats
  - Automatic tag recognition and parsing
  - Built-in cover art extraction

## Notes

1. **Chapter Support**: Chapter extraction framework is ready but requires format-specific implementation (e.g., MP3 ID3 CHAP, MP4 chapters)

2. **Performance**: Metadata is extracted once when the file is loaded and does not affect playback performance

3. **Memory**: Cover art data is stored in memory; large images may consume significant memory

4. **Encoding**: All text fields use UTF-8 encoding

5. **Compatibility**: Tested on Android, requires NDK build environment

## Future Optimizations

- [ ] Implement complete chapter extraction (MP3 CHAP, MP4 chapters)
- [ ] Add iterator support for custom tags
- [ ] Support writing/modifying metadata (currently read-only)
- [ ] Optimize memory usage for large cover art
- [ ] Add metadata extraction for streaming URLs

## License

Same as the rust-audio-player main project
