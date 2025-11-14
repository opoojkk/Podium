# 音频元数据提取功能使用指南

## 功能概述

rust-audio-player 现已支持完整的音频元数据提取功能，包括：

### 基础元数据
- ✅ **时长** (duration_ms) - 精确到毫秒
- ✅ **采样率** (sample_rate) - 如 44100Hz, 48000Hz
- ✅ **声道数** (channels) - 单声道/立体声/多声道
- ✅ **比特率** (bitrate_bps) - 平均码率
- ✅ **编码格式** (codec) - MP3, AAC, Opus, Vorbis, FLAC 等

### 音频质量参数
- ✅ **位深度** (bit_depth) - 如 16, 24, 32 位
- ✅ **VBR检测** (is_vbr) - 是否为可变比特率
- ✅ **瞬时比特率** (instantaneous_bitrate_bps) - VBR文件的瞬时码率

### ID3/元数据标签
- ✅ **标题** (title) - 歌曲/播客标题
- ✅ **艺术家** (artist) - 表演者
- ✅ **专辑** (album) - 专辑名称
- ✅ **专辑艺术家** (album_artist)
- ✅ **曲目编号** (track_number/track_total)
- ✅ **光盘编号** (disc_number/disc_total)
- ✅ **发行日期** (date)
- ✅ **流派** (genre)
- ✅ **作曲者** (composer)
- ✅ **评论** (comment)
- ✅ **歌词** (lyrics)
- ✅ **版权** (copyright)
- ✅ **编码器** (encoder)
- ✅ **发行商** (publisher)
- ✅ **ISRC** (国际标准录音代码)
- ✅ **语言** (language)
- ✅ **自定义标签** (custom_tags)

### 封面图片
- ✅ **封面数据** (cover_art.data) - 图像字节数据
- ✅ **MIME类型** (cover_art.mime_type) - 如 "image/jpeg", "image/png"
- ✅ **图片描述** (cover_art.description)
- ✅ **图片类型** (cover_art.picture_type)

### 章节信息（播客特别重要）
- ⚠️ **章节标记** (chapters) - 框架已准备好，等待格式特定实现

## Rust API 使用示例

```rust
use rust_audio_player::decoder::AudioDecoder;

// 加载音频文件
let decoder = AudioDecoder::from_file("path/to/audio.mp3")?;

// 访问基础格式信息
println!("时长: {} ms", decoder.metadata.format_info.duration_ms);
println!("采样率: {} Hz", decoder.metadata.format_info.sample_rate);
println!("声道数: {}", decoder.metadata.format_info.channels);
println!("编码格式: {}", decoder.metadata.format_info.codec);

if let Some(bitrate) = decoder.metadata.format_info.bitrate_bps {
    println!("比特率: {} kbps", bitrate / 1000);
}

// 访问音频质量参数
if let Some(bit_depth) = decoder.metadata.quality.bit_depth {
    println!("位深度: {} bit", bit_depth);
}

if decoder.metadata.quality.is_vbr {
    println!("这是一个VBR文件");
}

// 访问标签信息
if let Some(title) = &decoder.metadata.tags.title {
    println!("标题: {}", title);
}

if let Some(artist) = &decoder.metadata.tags.artist {
    println!("艺术家: {}", artist);
}

if let Some(album) = &decoder.metadata.tags.album {
    println!("专辑: {}", album);
}

if let Some(lyrics) = &decoder.metadata.tags.lyrics {
    println!("歌词:\n{}", lyrics);
}

// 获取封面图片
if let Some(cover) = decoder.get_cover_art() {
    println!("封面格式: {}", cover.mime_type);
    println!("封面大小: {} bytes", cover.data.len());

    // 保存封面到文件
    std::fs::write("cover.jpg", &cover.data)?;
}

// 获取元数据摘要
println!("\n元数据摘要:\n{}", decoder.metadata.summary());
```

## Android JNI API 使用示例

### Kotlin/Java 代码

```kotlin
// 1. 创建播放器并加载文件
val player = RustAudioPlayer()
player.loadFile("/path/to/audio.mp3")

// 2. 获取元数据（JSON格式）
val metadataJson = player.getMetadataJson()
val metadata = JSONObject(metadataJson)

// 3. 解析基础信息
val formatInfo = metadata.getJSONObject("formatInfo")
val durationMs = formatInfo.getLong("durationMs")
val sampleRate = formatInfo.getInt("sampleRate")
val channels = formatInfo.getInt("channels")
val codec = formatInfo.getString("codec")
val bitrateBps = formatInfo.optInt("bitrateBps", -1)

Log.i("Metadata", "时长: ${durationMs}ms")
Log.i("Metadata", "采样率: ${sampleRate}Hz")
Log.i("Metadata", "声道: $channels")
Log.i("Metadata", "编码: $codec")

if (bitrateBps > 0) {
    Log.i("Metadata", "比特率: ${bitrateBps / 1000}kbps")
}

// 4. 解析音频质量
val quality = metadata.getJSONObject("quality")
val bitDepth = quality.optInt("bitDepth", -1)
val isVbr = quality.getBoolean("isVbr")

if (bitDepth > 0) {
    Log.i("Metadata", "位深度: ${bitDepth}bit")
}

if (isVbr) {
    Log.i("Metadata", "VBR编码")
}

// 5. 解析标签信息
val tags = metadata.getJSONObject("tags")
val title = tags.optString("title", null)
val artist = tags.optString("artist", null)
val album = tags.optString("album", null)
val genre = tags.optString("genre", null)
val lyrics = tags.optString("lyrics", null)

title?.let { Log.i("Metadata", "标题: $it") }
artist?.let { Log.i("Metadata", "艺术家: $it") }
album?.let { Log.i("Metadata", "专辑: $it") }
genre?.let { Log.i("Metadata", "流派: $it") }
lyrics?.let { Log.i("Metadata", "歌词:\n$it") }

// 6. 获取封面图片
if (metadata.getBoolean("hasCoverArt")) {
    val coverData = player.getCoverArt()
    val coverMimeType = player.getCoverArtMimeType()

    Log.i("Metadata", "封面格式: $coverMimeType")
    Log.i("Metadata", "封面大小: ${coverData.size} bytes")

    // 将封面转换为Bitmap
    val bitmap = BitmapFactory.decodeByteArray(coverData, 0, coverData.size)
    imageView.setImageBitmap(bitmap)
}
```

### Java Native Methods 声明

在 `RustAudioPlayer.java` 中添加以下方法：

```java
public class RustAudioPlayer {
    // ... 现有方法 ...

    /**
     * 获取音频元数据（JSON格式）
     * @return JSON字符串，包含所有元数据信息
     */
    public native String nativeGetMetadataJson(long playerId);

    /**
     * 获取封面图片数据
     * @return 图片字节数组，如果没有封面则返回null
     */
    public native byte[] nativeGetCoverArt(long playerId);

    /**
     * 获取封面图片MIME类型
     * @return MIME类型字符串（如 "image/jpeg"），如果没有封面则返回null
     */
    public native String nativeGetCoverArtMimeType(long playerId);

    // 包装方法
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

## 支持的音频格式

元数据提取支持以下格式：

| 格式 | 扩展名 | 元数据标准 | 封面支持 |
|------|--------|------------|----------|
| MP3 | .mp3 | ID3v1, ID3v2 | ✅ |
| AAC | .m4a, .aac | iTunes metadata | ✅ |
| FLAC | .flac | Vorbis Comments | ✅ |
| OGG Vorbis | .ogg | Vorbis Comments | ✅ |
| Opus | .opus | Vorbis Comments | ✅ |
| WAV | .wav | RIFF INFO | ⚠️ |
| ALAC | .m4a | iTunes metadata | ✅ |

## JSON 响应格式

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
    "title": "示例歌曲",
    "artist": "示例艺术家",
    "album": "示例专辑",
    "albumArtist": "示例专辑艺术家",
    "trackNumber": 1,
    "trackTotal": 12,
    "discNumber": 1,
    "discTotal": 1,
    "date": "2024",
    "genre": "Pop",
    "composer": null,
    "comment": null,
    "lyrics": "歌词内容...",
    "copyright": "© 2024",
    "encoder": "LAME 3.100",
    "publisher": null,
    "isrc": "USRC12345678",
    "language": "zh"
  },
  "hasCoverArt": true
}
```

## 实现细节

### 架构

1. **metadata.rs** - 定义所有元数据结构
   - `AudioMetadata` - 顶层元数据容器
   - `FormatInfo` - 格式信息
   - `QualityParams` - 质量参数
   - `AudioTags` - 标签信息
   - `CoverArt` - 封面图片
   - `Chapter` - 章节信息

2. **decoder.rs** - 元数据提取逻辑
   - `extract_metadata()` - 提取完整元数据
   - `extract_tags()` - 从Symphonia标签提取
   - `extract_cover_art()` - 提取封面图片
   - `extract_chapters()` - 提取章节（待完善）

3. **jni_bindings.rs** - Android JNI接口
   - `nativeGetMetadataJson()` - 返回JSON格式元数据
   - `nativeGetCoverArt()` - 返回封面字节数组
   - `nativeGetCoverArtMimeType()` - 返回MIME类型

### 使用的库

- **Symphonia 0.5** - 强大的Rust音频解码库
  - 支持多种格式的元数据标准
  - 自动识别和解析标签
  - 内置封面图片提取

## 注意事项

1. **章节支持**: 章节提取框架已就绪，但需要针对特定格式（如MP3的ID3 CHAP，MP4的章节）进行扩展实现

2. **性能**: 元数据在文件加载时一次性提取，不会影响播放性能

3. **内存**: 封面图片数据会保存在内存中，大型图片可能占用较多内存

4. **编码**: 所有文本字段都使用UTF-8编码

5. **兼容性**: 在Android上测试，需要NDK编译环境

## 下一步优化

- [ ] 实现完整的章节提取（MP3 CHAP, MP4 chapters）
- [ ] 添加自定义标签的迭代器支持
- [ ] 支持写入/修改元数据（目前只读）
- [ ] 优化大型封面图片的内存使用
- [ ] 添加流媒体URL的元数据提取

## 许可证

与rust-audio-player主项目相同
