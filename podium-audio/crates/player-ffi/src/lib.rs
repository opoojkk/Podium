// FFI bridge for Podium Audio
// Provides C ABI + JNI entrypoints compatible with the previous rust-audio-player API.

use once_cell::sync::Lazy;
use parking_lot::Mutex;
use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use podium_core::{
    AudioError, AudioPlayer, PlaybackStatus, PlayerCallback, PlayerState, PlayerStateContainer,
    Result,
};
use podium_decode::AudioDecoder;
use podium_demux::Demuxer;
use podium_ringbuffer::SharedRingBuffer;
use podium_source_buffer::NetworkSource;
use std::collections::HashMap;
use std::fs::File;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Once};
use std::thread;

/// Minimal player implementation wired to Podium core types.
/// This currently manages state only; audio pipeline integration can be layered in later.
struct PodiumPlayer {
    state: PlayerStateContainer,
    callback: Option<Arc<dyn PlayerCallback>>,
    loaded: bool,
    /// Playback engine (decoder + renderer)
    engine: Option<PlaybackEngine>,
    last_source: Option<SourceKind>,
}

impl PodiumPlayer {
    fn new() -> Self {
        log::info!("PodiumPlayer::new");
        Self {
            state: PlayerStateContainer::new(),
            callback: None,
            loaded: false,
            engine: None,
            last_source: None,
        }
    }

    fn ensure_loaded(&self) -> Result<()> {
        if self.loaded {
            Ok(())
        } else {
            Err(podium_core::AudioError::InvalidState(
                "Audio not loaded".to_string(),
            ))
        }
    }

    fn start_engine(&mut self, source: SourceKind, start_position_ms: u64) -> Result<()> {
        // If already running with same source, just seek on decoder
        if let Some(engine) = &mut self.engine {
            if let Some(prev) = &self.last_source {
                if prev == &source {
                    log::info!("[engine] reuse existing engine, seek to {}", start_position_ms);
                    engine.seek_to(start_position_ms)?;
                    return Ok(());
                }
            }
        }

        // Stop old engine completely before starting new one
        if let Some(mut eng) = self.engine.take() {
            log::info!("[engine] stopping old engine before starting new one");
            eng.stop();
            // Engine is now fully stopped (stream paused, thread joined)
            drop(eng);
            log::info!("[engine] old engine stopped and dropped");
        }

        let desc = match &source {
            SourceKind::Http(u) => format!("http {}", u),
            SourceKind::File(p) => format!("file {}", p),
        };
        log::info!("[engine] starting new engine: {}", desc);
        self.last_source = Some(source.clone());
        let mut engine = PlaybackEngine::new(source, self.state.clone())?;
        engine.seek_to(start_position_ms)?;
        self.engine = Some(engine);
        log::info!("[engine] new engine started successfully");
        Ok(())
    }
}

impl AudioPlayer for PodiumPlayer {
    fn load_file(&mut self, _path: &str) -> Result<()> {
        log::info!("load_file called");
        self.state.set_state(PlayerState::Loading);
        self.state.update_status(|status| {
            status.position_ms = 0;
            status.duration_ms = 0;
            status.buffering = false;
        });
        self.loaded = true;
        self.start_engine(SourceKind::File(_path.to_string()), 0)?;
        self.state.set_state(PlayerState::Ready);
        Ok(())
    }

    fn load_url(&mut self, _url: &str) -> Result<()> {
        log::info!("load_url called");
        self.state.set_state(PlayerState::Loading);
        self.state.update_status(|status| {
            status.position_ms = 0;
            status.duration_ms = 0;
            status.buffering = true;
        });
        self.loaded = true;
        self.start_engine(SourceKind::Http(_url.to_string()), 0)?;
        self.state.set_state(PlayerState::Ready);
        Ok(())
    }

    fn load_buffer(&mut self, _buffer: &[u8]) -> Result<()> {
        log::info!("load_buffer called ({} bytes)", _buffer.len());
        self.state.set_state(PlayerState::Loading);
        self.state.update_status(|status| {
            status.position_ms = 0;
            status.duration_ms = 0;
            status.buffering = false;
        });
        self.loaded = true;
        // For buffer mode, write to temp file and play as file
        let tmp_path = std::env::temp_dir().join("podium_audio_buffer.tmp");
        std::fs::write(&tmp_path, _buffer)
            .map_err(|e| AudioError::IoError(format!("write temp failed: {e}")))?;
        self.start_engine(SourceKind::File(
            tmp_path.to_string_lossy().to_string(),
        ), 0)?;
        self.state.set_state(PlayerState::Ready);
        Ok(())
    }

    fn play(&mut self) -> Result<()> {
        log::info!("play called");
        self.ensure_loaded()?;
        if let Some(engine) = &mut self.engine {
            engine.play();
        }
        self.state.set_state(PlayerState::Playing);
        self.state.update_status(|status| status.buffering = false);
        Ok(())
    }

    fn pause(&mut self) -> Result<()> {
        log::info!("pause called");
        if let Some(engine) = &mut self.engine {
            engine.pause();
        }
        self.state.set_state(PlayerState::Paused);
        Ok(())
    }

    fn stop(&mut self) -> Result<()> {
        log::info!("stop called");
        if let Some(engine) = &mut self.engine {
            engine.stop();
        }
        self.state.set_state(PlayerState::Stopped);
        self.state.update_status(|status| status.position_ms = 0);
        if let Some(engine) = &self.engine {
            engine.position_ms.store(0, Ordering::SeqCst);
        }
        Ok(())
    }

    fn seek(&mut self, position_ms: u64) -> Result<()> {
        log::info!("seek called -> {} ms", position_ms);
        self.ensure_loaded()?;
        if let Some(src) = self.last_source.clone() {
            self.start_engine(src, position_ms)?;
            self.state.update_status(|status| {
                status.position_ms = position_ms;
            });
            self.state.set_state(PlayerState::Ready);
        } else {
            log::warn!("seek requested but no source cached");
        }
        Ok(())
    }

    fn set_volume(&mut self, volume: f32) -> Result<()> {
        log::info!("set_volume called -> {}", volume);
        if !(0.0..=1.0).contains(&volume) {
            return Err(podium_core::AudioError::InvalidState(format!(
                "Volume out of range: {}",
                volume
            )));
        }
        self.state.update_status(|status| status.volume = volume);
        Ok(())
    }

    fn set_playback_rate(&mut self, rate: f32) -> Result<()> {
        log::info!("set_playback_rate called -> {}", rate);
        if rate <= 0.0 {
            return Err(podium_core::AudioError::InvalidState(
                "Playback rate must be > 0".to_string(),
            ));
        }
        self.state.update_status(|status| status.playback_rate = rate);
        Ok(())
    }

    fn get_state(&self) -> PlayerState {
        self.state.get_state()
    }

    fn get_status(&self) -> PlaybackStatus {
        let mut status = self.state.get_status();
        if let Some(engine) = &self.engine {
            status.position_ms = engine.position_ms.load(Ordering::SeqCst);
            let dur = engine.duration_ms.load(Ordering::SeqCst);
            if dur > 0 {
                status.duration_ms = dur;
            }
        }
        status
    }

    fn set_callback(&mut self, callback: Option<Arc<dyn PlayerCallback>>) {
        self.callback = callback;
    }

    fn release(&mut self) -> Result<()> {
        log::info!("release called");
        self.loaded = false;
        if let Some(mut engine) = self.engine.take() {
            engine.stop();
        }
        self.state.set_state(PlayerState::Idle);
        Ok(())
    }

    fn as_any(&self) -> &dyn std::any::Any {
        self
    }
}

static PLAYER_REGISTRY: Lazy<Mutex<HashMap<i64, PodiumPlayer>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));
static NEXT_PLAYER_ID: Lazy<Mutex<i64>> = Lazy::new(|| Mutex::new(1));
static INIT_LOGGER: Once = Once::new();

fn init_logging() {
    INIT_LOGGER.call_once(|| {
        // Keep it simple: try env_logger if available, otherwise no-op.
        let _ = env_logger::builder()
            .is_test(false)
            .filter_level(log::LevelFilter::Info)
            .try_init();
    });
}

fn register_player(player: PodiumPlayer) -> i64 {
    init_logging();
    let mut next = NEXT_PLAYER_ID.lock();
    let id = *next;
    *next += 1;
    drop(next);

    PLAYER_REGISTRY.lock().insert(id, player);
    id
}

fn with_player_mut<R>(id: i64, f: impl FnOnce(&mut PodiumPlayer) -> Result<R>) -> Result<R> {
    let mut registry = PLAYER_REGISTRY.lock();
    let player = registry
        .get_mut(&id)
        .ok_or_else(|| podium_core::AudioError::InvalidState("Invalid player ID".into()))?;
    f(player)
}

fn with_player<R>(id: i64, f: impl FnOnce(&PodiumPlayer) -> Result<R>) -> Result<R> {
    let registry = PLAYER_REGISTRY.lock();
    let player = registry
        .get(&id)
        .ok_or_else(|| podium_core::AudioError::InvalidState("Invalid player ID".into()))?;
    f(player)
}

fn to_code(result: Result<()>) -> i32 {
    match result {
        Ok(_) => 0,
        Err(err) => {
            log::error!("FFI error: {}", err);
            -1
        }
    }
}

// -----------------------------------------------------------------------------
// Playback engine
// -----------------------------------------------------------------------------

#[derive(Clone, PartialEq)]
enum SourceKind {
    Http(String),
    File(String),
}

struct PlaybackEngine {
    ring: SharedRingBuffer,
    position_ms: Arc<AtomicU64>,
    duration_ms: Arc<AtomicU64>,
    playing: Arc<AtomicBool>,
    stop_flag: Arc<AtomicBool>,
    seek_request: Arc<AtomicU64>,
    _render_thread: Option<thread::JoinHandle<()>>,
    // Store the audio stream so we can properly stop it
    audio_stream: Arc<Mutex<Option<cpal::Stream>>>,
}

impl PlaybackEngine {
    fn new(source: SourceKind, state: PlayerStateContainer) -> Result<Self> {
        // Start with ~5s buffer for stereo f32 at 48k
        let ring = SharedRingBuffer::new(48000 * 2 * 5);
        let position_ms = Arc::new(AtomicU64::new(0));
        let duration_ms = Arc::new(AtomicU64::new(0));
        let playing = Arc::new(AtomicBool::new(false));
        let stop_flag = Arc::new(AtomicBool::new(false));
        let seek_request = Arc::new(AtomicU64::new(0));
        let audio_stream = Arc::new(Mutex::new(None));

        // Decoder thread
        let ring_clone = ring.clone();
        let pos_clone = position_ms.clone();
        let dur_clone = duration_ms.clone();
        let play_flag = playing.clone();
        let stop = stop_flag.clone();
        let seek = seek_request.clone();
        let stream_holder = audio_stream.clone();

        let handle = thread::spawn(move || {
            if let Err(e) = Self::decode_loop(
                source,
                ring_clone,
                pos_clone,
                dur_clone,
                play_flag,
                stop,
                seek,
                state,
                stream_holder,
            ) {
                log::error!("decode loop error: {}", e);
            }
        });

        Ok(Self {
            ring,
            position_ms,
            duration_ms,
            playing,
            stop_flag,
            seek_request,
            _render_thread: Some(handle),
            audio_stream,
        })
    }

    fn stop(&mut self) {
        log::info!("[engine] stopping playback engine");

        // Stop playback flag first
        self.playing.store(false, Ordering::SeqCst);

        // Explicitly pause and drop the audio stream to prevent it from continuing to play
        if let Some(stream) = self.audio_stream.lock().take() {
            log::info!("[engine] pausing and dropping audio stream");
            // The stream will be paused and dropped when it goes out of scope
            let _ = stream.pause();
            drop(stream);
        }

        // Signal the decoder thread to stop
        self.stop_flag.store(true, Ordering::SeqCst);

        // Wait for decoder thread to finish
        if let Some(handle) = self._render_thread.take() {
            log::info!("[engine] waiting for decoder thread to finish");
            let _ = handle.join();
        }

        log::info!("[engine] playback engine stopped");
    }

    fn pause(&mut self) {
        self.playing.store(false, Ordering::SeqCst);
    }

    fn play(&mut self) {
        self.playing.store(true, Ordering::SeqCst);
    }

    fn seek_to(&mut self, position_ms: u64) -> Result<()> {
        self.ring.clear();
        self.position_ms.store(position_ms, Ordering::SeqCst);
        self.seek_request.store(position_ms, Ordering::SeqCst);
        Ok(())
    }

    fn decode_loop(
        source: SourceKind,
        ring: SharedRingBuffer,
        pos_ms: Arc<AtomicU64>,
        dur_ms: Arc<AtomicU64>,
        playing: Arc<AtomicBool>,
        stop_flag: Arc<AtomicBool>,
        seek_request: Arc<AtomicU64>,
        state: PlayerStateContainer,
        stream_holder: Arc<Mutex<Option<cpal::Stream>>>,
    ) -> Result<()> {
        // Build MediaSource
        let hint_path = match &source {
            SourceKind::File(p) => Some(p.clone()),
            SourceKind::Http(_) => None,
        };

        let media_source: Box<dyn symphonia::core::io::MediaSource> = match source {
            SourceKind::File(path) => {
                let file = File::open(&path)
                    .map_err(|e| AudioError::IoError(format!("open file {}: {}", path, e)))?;
                Box::new(file)
            }
            SourceKind::Http(url) => {
                log::info!("[engine] using HttpRangeSource url={}", url);
                let ns = NetworkSource::from_http_range(url)?;
                Box::new(ns)
            }
        };

        let hint = if let Some(p) = hint_path {
            podium_demux::Demuxer::create_hint_from_path(&p)
        } else {
            podium_demux::Demuxer::create_hint_from_path("stream.mp3")
        };

        let mut demuxer = Demuxer::from_media_source(media_source, hint)?;
        let track_info = demuxer.get_track_info()?;
        dur_ms.store(track_info.duration_ms, Ordering::SeqCst);
        log::info!(
            "[engine] track sample_rate={} channels={} duration_ms={}",
            track_info.sample_rate,
            track_info.channels,
            track_info.duration_ms
        );
        // Resize ring to ~5s of audio
        let desired_channels = track_info.channels.max(1) as usize;
        let desired_sr = track_info.sample_rate.max(1);
        ring.resize((desired_sr as usize) * desired_channels * 5);

        let mut decoder = AudioDecoder::from_demuxer(&demuxer)?;

        // Setup renderer (cpal)
        let host = cpal::default_host();
        let device = host
            .default_output_device()
            .ok_or_else(|| AudioError::DeviceError("no default output device".into()))?;
        let config = device.default_output_config().map_err(|e| {
            AudioError::DeviceError(format!("output config failed: {}", e))
        })?;

        let sample_rate = config.sample_rate().0;
        let channels = config.channels() as usize;
        let mut out_channels = track_info.channels as usize;
        if out_channels == 1 && channels >= 2 {
            out_channels = 2; // upmix mono to stereo
        }

        let err_fn = |err| log::error!("[engine] output stream error: {}", err);
        let ring_for_cb = ring.clone();
        let play_flag_for_cb = playing.clone();
        let mut last_underflow = 0;
        let stream = match config.sample_format() {
            cpal::SampleFormat::F32 => device
                .build_output_stream(
                    &config.config(),
                    move |data: &mut [f32], _| {
                        if !play_flag_for_cb.load(Ordering::SeqCst) {
                            data.fill(0.0);
                            return;
                        }
                        let read = ring_for_cb.read(data);
                        if read < data.len() {
                            data[read..].fill(0.0);
                            last_underflow += 1;
                            if last_underflow % 10 == 0 {
                                log::warn!("[engine] audio underflow count={}", last_underflow);
                            }
                        }
                    },
                    err_fn,
                    None,
                )
                .map_err(|e| AudioError::PlaybackError(format!("build stream: {}", e)))?,
            _ => {
                return Err(AudioError::UnsupportedFormat(
                    "Only f32 sample format supported".into(),
                ))
            }
        };
        stream
            .play()
            .map_err(|e| AudioError::PlaybackError(format!("stream play: {}", e)))?;

        // Store the stream so it can be properly stopped later
        *stream_holder.lock() = Some(stream);
        log::info!("[engine] audio stream created and stored");

        playing.store(false, Ordering::SeqCst); // start paused; play() will toggle
        state.set_state(PlayerState::Ready);

        // Decode loop
        loop {
            if stop_flag.load(Ordering::SeqCst) {
                log::info!("[engine] stop requested");
                break;
            }

            // If not playing, still allow prebuffering until ring is mostly full
            if !playing.load(Ordering::SeqCst) && ring.fullness() > 0.9 {
                thread::sleep(std::time::Duration::from_millis(10));
                continue;
            }

            // Handle seek request
            let target_ms = seek_request.swap(0, Ordering::SeqCst);
            if target_ms > 0 {
                let _ = demuxer.seek(target_ms);
                pos_ms.store(target_ms, Ordering::SeqCst);
                ring.clear();
                log::info!("[engine] decoder seek to {} ms", target_ms);
            }
            match demuxer.next_packet() {
                Ok(packet) => {
                    let mut pcm = decoder.decode(&packet)?;
                    // If needed, upmix mono to stereo
                    if out_channels == 2 && track_info.channels == 1 {
                        let mut stereo = Vec::with_capacity(pcm.len() * 2);
                        for s in pcm.iter() {
                            stereo.push(*s);
                            stereo.push(*s);
                        }
                        pcm = stereo;
                    }
                    let written = ring.write(&pcm);
                    if written < pcm.len() {
                        log::debug!(
                            "[engine] ring full, dropped {} samples",
                            pcm.len() - written
                        );
                    }
                    let frames = written / out_channels;
                    if playing.load(Ordering::SeqCst) {
                        let inc_ms = (frames as u64 * 1000) / sample_rate as u64;
                        let new_pos = pos_ms.fetch_add(inc_ms, Ordering::SeqCst) + inc_ms;
                        if frames > 0 && new_pos % 1000 == 0 {
                            log::debug!(
                                "[engine] progress pos_ms={} ring_fullness={:.2}",
                                new_pos,
                                ring.fullness()
                            );
                        }
                    }
                }
                Err(e) => {
                    log::info!("[engine] demux end or error: {}", e);
                    break;
                }
            }
        }

        // Clean up the audio stream when decode loop exits
        log::info!("[engine] decode loop finished, cleaning up audio stream");
        if let Some(stream) = stream_holder.lock().take() {
            let _ = stream.pause();
            drop(stream);
            log::info!("[engine] audio stream cleaned up");
        }

        state.set_state(PlayerState::Stopped);
        playing.store(false, Ordering::SeqCst);
        Ok(())
    }
}

// -------------------------------
// C ABI (iOS/macOS/others)
// -------------------------------
#[no_mangle]
pub extern "C" fn rust_audio_player_create() -> i64 {
    register_player(PodiumPlayer::new())
}

#[no_mangle]
pub extern "C" fn rust_audio_player_load_file(player_id: i64, path: *const std::os::raw::c_char) -> i32 {
    if path.is_null() {
        return -1;
    }
    let c_str = unsafe { std::ffi::CStr::from_ptr(path) };
    match c_str.to_str() {
        Ok(path_str) => to_code(with_player_mut(player_id, |p| p.load_file(path_str))),
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn rust_audio_player_load_url(player_id: i64, url: *const std::os::raw::c_char) -> i32 {
    if url.is_null() {
        return -1;
    }
    let c_str = unsafe { std::ffi::CStr::from_ptr(url) };
    match c_str.to_str() {
        Ok(url_str) => to_code(with_player_mut(player_id, |p| p.load_url(url_str))),
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn rust_audio_player_play(player_id: i64) -> i32 {
    to_code(with_player_mut(player_id, |p| p.play()))
}

#[no_mangle]
pub extern "C" fn rust_audio_player_pause(player_id: i64) -> i32 {
    to_code(with_player_mut(player_id, |p| p.pause()))
}

#[no_mangle]
pub extern "C" fn rust_audio_player_stop(player_id: i64) -> i32 {
    to_code(with_player_mut(player_id, |p| p.stop()))
}

#[no_mangle]
pub extern "C" fn rust_audio_player_seek(player_id: i64, position_ms: i64) -> i32 {
    to_code(with_player_mut(player_id, |p| p.seek(position_ms as u64)))
}

#[no_mangle]
pub extern "C" fn rust_audio_player_get_position(player_id: i64) -> i64 {
    match with_player(player_id, |p| Ok(p.get_status().position_ms)) {
        Ok(pos) => pos as i64,
        Err(err) => {
            log::error!("Failed to get position: {}", err);
            -1
        }
    }
}

#[no_mangle]
pub extern "C" fn rust_audio_player_get_duration(player_id: i64) -> i64 {
    match with_player(player_id, |p| Ok(p.get_status().duration_ms)) {
        Ok(dur) => dur as i64,
        Err(err) => {
            log::error!("Failed to get duration: {}", err);
            -1
        }
    }
}

#[no_mangle]
pub extern "C" fn rust_audio_player_get_state(player_id: i64) -> i32 {
    match with_player(player_id, |p| Ok(p.get_state())) {
        Ok(state) => match state {
            PlayerState::Idle => 0,
            PlayerState::Loading => 1,
            PlayerState::Ready => 2,
            PlayerState::Playing => 3,
            PlayerState::Paused => 4,
            PlayerState::Stopped => 5,
            PlayerState::Error => 6,
        },
        Err(err) => {
            log::error!("Failed to get state: {}", err);
            -1
        }
    }
}

#[no_mangle]
pub extern "C" fn rust_audio_player_release(player_id: i64) -> i32 {
    let mut registry = PLAYER_REGISTRY.lock();
    if let Some(mut player) = registry.remove(&player_id) {
        to_code(player.release())
    } else {
        -1
    }
}

// -------------------------------
// JNI bindings for Android/JVM
// -------------------------------
#[cfg(any(feature = "android", feature = "desktop"))]
mod jni_bridge {
    use super::*;
    use jni::objects::{JByteArray, JClass, JString};
    use jni::sys::{jfloat, jint, jlong, jstring};
    use jni::JNIEnv;

    fn jstring_to_string(env: &mut JNIEnv, jstr: &JString) -> Result<String> {
        let java_str = env
            .get_string(jstr)
            .map_err(|e| podium_core::AudioError::Other(e.to_string()))?;
        Ok(java_str.into())
    }

    fn string_to_jstring(env: &JNIEnv, s: &str) -> Result<jstring> {
        env.new_string(s)
            .map(|j| j.into_raw())
            .map_err(|e| podium_core::AudioError::Other(e.to_string()))
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeCreate(
        _env: JNIEnv,
        _class: JClass,
    ) -> jlong {
        register_player(PodiumPlayer::new())
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeLoadFile(
        mut env: JNIEnv,
        _class: JClass,
        player_id: jlong,
        path: JString,
    ) -> jint {
        match jstring_to_string(&mut env, &path) {
            Ok(p) => to_code(with_player_mut(player_id, |player| player.load_file(&p))) as jint,
            Err(err) => {
                log::error!("Failed to read path: {}", err);
                -1
            }
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeLoadUrl(
        mut env: JNIEnv,
        _class: JClass,
        player_id: jlong,
        url: JString,
    ) -> jint {
        match jstring_to_string(&mut env, &url) {
            Ok(u) => to_code(with_player_mut(player_id, |player| player.load_url(&u))) as jint,
            Err(err) => {
                log::error!("Failed to read URL: {}", err);
                -1
            }
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeLoadBuffer(
        env: JNIEnv,
        _class: JClass,
        player_id: jlong,
        buffer: JByteArray,
    ) -> jint {
        match env.convert_byte_array(buffer) {
            Ok(data) => to_code(with_player_mut(player_id, |p| p.load_buffer(&data))) as jint,
            Err(err) => {
                log::error!("Failed to convert buffer: {}", err);
                -1
            }
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativePlay(
        _env: JNIEnv,
        _class: JClass,
        player_id: jlong,
    ) -> jint {
        to_code(with_player_mut(player_id, |p| p.play())) as jint
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativePause(
        _env: JNIEnv,
        _class: JClass,
        player_id: jlong,
    ) -> jint {
        to_code(with_player_mut(player_id, |p| p.pause())) as jint
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeStop(
        _env: JNIEnv,
        _class: JClass,
        player_id: jlong,
    ) -> jint {
        to_code(with_player_mut(player_id, |p| p.stop())) as jint
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeSeek(
        _env: JNIEnv,
        _class: JClass,
        player_id: jlong,
        position_ms: jlong,
    ) -> jint {
        to_code(with_player_mut(player_id, |p| p.seek(position_ms as u64))) as jint
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeSetVolume(
        _env: JNIEnv,
        _class: JClass,
        player_id: jlong,
        volume: jfloat,
    ) -> jint {
        to_code(with_player_mut(player_id, |p| p.set_volume(volume))) as jint
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeGetPosition(
        _env: JNIEnv,
        _class: JClass,
        player_id: jlong,
    ) -> jlong {
        with_player(player_id, |p| Ok(p.get_status().position_ms))
            .map(|pos| pos as jlong)
            .unwrap_or(-1)
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeGetDuration(
        _env: JNIEnv,
        _class: JClass,
        player_id: jlong,
    ) -> jlong {
        with_player(player_id, |p| Ok(p.get_status().duration_ms))
            .map(|dur| dur as jlong)
            .unwrap_or(-1)
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeGetState(
        _env: JNIEnv,
        _class: JClass,
        player_id: jlong,
    ) -> jint {
        with_player(player_id, |p| Ok(p.get_state()))
            .map(|state| match state {
                PlayerState::Idle => 0,
                PlayerState::Loading => 1,
                PlayerState::Ready => 2,
                PlayerState::Playing => 3,
                PlayerState::Paused => 4,
                PlayerState::Stopped => 5,
                PlayerState::Error => 6,
            })
            .unwrap_or(-1)
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeRelease(
        _env: JNIEnv,
        _class: JClass,
        player_id: jlong,
    ) -> jint {
        let mut registry = PLAYER_REGISTRY.lock();
        if let Some(mut player) = registry.remove(&player_id) {
            to_code(player.release()) as jint
        } else {
            -1
        }
    }

    // JVM desktop bindings mirror the Android signatures but use RustAudioPlayerJvm class names.
    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeCreate(
        _env: JNIEnv,
        _class: JClass,
    ) -> jlong {
        register_player(PodiumPlayer::new())
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeLoadFile(
        mut env: JNIEnv,
        _class: JClass,
        player_id: jlong,
        path: JString,
    ) -> jint {
        match jstring_to_string(&mut env, &path) {
            Ok(p) => to_code(with_player_mut(player_id, |player| player.load_file(&p))) as jint,
            Err(err) => {
                log::error!("Failed to read path: {}", err);
                -1
            }
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeLoadUrl(
        mut env: JNIEnv,
        _class: JClass,
        player_id: jlong,
        url: JString,
    ) -> jint {
        match jstring_to_string(&mut env, &url) {
            Ok(u) => to_code(with_player_mut(player_id, |player| player.load_url(&u))) as jint,
            Err(err) => {
                log::error!("Failed to read URL: {}", err);
                -1
            }
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeLoadBuffer(
        env: JNIEnv,
        _class: JClass,
        player_id: jlong,
        buffer: JByteArray,
    ) -> jint {
        match env.convert_byte_array(buffer) {
            Ok(data) => to_code(with_player_mut(player_id, |p| p.load_buffer(&data))) as jint,
            Err(err) => {
                log::error!("Failed to convert buffer: {}", err);
                -1
            }
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativePlay(
        _env: JNIEnv,
        _class: JClass,
        player_id: jlong,
    ) -> jint {
        to_code(with_player_mut(player_id, |p| p.play())) as jint
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativePause(
        _env: JNIEnv,
        _class: JClass,
        player_id: jlong,
    ) -> jint {
        to_code(with_player_mut(player_id, |p| p.pause())) as jint
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeStop(
        _env: JNIEnv,
        _class: JClass,
        player_id: jlong,
    ) -> jint {
        to_code(with_player_mut(player_id, |p| p.stop())) as jint
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeSeek(
        _env: JNIEnv,
        _class: JClass,
        player_id: jlong,
        position_ms: jlong,
    ) -> jint {
        to_code(with_player_mut(player_id, |p| p.seek(position_ms as u64))) as jint
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeSetVolume(
        _env: JNIEnv,
        _class: JClass,
        player_id: jlong,
        volume: jfloat,
    ) -> jint {
        to_code(with_player_mut(player_id, |p| p.set_volume(volume))) as jint
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeGetPosition(
        _env: JNIEnv,
        _class: JClass,
        player_id: jlong,
    ) -> jlong {
        with_player(player_id, |p| Ok(p.get_status().position_ms))
            .map(|pos| pos as jlong)
            .unwrap_or(-1)
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeGetDuration(
        _env: JNIEnv,
        _class: JClass,
        player_id: jlong,
    ) -> jlong {
        with_player(player_id, |p| Ok(p.get_status().duration_ms))
            .map(|dur| dur as jlong)
            .unwrap_or(-1)
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeGetState(
        _env: JNIEnv,
        _class: JClass,
        player_id: jlong,
    ) -> jint {
        with_player(player_id, |p| Ok(p.get_state()))
            .map(|state| match state {
                PlayerState::Idle => 0,
                PlayerState::Loading => 1,
                PlayerState::Ready => 2,
                PlayerState::Playing => 3,
                PlayerState::Paused => 4,
                PlayerState::Stopped => 5,
                PlayerState::Error => 6,
            })
            .unwrap_or(-1)
    }

    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayerJvm_nativeRelease(
        _env: JNIEnv,
        _class: JClass,
        player_id: jlong,
    ) -> jint {
        let mut registry = PLAYER_REGISTRY.lock();
        if let Some(mut player) = registry.remove(&player_id) {
            to_code(player.release()) as jint
        } else {
            -1
        }
    }

    // Optional metadata stub to keep interface compatibility; returns "{}".
    #[no_mangle]
    pub extern "system" fn Java_com_opoojkk_podium_audio_RustAudioPlayer_nativeGetMetadataJson(
        env: JNIEnv,
        _class: JClass,
        _player_id: jlong,
    ) -> jstring {
        string_to_jstring(&env, "{}").unwrap_or(std::ptr::null_mut())
    }
}
