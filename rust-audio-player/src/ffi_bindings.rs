// C FFI bindings for iOS/macOS
// Provides C-compatible interface to the audio player

use crate::player::AudioPlayer;
use std::collections::HashMap;
use std::ffi::CStr;
use std::os::raw::c_char;
use std::sync::Mutex;

lazy_static::lazy_static! {
    static ref PLAYER_REGISTRY: Mutex<HashMap<i64, Box<dyn AudioPlayer>>> = Mutex::new(HashMap::new());
    static ref NEXT_PLAYER_ID: Mutex<i64> = Mutex::new(1);
}

/// Create a new audio player instance
/// Returns: player ID (>0) on success, -1 on error
#[no_mangle]
pub extern "C" fn rust_audio_player_create() -> i64 {
    match crate::create_player() {
        Ok(player) => {
            let mut id_counter = NEXT_PLAYER_ID.lock().unwrap();
            let player_id = *id_counter;
            *id_counter += 1;

            let mut registry = PLAYER_REGISTRY.lock().unwrap();
            registry.insert(player_id, player);

            log::info!("Created audio player with ID: {}", player_id);
            player_id
        }
        Err(e) => {
            log::error!("Failed to create audio player: {}", e);
            -1
        }
    }
}

/// Load audio file from path
/// Returns: 0 on success, -1 on error
#[no_mangle]
pub extern "C" fn rust_audio_player_load_file(player_id: i64, path: *const c_char) -> i32 {
    if path.is_null() {
        log::error!("Null path provided");
        return -1;
    }

    let path_str = match unsafe { CStr::from_ptr(path) }.to_str() {
        Ok(s) => s,
        Err(e) => {
            log::error!("Invalid UTF-8 in path: {}", e);
            return -1;
        }
    };

    let mut registry = PLAYER_REGISTRY.lock().unwrap();
    match registry.get_mut(&player_id) {
        Some(player) => {
            match player.load_file(path_str) {
                Ok(_) => {
                    log::info!("Loaded file: {}", path_str);
                    0
                }
                Err(e) => {
                    log::error!("Failed to load file: {}", e);
                    -1
                }
            }
        }
        None => {
            log::error!("Invalid player ID: {}", player_id);
            -1
        }
    }
}

/// Load audio from URL
/// Returns: 0 on success, -1 on error
#[no_mangle]
pub extern "C" fn rust_audio_player_load_url(player_id: i64, url: *const c_char) -> i32 {
    if url.is_null() {
        log::error!("Null URL provided");
        return -1;
    }

    let url_str = match unsafe { CStr::from_ptr(url) }.to_str() {
        Ok(s) => s,
        Err(e) => {
            log::error!("Invalid UTF-8 in URL: {}", e);
            return -1;
        }
    };

    let mut registry = PLAYER_REGISTRY.lock().unwrap();
    match registry.get_mut(&player_id) {
        Some(player) => {
            match player.load_url(url_str) {
                Ok(_) => {
                    log::info!("Loaded URL: {}", url_str);
                    0
                }
                Err(e) => {
                    log::error!("Failed to load URL: {}", e);
                    -1
                }
            }
        }
        None => {
            log::error!("Invalid player ID: {}", player_id);
            -1
        }
    }
}

/// Start or resume playback
/// Returns: 0 on success, -1 on error
#[no_mangle]
pub extern "C" fn rust_audio_player_play(player_id: i64) -> i32 {
    let mut registry = PLAYER_REGISTRY.lock().unwrap();
    match registry.get_mut(&player_id) {
        Some(player) => {
            match player.play() {
                Ok(_) => 0,
                Err(e) => {
                    log::error!("Failed to play: {}", e);
                    -1
                }
            }
        }
        None => {
            log::error!("Invalid player ID: {}", player_id);
            -1
        }
    }
}

/// Pause playback
/// Returns: 0 on success, -1 on error
#[no_mangle]
pub extern "C" fn rust_audio_player_pause(player_id: i64) -> i32 {
    let mut registry = PLAYER_REGISTRY.lock().unwrap();
    match registry.get_mut(&player_id) {
        Some(player) => {
            match player.pause() {
                Ok(_) => 0,
                Err(e) => {
                    log::error!("Failed to pause: {}", e);
                    -1
                }
            }
        }
        None => {
            log::error!("Invalid player ID: {}", player_id);
            -1
        }
    }
}

/// Stop playback
/// Returns: 0 on success, -1 on error
#[no_mangle]
pub extern "C" fn rust_audio_player_stop(player_id: i64) -> i32 {
    let mut registry = PLAYER_REGISTRY.lock().unwrap();
    match registry.get_mut(&player_id) {
        Some(player) => {
            match player.stop() {
                Ok(_) => 0,
                Err(e) => {
                    log::error!("Failed to stop: {}", e);
                    -1
                }
            }
        }
        None => {
            log::error!("Invalid player ID: {}", player_id);
            -1
        }
    }
}

/// Seek to position in milliseconds
/// Returns: 0 on success, -1 on error
#[no_mangle]
pub extern "C" fn rust_audio_player_seek(player_id: i64, position_ms: i64) -> i32 {
    if position_ms < 0 {
        log::error!("Invalid position: {}", position_ms);
        return -1;
    }

    let mut registry = PLAYER_REGISTRY.lock().unwrap();
    match registry.get_mut(&player_id) {
        Some(player) => {
            match player.seek(position_ms as u64) {
                Ok(_) => 0,
                Err(e) => {
                    log::error!("Failed to seek: {}", e);
                    -1
                }
            }
        }
        None => {
            log::error!("Invalid player ID: {}", player_id);
            -1
        }
    }
}

/// Get current playback position in milliseconds
/// Returns: position in ms, or -1 on error
#[no_mangle]
pub extern "C" fn rust_audio_player_get_position(player_id: i64) -> i64 {
    let registry = PLAYER_REGISTRY.lock().unwrap();
    match registry.get(&player_id) {
        Some(player) => {
            let status = player.get_status();
            status.position_ms as i64
        }
        None => {
            log::error!("Invalid player ID: {}", player_id);
            -1
        }
    }
}

/// Get audio duration in milliseconds
/// Returns: duration in ms, or -1 if unknown
#[no_mangle]
pub extern "C" fn rust_audio_player_get_duration(player_id: i64) -> i64 {
    let registry = PLAYER_REGISTRY.lock().unwrap();
    match registry.get(&player_id) {
        Some(player) => {
            let status = player.get_status();
            status.duration_ms as i64
        }
        None => {
            log::error!("Invalid player ID: {}", player_id);
            -1
        }
    }
}

/// Get player state
/// Returns: 0=Idle, 1=Loading, 2=Ready, 3=Playing, 4=Paused, 5=Stopped, 6=Error, -1=Invalid player ID
#[no_mangle]
pub extern "C" fn rust_audio_player_get_state(player_id: i64) -> i32 {
    let registry = PLAYER_REGISTRY.lock().unwrap();
    match registry.get(&player_id) {
        Some(player) => {
            use crate::player::PlayerState;
            match player.get_state() {
                PlayerState::Idle => 0,
                PlayerState::Loading => 1,
                PlayerState::Ready => 2,
                PlayerState::Playing => 3,
                PlayerState::Paused => 4,
                PlayerState::Stopped => 5,
                PlayerState::Error => 6,
            }
        }
        None => {
            log::error!("Invalid player ID: {}", player_id);
            -1
        }
    }
}

/// Release/destroy player instance
/// Returns: 0 on success, -1 on error
#[no_mangle]
pub extern "C" fn rust_audio_player_release(player_id: i64) -> i32 {
    let mut registry = PLAYER_REGISTRY.lock().unwrap();
    match registry.remove(&player_id) {
        Some(_) => {
            log::info!("Released player with ID: {}", player_id);
            0
        }
        None => {
            log::error!("Invalid player ID: {}", player_id);
            -1
        }
    }
}
