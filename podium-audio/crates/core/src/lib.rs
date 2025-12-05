// Core types and traits for Podium audio player

pub mod callback;
pub mod error;
pub mod player;
pub mod state;

// Re-export commonly used types
pub use callback::{CallbackEvent, CallbackManager, PlayerCallback};
pub use error::{AudioError, Result};
pub use player::{AudioPlayer, Session};
pub use state::{PlaybackStatus, PlayerState, PlayerStateContainer};
