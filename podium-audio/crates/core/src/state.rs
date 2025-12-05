// Core audio player state management

use crate::error::{AudioError, Result};
use parking_lot::RwLock;
use std::sync::Arc;

/// Player state
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PlayerState {
    /// Player is idle (no audio loaded)
    Idle,
    /// Audio is loading
    Loading,
    /// Audio is loaded and ready to play
    Ready,
    /// Audio is currently playing
    Playing,
    /// Audio is paused
    Paused,
    /// Playback has stopped
    Stopped,
    /// Player encountered an error
    Error,
}

/// Playback status information
#[derive(Debug, Clone)]
pub struct PlaybackStatus {
    /// Current playback position in milliseconds
    pub position_ms: u64,
    /// Total duration in milliseconds
    pub duration_ms: u64,
    /// Current volume (0.0 - 1.0)
    pub volume: f32,
    /// Current playback speed/rate
    pub playback_rate: f32,
    /// Whether the player is buffering
    pub buffering: bool,
}

impl Default for PlaybackStatus {
    fn default() -> Self {
        Self {
            position_ms: 0,
            duration_ms: 0,
            volume: 1.0,
            playback_rate: 1.0,
            buffering: false,
        }
    }
}

/// Thread-safe player state container
#[derive(Clone)]
pub struct PlayerStateContainer {
    state: Arc<RwLock<PlayerState>>,
    status: Arc<RwLock<PlaybackStatus>>,
}

impl PlayerStateContainer {
    pub fn new() -> Self {
        Self {
            state: Arc::new(RwLock::new(PlayerState::Idle)),
            status: Arc::new(RwLock::new(PlaybackStatus::default())),
        }
    }

    pub fn get_state(&self) -> PlayerState {
        *self.state.read()
    }

    pub fn set_state(&self, new_state: PlayerState) {
        *self.state.write() = new_state;
        log::debug!("Player state changed to: {:?}", new_state);
    }

    pub fn get_status(&self) -> PlaybackStatus {
        self.status.read().clone()
    }

    pub fn update_status<F>(&self, f: F)
    where
        F: FnOnce(&mut PlaybackStatus),
    {
        let mut status = self.status.write();
        f(&mut status);
    }

    pub fn validate_state_transition(&self, from: PlayerState, to: PlayerState) -> Result<()> {
        let current = self.get_state();
        if current != from {
            return Err(AudioError::InvalidState(
                format!("Expected state {:?}, but current state is {:?}", from, current)
            ));
        }

        // Validate valid state transitions
        match (from, to) {
            // From Idle
            (PlayerState::Idle, PlayerState::Loading) => Ok(()),
            (PlayerState::Idle, PlayerState::Error) => Ok(()),

            // From Loading
            (PlayerState::Loading, PlayerState::Ready) => Ok(()),
            (PlayerState::Loading, PlayerState::Error) => Ok(()),

            // From Ready
            (PlayerState::Ready, PlayerState::Playing) => Ok(()),
            (PlayerState::Ready, PlayerState::Loading) => Ok(()),
            (PlayerState::Ready, PlayerState::Idle) => Ok(()),
            (PlayerState::Ready, PlayerState::Error) => Ok(()),

            // From Playing
            (PlayerState::Playing, PlayerState::Paused) => Ok(()),
            (PlayerState::Playing, PlayerState::Stopped) => Ok(()),
            (PlayerState::Playing, PlayerState::Error) => Ok(()),
            (PlayerState::Playing, PlayerState::Ready) => Ok(()), // For seeking

            // From Paused
            (PlayerState::Paused, PlayerState::Playing) => Ok(()),
            (PlayerState::Paused, PlayerState::Stopped) => Ok(()),
            (PlayerState::Paused, PlayerState::Error) => Ok(()),

            // From Stopped
            (PlayerState::Stopped, PlayerState::Playing) => Ok(()),
            (PlayerState::Stopped, PlayerState::Ready) => Ok(()),
            (PlayerState::Stopped, PlayerState::Idle) => Ok(()),
            (PlayerState::Stopped, PlayerState::Error) => Ok(()),

            // From Error
            (PlayerState::Error, PlayerState::Idle) => Ok(()),
            (PlayerState::Error, PlayerState::Loading) => Ok(()),

            // Invalid transitions
            _ => Err(AudioError::InvalidState(
                format!("Invalid state transition from {:?} to {:?}", from, to)
            )),
        }
    }
}

impl Default for PlayerStateContainer {
    fn default() -> Self {
        Self::new()
    }
}
