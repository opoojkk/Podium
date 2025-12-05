// iOS/macOS audio renderer using cpal

#[cfg(any(target_os = "ios", target_os = "macos"))]
mod cpal_renderer;

#[cfg(any(target_os = "ios", target_os = "macos"))]
pub use cpal_renderer::CpalRenderer;

#[cfg(not(any(target_os = "ios", target_os = "macos")))]
pub struct CpalRenderer;

#[cfg(not(any(target_os = "ios", target_os = "macos")))]
impl CpalRenderer {
    pub fn new(_spec: podium_renderer_api::AudioSpec) -> podium_core::Result<Self> {
        Err(podium_core::AudioError::InitializationError(
            "Cpal renderer is only available on iOS/macOS".to_string(),
        ))
    }
}
