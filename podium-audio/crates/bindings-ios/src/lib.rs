// C FFI bindings for iOS
// Provides a C interface that can be called from Swift

use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_float, c_int, c_longlong};
use std::sync::Arc;
use parking_lot::Mutex;
use once_cell::sync::Lazy;
use std::collections::HashMap;

use podium_core::{AudioError, PlayerState};

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
    #[cfg(target_os = "ios")]
    {
        use log::LevelFilter;
        use oslog::OsLogger;

        // Initialize iOS logger (only once)
        let _ = OsLogger::new("com.opoojkk.podium.audio")
            .level_filter(LevelFilter::Debug)
            .init();
    }
}

// ============================================================================
// Helper Functions
// ============================================================================

unsafe fn cstr_to_string(cstr: *const c_char) -> Option<String> {
    if cstr.is_null() {
        return None;
    }
    CStr::from_ptr(cstr).to_str().ok().map(|s| s.to_string())
}

fn string_to_cstring(s: &str) -> *mut c_char {
    match CString::new(s) {
        Ok(cstr) => cstr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// Free a C string allocated by Rust
#[no_mangle]
pub extern "C" fn podium_free_string(ptr: *mut c_char) {
    if !ptr.is_null() {
        unsafe {
            let _ = CString::from_raw(ptr);
        }
    }
}

// ============================================================================
// Player Lifecycle
// ============================================================================

/// Create a new audio player instance
/// Returns a player ID (handle) for subsequent operations
/// Returns -1 on error
#[no_mangle]
pub extern "C" fn podium_player_create() -> c_longlong {
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
/// Returns 0 on success, -1 on error
#[no_mangle]
pub extern "C" fn podium_player_release(player_id: c_longlong) -> c_int {
    log::info!("Releasing player {}", player_id);

    let mut registry = PLAYER_REGISTRY.lock();
    if let Some(player_arc) = registry.remove(&player_id) {
        drop(registry);

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
/// Returns 0 on success, -1 on error
#[no_mangle]
pub extern "C" fn podium_player_load_file(
    player_id: c_longlong,
    path: *const c_char,
) -> c_int {
    let path_str = match unsafe { cstr_to_string(path) } {
        Some(s) => s,
        None => {
            log::error!("Invalid path string");
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
/// Returns 0 on success, -1 on error
#[no_mangle]
pub extern "C" fn podium_player_load_buffer(
    player_id: c_longlong,
    buffer: *const u8,
    buffer_len: usize,
) -> c_int {
    if buffer.is_null() || buffer_len == 0 {
        log::error!("Invalid buffer");
        return -1;
    }

    let data = unsafe { std::slice::from_raw_parts(buffer, buffer_len) };
    log::info!("Loading buffer: {} bytes", buffer_len);

    let registry = PLAYER_REGISTRY.lock();
    if let Some(player_arc) = registry.get(&player_id) {
        let mut player = player_arc.lock();
        match player.load_buffer(data) {
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
/// Returns 0 on success, -1 on error
#[no_mangle]
pub extern "C" fn podium_player_load_url(
    player_id: c_longlong,
    url: *const c_char,
) -> c_int {
    let url_str = match unsafe { cstr_to_string(url) } {
        Some(s) => s,
        None => {
            log::error!("Invalid URL string");
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
/// Returns 0 on success, -1 on error
#[no_mangle]
pub extern "C" fn podium_player_play(player_id: c_longlong) -> c_int {
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
/// Returns 0 on success, -1 on error
#[no_mangle]
pub extern "C" fn podium_player_pause(player_id: c_longlong) -> c_int {
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
/// Returns 0 on success, -1 on error
#[no_mangle]
pub extern "C" fn podium_player_stop(player_id: c_longlong) -> c_int {
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
/// Returns 0 on success, -1 on error
#[no_mangle]
pub extern "C" fn podium_player_seek(
    player_id: c_longlong,
    position_ms: c_longlong,
) -> c_int {
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
/// Returns 0 on success, -1 on error
#[no_mangle]
pub extern "C" fn podium_player_set_volume(
    player_id: c_longlong,
    volume: c_float,
) -> c_int {
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
/// Returns position in milliseconds, or -1 on error
#[no_mangle]
pub extern "C" fn podium_player_get_position(player_id: c_longlong) -> c_longlong {
    let registry = PLAYER_REGISTRY.lock();
    if let Some(player_arc) = registry.get(&player_id) {
        let player = player_arc.lock();
        player.get_position() as c_longlong
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Get duration (milliseconds)
/// Returns duration in milliseconds, or -1 on error
#[no_mangle]
pub extern "C" fn podium_player_get_duration(player_id: c_longlong) -> c_longlong {
    let registry = PLAYER_REGISTRY.lock();
    if let Some(player_arc) = registry.get(&player_id) {
        let player = player_arc.lock();
        player.get_duration() as c_longlong
    } else {
        log::error!("Invalid player ID: {}", player_id);
        -1
    }
}

/// Get player state
/// Returns state value (0-6), or -1 on error
/// States: 0=Idle, 1=Loading, 2=Ready, 3=Playing, 4=Paused, 5=Stopped, 6=Error
#[no_mangle]
pub extern "C" fn podium_player_get_state(player_id: c_longlong) -> c_int {
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
