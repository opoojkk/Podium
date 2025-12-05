// JNI bindings for Android
// Provides a bridge between Kotlin/Java and the modular Podium audio architecture

use jni::JNIEnv;
use jni::objects::{JClass, JObject, JString, JByteArray, GlobalRef};
use jni::sys::{jlong, jfloat, jint, jstring};
use std::sync::Arc;
use parking_lot::Mutex;
use once_cell::sync::Lazy;
use std::collections::HashMap;

use podium_core::{AudioError, PlayerState};
use podium_renderer_api::AudioRenderer;

mod player;
use player::PodiumPlayer;

// ============================================================================
// Global Player Registry
// ============================================================================

static PLAYER_REGISTRY: Lazy<Mutex<HashMap<i64, Arc<Mutex<PodiumPlayer>>>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));

static NEXT_PLAYER_ID: Lazy<Mutex<i64>> = Lazy::new(|| Mutex::new(1));

// ============================================================================
// Logging Initialization
// ============================================================================

fn init_logging() {
    #[cfg(target_os = "android")]
    {
        use android_logger::Config;
        use log::LevelFilter;

        // Initialize Android logger (only once)
        let _ = android_logger::init_once(
            Config::default()
                .with_max_level(LevelFilter::Debug)
                .with_tag("PodiumAudio")
        );
    }

    #[cfg(not(target_os = "android"))]
    {
        // Initialize env_logger for Desktop JVM (only once)
        let _ = env_logger::builder()
            .filter_level(log::LevelFilter::Debug)
            .try_init();
    }
}

// ============================================================================
// Helper Functions
// ============================================================================

fn jstring_to_string(env: &mut JNIEnv, jstr: &JString) -> Result<String, jni::errors::Error> {
    let java_str = env.get_string(jstr)?;
    Ok(java_str.into())
}

fn string_to_jstring(env: &JNIEnv, s: &str) -> Result<jstring, jni::errors::Error> {
    let jstr = env.new_string(s)?;
    Ok(jstr.into_raw())
}

// ============================================================================
// Player Lifecycle
// ============================================================================

/// Create a new audio player instance
/// Returns a player ID (handle) for subsequent operations
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeCreate(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    init_logging();

    log::info!("Creating native audio player (modular architecture)");

    match PodiumPlayer::new() {
        Ok(player) => {
            let mut id_lock = NEXT_PLAYER_ID.lock();
            let player_id = *id_lock;
            *id_lock += 1;
            drop(id_lock);

            let mut registry = PLAYER_REGISTRY.lock();
            registry.insert(player_id, Arc::new(Mutex::new(player)));
            drop(registry);

            log::info!("Audio player created with ID: {}", player_id);
            player_id
        }
        Err(e) => {
            log::error!("Failed to create audio player: {:?}", e);
            -1
        }
    }
}

/// Release player resources
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeRelease(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jint {
    log::info!("Releasing player {}", player_id);

    let mut registry = PLAYER_REGISTRY.lock();
    if let Some(player_arc) = registry.remove(&player_id) {
        drop(registry); // Release lock before stopping player

        let mut player = player_arc.lock();
        match player.release() {
            Ok(_) => {
                log::info!("Player {} released successfully", player_id);
                0
            }
            Err(e) => {
                log::error!("Failed to release player: {:?}", e);
                -1
            }
        }
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

// ============================================================================
// Loading Audio
// ============================================================================

/// Load audio file from path
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

    let registry = PLAYER_REGISTRY.lock();
    if let Some(player_arc) = registry.get(&player_id) {
        let mut player = player_arc.lock();
        match player.load_file(&path_str) {
            Ok(_) => {
                log::info!("File loaded successfully");
                0
            }
            Err(e) => {
                log::error!("Failed to load file: {:?}", e);
                -1
            }
        }
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Load audio from byte buffer
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

    let registry = PLAYER_REGISTRY.lock();
    if let Some(player_arc) = registry.get(&player_id) {
        let mut player = player_arc.lock();
        match player.load_buffer(&buffer_data) {
            Ok(_) => {
                log::info!("Buffer loaded successfully");
                0
            }
            Err(e) => {
                log::error!("Failed to load buffer: {:?}", e);
                -1
            }
        }
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Load audio from URL (streaming)
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeLoadUrl(
    mut env: JNIEnv,
    _class: JClass,
    player_id: jlong,
    url: JString,
) -> jint {
    let url_str = match jstring_to_string(&mut env, &url) {
        Ok(s) => s,
        Err(e) => {
            log::error!("Failed to convert URL: {}", e);
            return -1;
        }
    };

    log::info!("Loading URL: {}", url_str);

    let registry = PLAYER_REGISTRY.lock();
    if let Some(player_arc) = registry.get(&player_id) {
        let mut player = player_arc.lock();
        match player.load_url(&url_str) {
            Ok(_) => {
                log::info!("URL loaded successfully");
                0
            }
            Err(e) => {
                log::error!("Failed to load URL: {:?}", e);
                -1
            }
        }
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

// ============================================================================
// Playback Control
// ============================================================================

/// Start playback
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativePlay(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jint {
    log::info!("Play command for player {}", player_id);

    let registry = PLAYER_REGISTRY.lock();
    if let Some(player_arc) = registry.get(&player_id) {
        let mut player = player_arc.lock();
        match player.play() {
            Ok(_) => 0,
            Err(e) => {
                log::error!("Failed to play: {:?}", e);
                -1
            }
        }
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Pause playback
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativePause(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jint {
    log::info!("Pause command for player {}", player_id);

    let registry = PLAYER_REGISTRY.lock();
    if let Some(player_arc) = registry.get(&player_id) {
        let mut player = player_arc.lock();
        match player.pause() {
            Ok(_) => 0,
            Err(e) => {
                log::error!("Failed to pause: {:?}", e);
                -1
            }
        }
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Stop playback
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeStop(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jint {
    log::info!("Stop command for player {}", player_id);

    let registry = PLAYER_REGISTRY.lock();
    if let Some(player_arc) = registry.get(&player_id) {
        let mut player = player_arc.lock();
        match player.stop() {
            Ok(_) => 0,
            Err(e) => {
                log::error!("Failed to stop: {:?}", e);
                -1
            }
        }
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Seek to position (milliseconds)
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeSeek(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
    position_ms: jlong,
) -> jint {
    log::info!("Seek command for player {} to {} ms", player_id, position_ms);

    let registry = PLAYER_REGISTRY.lock();
    if let Some(player_arc) = registry.get(&player_id) {
        let mut player = player_arc.lock();
        match player.seek(position_ms as u64) {
            Ok(_) => 0,
            Err(e) => {
                log::error!("Failed to seek: {:?}", e);
                -1
            }
        }
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Set volume (0.0 - 1.0)
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeSetVolume(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
    volume: jfloat,
) -> jint {
    log::debug!("Set volume for player {} to {}", player_id, volume);

    let registry = PLAYER_REGISTRY.lock();
    if let Some(player_arc) = registry.get(&player_id) {
        let mut player = player_arc.lock();
        match player.set_volume(volume) {
            Ok(_) => 0,
            Err(e) => {
                log::error!("Failed to set volume: {:?}", e);
                -1
            }
        }
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

// ============================================================================
// State Queries
// ============================================================================

/// Get current position (milliseconds)
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeGetPosition(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jlong {
    let registry = PLAYER_REGISTRY.lock();
    if let Some(player_arc) = registry.get(&player_id) {
        let player = player_arc.lock();
        player.get_position() as jlong
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Get duration (milliseconds)
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeGetDuration(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jlong {
    let registry = PLAYER_REGISTRY.lock();
    if let Some(player_arc) = registry.get(&player_id) {
        let player = player_arc.lock();
        player.get_duration() as jlong
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Get player state
#[no_mangle]
pub extern "C" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeGetState(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jint {
    let registry = PLAYER_REGISTRY.lock();
    if let Some(player_arc) = registry.get(&player_id) {
        let player = player_arc.lock();
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
