// Thread-safe callback mechanism for player events
// Addresses the issue of high-frequency JNI callbacks by batching and throttling

use crate::state::PlayerState;
use parking_lot::Mutex;
use std::sync::Arc;
use std::time::{Duration, Instant};

/// Player event types
#[derive(Debug, Clone)]
pub enum CallbackEvent {
    /// Player state changed
    StateChanged {
        old_state: PlayerState,
        new_state: PlayerState,
    },

    /// Playback position updated
    PositionChanged {
        position_ms: u64,
        duration_ms: u64,
    },

    /// Playback completed
    PlaybackCompleted,

    /// Playback error occurred
    Error { message: String },

    /// Buffering state changed
    BufferingChanged { buffering: bool },

    /// Volume changed
    VolumeChanged { volume: f32 },

    /// Playback rate changed
    PlaybackRateChanged { rate: f32 },
}

/// Player callback trait
/// Implementations should be lightweight and non-blocking
pub trait PlayerCallback: Send + Sync {
    /// Called when an event occurs
    /// This should return quickly to avoid blocking the audio thread
    fn on_event(&self, event: CallbackEvent);
}

/// Throttled callback wrapper
/// Prevents excessive callback frequency, especially for position updates
pub struct ThrottledCallback {
    inner: Arc<dyn PlayerCallback>,
    last_position_update: Arc<Mutex<Instant>>,
    position_update_interval: Duration,
}

impl ThrottledCallback {
    pub fn new(callback: Arc<dyn PlayerCallback>, update_interval_ms: u64) -> Self {
        Self {
            inner: callback,
            last_position_update: Arc::new(Mutex::new(Instant::now())),
            position_update_interval: Duration::from_millis(update_interval_ms),
        }
    }

    pub fn dispatch(&self, event: CallbackEvent) {
        match &event {
            CallbackEvent::PositionChanged { .. } => {
                // Throttle position updates
                let mut last_update = self.last_position_update.lock();
                if last_update.elapsed() >= self.position_update_interval {
                    *last_update = Instant::now();
                    self.inner.on_event(event);
                }
            }
            _ => {
                // Other events are not throttled
                self.inner.on_event(event);
            }
        }
    }
}

/// Callback manager for handling multiple callbacks
pub struct CallbackManager {
    callbacks: Arc<Mutex<Vec<Arc<ThrottledCallback>>>>,
}

impl CallbackManager {
    pub fn new() -> Self {
        Self {
            callbacks: Arc::new(Mutex::new(Vec::new())),
        }
    }

    pub fn add_callback(&self, callback: Arc<dyn PlayerCallback>, throttle_ms: u64) {
        let throttled = Arc::new(ThrottledCallback::new(callback, throttle_ms));
        self.callbacks.lock().push(throttled);
    }

    pub fn clear_callbacks(&self) {
        self.callbacks.lock().clear();
    }

    pub fn dispatch_event(&self, event: CallbackEvent) {
        let callbacks = self.callbacks.lock();
        for callback in callbacks.iter() {
            callback.dispatch(event.clone());
        }
    }
}

impl Default for CallbackManager {
    fn default() -> Self {
        Self::new()
    }
}
