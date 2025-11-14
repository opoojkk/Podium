// JNI bindings for Android
// Provides a bridge between Kotlin/Java and Rust

#[cfg(target_os = "android")]
use jni::JNIEnv;
#[cfg(target_os = "android")]
use jni::objects::{JClass, JObject, JString, JByteArray, GlobalRef};
#[cfg(target_os = "android")]
use jni::sys::{jlong, jfloat, jint, jstring};
#[cfg(target_os = "android")]
use std::sync::Arc;
#[cfg(target_os = "android")]
use parking_lot::Mutex;
#[cfg(target_os = "android")]
use once_cell::sync::Lazy;
#[cfg(target_os = "android")]
use std::collections::HashMap;

#[cfg(target_os = "android")]
use crate::player::{AudioPlayer, PlayerState};
#[cfg(target_os = "android")]
use crate::callback::{PlayerCallback, CallbackEvent};
#[cfg(target_os = "android")]
use crate::android::AndroidAudioPlayer;

#[cfg(target_os = "android")]
static PLAYER_REGISTRY: Lazy<Mutex<HashMap<i64, Box<dyn AudioPlayer>>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));

#[cfg(target_os = "android")]
static NEXT_PLAYER_ID: Lazy<Mutex<i64>> = Lazy::new(|| Mutex::new(1));

#[cfg(target_os = "android")]
static CALLBACK_REGISTRY: Lazy<Mutex<HashMap<i64, Arc<JniCallback>>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));

/// JNI callback wrapper
/// Bridges Rust callbacks to Java/Kotlin
#[cfg(target_os = "android")]
struct JniCallback {
    callback_object: GlobalRef,
}

#[cfg(target_os = "android")]
impl JniCallback {
    fn new(env: &JNIEnv, callback_object: JObject) -> Result<Self, jni::errors::Error> {
        let global_ref = env.new_global_ref(callback_object)?;
        Ok(Self {
            callback_object: global_ref,
        })
    }
}

#[cfg(target_os = "android")]
impl PlayerCallback for JniCallback {
    fn on_event(&self, event: CallbackEvent) {
        // Get JNI environment for this thread
        // Note: This is a simplified implementation
        // In production, you'd need to attach the thread to the JVM
        log::debug!("Callback event: {:?}", event);

        // TODO: Implement proper JNI callback invocation
        // This requires attaching the native thread to the JVM and calling Java methods
        // For now, we just log the event
    }
}

// Helper function to convert Java string to Rust string
#[cfg(target_os = "android")]
fn jstring_to_string(env: &mut JNIEnv, jstr: &JString) -> Result<String, jni::errors::Error> {
    let java_str = env.get_string(jstr)?;
    Ok(java_str.into())
}

// Helper function to convert Rust string to Java string
#[cfg(target_os = "android")]
fn string_to_jstring(env: &JNIEnv, s: &str) -> Result<jstring, jni::errors::Error> {
    let jstr = env.new_string(s)?;
    Ok(jstr.into_raw())
}

/// Create a new audio player instance
/// Returns a player ID (handle) for subsequent operations
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeCreate(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    crate::init_logging();

    log::info!("Creating native audio player");

    match AndroidAudioPlayer::new() {
        Ok(player) => {
            let mut id_lock = NEXT_PLAYER_ID.lock();
            let player_id = *id_lock;
            *id_lock += 1;
            drop(id_lock);

            let mut registry = PLAYER_REGISTRY.lock();
            registry.insert(player_id, Box::new(player));
            drop(registry);

            log::info!("Audio player created with ID: {}", player_id);
            player_id
        }
        Err(e) => {
            log::error!("Failed to create audio player: {}", e);
            -1
        }
    }
}

/// Load audio file
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeLoadFile(
    mut env: JNIEnv,
    _class: JClass,
    player_id: jlong,
    path: JString,
) -> jint {
    let path_str = match jstring_to_string(&mut env, &path) {
        Ok(s) => s,
        Err(e) => {
            log::error!("Failed to convert path: {}", e);
            return -1;
        }
    };

    log::info!("Loading file: {}", path_str);

    let mut registry = PLAYER_REGISTRY.lock();
    if let Some(player) = registry.get_mut(&player_id) {
        match player.load_file(&path_str) {
            Ok(_) => {
                log::info!("File loaded successfully");
                0
            }
            Err(e) => {
                log::error!("Failed to load file: {}", e);
                -1
            }
        }
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Load audio from byte buffer
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeLoadBuffer(
    env: JNIEnv,
    _class: JClass,
    player_id: jlong,
    buffer: JByteArray,
) -> jint {
    let buffer_data = match env.convert_byte_array(&buffer) {
        Ok(data) => data,
        Err(e) => {
            log::error!("Failed to convert buffer: {}", e);
            return -1;
        }
    };

    log::info!("Loading buffer: {} bytes", buffer_data.len());

    let mut registry = PLAYER_REGISTRY.lock();
    if let Some(player) = registry.get_mut(&player_id) {
        match player.load_buffer(&buffer_data) {
            Ok(_) => {
                log::info!("Buffer loaded successfully");
                0
            }
            Err(e) => {
                log::error!("Failed to load buffer: {}", e);
                -1
            }
        }
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Start playback
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativePlay(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jint {
    log::info!("Play command for player {}", player_id);

    let mut registry = PLAYER_REGISTRY.lock();
    if let Some(player) = registry.get_mut(&player_id) {
        match player.play() {
            Ok(_) => 0,
            Err(e) => {
                log::error!("Failed to play: {}", e);
                -1
            }
        }
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Pause playback
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativePause(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jint {
    log::info!("Pause command for player {}", player_id);

    let mut registry = PLAYER_REGISTRY.lock();
    if let Some(player) = registry.get_mut(&player_id) {
        match player.pause() {
            Ok(_) => 0,
            Err(e) => {
                log::error!("Failed to pause: {}", e);
                -1
            }
        }
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Stop playback
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeStop(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jint {
    log::info!("Stop command for player {}", player_id);

    let mut registry = PLAYER_REGISTRY.lock();
    if let Some(player) = registry.get_mut(&player_id) {
        match player.stop() {
            Ok(_) => 0,
            Err(e) => {
                log::error!("Failed to stop: {}", e);
                -1
            }
        }
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Seek to position (milliseconds)
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeSeek(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
    position_ms: jlong,
) -> jint {
    log::info!("Seek command for player {} to {} ms", player_id, position_ms);

    let mut registry = PLAYER_REGISTRY.lock();
    if let Some(player) = registry.get_mut(&player_id) {
        match player.seek(position_ms as u64) {
            Ok(_) => 0,
            Err(e) => {
                log::error!("Failed to seek: {}", e);
                -1
            }
        }
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Set volume (0.0 - 1.0)
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeSetVolume(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
    volume: jfloat,
) -> jint {
    log::debug!("Set volume for player {} to {}", player_id, volume);

    let mut registry = PLAYER_REGISTRY.lock();
    if let Some(player) = registry.get_mut(&player_id) {
        match player.set_volume(volume) {
            Ok(_) => 0,
            Err(e) => {
                log::error!("Failed to set volume: {}", e);
                -1
            }
        }
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Get current position (milliseconds)
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeGetPosition(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jlong {
    let registry = PLAYER_REGISTRY.lock();
    if let Some(player) = registry.get(&player_id) {
        let status = player.get_status();
        status.position_ms as jlong
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Get duration (milliseconds)
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeGetDuration(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jlong {
    let registry = PLAYER_REGISTRY.lock();
    if let Some(player) = registry.get(&player_id) {
        let status = player.get_status();
        status.duration_ms as jlong
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Get player state
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeGetState(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jint {
    let registry = PLAYER_REGISTRY.lock();
    if let Some(player) = registry.get(&player_id) {
        let state = player.get_state();
        match state {
            PlayerState::Idle => 0,
            PlayerState::Loading => 1,
            PlayerState::Ready => 2,
            PlayerState::Playing => 3,
            PlayerState::Paused => 4,
            PlayerState::Stopped => 5,
            PlayerState::Error => 6,
        }
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Release player resources
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeRelease(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jint {
    log::info!("Releasing player {}", player_id);

    let mut registry = PLAYER_REGISTRY.lock();
    if let Some(mut player) = registry.remove(&player_id) {
        match player.release() {
            Ok(_) => {
                log::info!("Player {} released successfully", player_id);
                0
            }
            Err(e) => {
                log::error!("Failed to release player: {}", e);
                -1
            }
        }
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

// ============================================================================
// Metadata Access Functions
// ============================================================================

/// Get metadata as JSON string
/// This is the easiest way to transfer complex metadata to Java/Kotlin
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeGetMetadataJson(
    env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jstring {
    use crate::android::AndroidAudioPlayer;

    let registry = PLAYER_REGISTRY.lock();
    if let Some(player) = registry.get(&player_id) {
        // Downcast to AndroidAudioPlayer to access decoder
        let android_player = player.as_any().downcast_ref::<AndroidAudioPlayer>();

        if let Some(android_player) = android_player {
            if let Some(decoder_guard) = android_player.get_decoder() {
                if let Some(ref decoder) = *decoder_guard {
                    let metadata = &decoder.metadata;

                // Create JSON representation of metadata
                let json = format!(
                    r#"{{
                        "formatInfo": {{
                            "durationMs": {},
                            "sampleRate": {},
                            "channels": {},
                            "codec": "{}",
                            "bitrateBps": {},
                            "totalFrames": {}
                        }},
                        "quality": {{
                            "bitDepth": {},
                            "isVbr": {},
                            "compressionQuality": {},
                            "instantaneousBitrateBps": {}
                        }},
                        "tags": {{
                            "title": {},
                            "artist": {},
                            "album": {},
                            "albumArtist": {},
                            "trackNumber": {},
                            "trackTotal": {},
                            "discNumber": {},
                            "discTotal": {},
                            "date": {},
                            "genre": {},
                            "composer": {},
                            "comment": {},
                            "lyrics": {},
                            "copyright": {},
                            "encoder": {},
                            "publisher": {},
                            "isrc": {},
                            "language": {}
                        }},
                        "hasCoverArt": {}
                    }}"#,
                    metadata.format_info.duration_ms,
                    metadata.format_info.sample_rate,
                    metadata.format_info.channels,
                    metadata.format_info.codec,
                    metadata.format_info.bitrate_bps.map(|b| format!("{}", b)).unwrap_or("null".to_string()),
                    metadata.format_info.total_frames.map(|f| format!("{}", f)).unwrap_or("null".to_string()),
                    metadata.quality.bit_depth.map(|b| format!("{}", b)).unwrap_or("null".to_string()),
                    metadata.quality.is_vbr,
                    metadata.quality.compression_quality.map(|q| format!("{}", q)).unwrap_or("null".to_string()),
                    metadata.quality.instantaneous_bitrate_bps.map(|b| format!("{}", b)).unwrap_or("null".to_string()),
                    json_option_string(&metadata.tags.title),
                    json_option_string(&metadata.tags.artist),
                    json_option_string(&metadata.tags.album),
                    json_option_string(&metadata.tags.album_artist),
                    metadata.tags.track_number.map(|n| format!("{}", n)).unwrap_or("null".to_string()),
                    metadata.tags.track_total.map(|n| format!("{}", n)).unwrap_or("null".to_string()),
                    metadata.tags.disc_number.map(|n| format!("{}", n)).unwrap_or("null".to_string()),
                    metadata.tags.disc_total.map(|n| format!("{}", n)).unwrap_or("null".to_string()),
                    json_option_string(&metadata.tags.date),
                    json_option_string(&metadata.tags.genre),
                    json_option_string(&metadata.tags.composer),
                    json_option_string(&metadata.tags.comment),
                    json_option_string(&metadata.tags.lyrics),
                    json_option_string(&metadata.tags.copyright),
                    json_option_string(&metadata.tags.encoder),
                    json_option_string(&metadata.tags.publisher),
                    json_option_string(&metadata.tags.isrc),
                    json_option_string(&metadata.tags.language),
                    decoder.get_cover_art().is_some()
                );

                    match string_to_jstring(&env, &json) {
                        Ok(jstr) => jstr,
                        Err(e) => {
                            log::error!("Failed to create JSON string: {}", e);
                            std::ptr::null_mut()
                        }
                    }
                } else {
                    log::warn!("No decoder loaded for player {}", player_id);
                    match string_to_jstring(&env, "{}") {
                        Ok(jstr) => jstr,
                        Err(_) => std::ptr::null_mut()
                    }
                }
            }
        } else {
            log::error!("Failed to downcast player to AndroidAudioPlayer");
            std::ptr::null_mut()
        }
    } else {
        log::error!("Invalid player ID: {}", player_id);
        std::ptr::null_mut()
    }
}

#[cfg(target_os = "android")]
fn json_option_string(opt: &Option<String>) -> String {
    match opt {
        Some(s) => format!(r#""{}""#, s.replace("\\", "\\\\").replace("\"", "\\\"")),
        None => "null".to_string()
    }
}

/// Get cover art as byte array
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeGetCoverArt<'local>(
    env: JNIEnv<'local>,
    _class: JClass,
    player_id: jlong,
) -> JByteArray<'local> {
    use crate::android::AndroidAudioPlayer;

    let registry = PLAYER_REGISTRY.lock();
    if let Some(player) = registry.get(&player_id) {
        let android_player = player.as_any().downcast_ref::<AndroidAudioPlayer>();

        if let Some(android_player) = android_player {
            if let Some(decoder_guard) = android_player.get_decoder() {
                if let Some(ref decoder) = *decoder_guard {
                    if let Some(cover_art) = decoder.get_cover_art() {
                        match env.byte_array_from_slice(&cover_art.data) {
                            Ok(byte_array) => return byte_array.into_raw(),
                            Err(e) => {
                                log::error!("Failed to create byte array: {}", e);
                            }
                        }
                    }
                }
            }
        }
    }

    std::ptr::null_mut()
}

/// Get cover art MIME type
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeGetCoverArtMimeType(
    env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jstring {
    use crate::android::AndroidAudioPlayer;

    let registry = PLAYER_REGISTRY.lock();
    if let Some(player) = registry.get(&player_id) {
        let android_player = player.as_any().downcast_ref::<AndroidAudioPlayer>();

        if let Some(android_player) = android_player {
            if let Some(decoder_guard) = android_player.get_decoder() {
                if let Some(ref decoder) = *decoder_guard {
                    if let Some(cover_art) = decoder.get_cover_art() {
                        match string_to_jstring(&env, &cover_art.mime_type) {
                            Ok(jstr) => return jstr,
                            Err(e) => {
                                log::error!("Failed to create MIME type string: {}", e);
                            }
                        }
                    }
                }
            }
        }
    }

    std::ptr::null_mut()
}
