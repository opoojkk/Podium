// JNI bindings for JVM desktop platforms (macOS, Linux, Windows)
// Provides JNI-compatible interface to the audio player

use jni::JNIEnv;
use jni::objects::{JClass, JObject, JString};
use jni::sys::{jlong, jint, jstring};
use std::ffi::CString;

// Import the C FFI functions
use crate::ffi_bindings::*;

/// Create a new audio player instance
/// JNI signature: ()J
#[no_mangle]
pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeCreate(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    rust_audio_player_create()
}

/// Load audio file from path
/// JNI signature: (JLjava/lang/String;)I
#[no_mangle]
pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeLoadFile(
    env: JNIEnv,
    _class: JClass,
    player_id: jlong,
    path: jstring,
) -> jint {
    let path_obj = unsafe { JObject::from_raw(path) };
    let path_jstring = JString::from(path_obj);

    let path_str = match env.get_string(&path_jstring) {
        Ok(s) => s,
        Err(e) => {
            log::error!("Failed to get path string: {}", e);
            return -1;
        }
    };

    let path_cstr = match CString::new(path_str.to_str().unwrap_or("")) {
        Ok(s) => s,
        Err(e) => {
            log::error!("Failed to create CString: {}", e);
            return -1;
        }
    };

    rust_audio_player_load_file(player_id, path_cstr.as_ptr())
}

/// Load audio from URL
/// JNI signature: (JLjava/lang/String;)I
#[no_mangle]
pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeLoadUrl(
    env: JNIEnv,
    _class: JClass,
    player_id: jlong,
    url: jstring,
) -> jint {
    let url_obj = unsafe { JObject::from_raw(url) };
    let url_jstring = JString::from(url_obj);

    let url_str = match env.get_string(&url_jstring) {
        Ok(s) => s,
        Err(e) => {
            log::error!("Failed to get URL string: {}", e);
            return -1;
        }
    };

    let url_cstr = match CString::new(url_str.to_str().unwrap_or("")) {
        Ok(s) => s,
        Err(e) => {
            log::error!("Failed to create CString: {}", e);
            return -1;
        }
    };

    rust_audio_player_load_url(player_id, url_cstr.as_ptr())
}

/// Load audio from byte buffer
/// JNI signature: (J[B)I
#[no_mangle]
pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeLoadBuffer(
    _env: JNIEnv,
    _class: JClass,
    _player_id: jlong,
    _buffer: jni::sys::jbyteArray,
) -> jint {
    // TODO: Implement buffer loading if needed
    log::warn!("nativeLoadBuffer not yet implemented");
    -1
}

/// Start or resume playback
/// JNI signature: (J)I
#[no_mangle]
pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativePlay(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jint {
    rust_audio_player_play(player_id) as jint
}

/// Pause playback
/// JNI signature: (J)I
#[no_mangle]
pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativePause(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jint {
    rust_audio_player_pause(player_id) as jint
}

/// Stop playback
/// JNI signature: (J)I
#[no_mangle]
pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeStop(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jint {
    rust_audio_player_stop(player_id) as jint
}

/// Seek to position in milliseconds
/// JNI signature: (JJ)I
#[no_mangle]
pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeSeek(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
    position_ms: jlong,
) -> jint {
    rust_audio_player_seek(player_id, position_ms) as jint
}

/// Set volume (0.0 - 1.0)
/// JNI signature: (JF)I
#[no_mangle]
pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeSetVolume(
    _env: JNIEnv,
    _class: JClass,
    _player_id: jlong,
    _volume: jni::sys::jfloat,
) -> jint {
    // TODO: Implement volume control if needed
    log::warn!("nativeSetVolume not yet implemented");
    0 // Return success for now
}

/// Get current playback position in milliseconds
/// JNI signature: (J)J
#[no_mangle]
pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeGetPosition(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jlong {
    rust_audio_player_get_position(player_id)
}

/// Get audio duration in milliseconds
/// JNI signature: (J)J
#[no_mangle]
pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeGetDuration(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jlong {
    rust_audio_player_get_duration(player_id)
}

/// Get player state
/// JNI signature: (J)I
#[no_mangle]
pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeGetState(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jint {
    rust_audio_player_get_state(player_id)
}

/// Release/destroy player instance
/// JNI signature: (J)I
#[no_mangle]
pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeRelease(
    _env: JNIEnv,
    _class: JClass,
    player_id: jlong,
) -> jint {
    rust_audio_player_release(player_id) as jint
}
