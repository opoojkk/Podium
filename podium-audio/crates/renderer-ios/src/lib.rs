// Cross-platform audio renderer using cpal
// Supports: iOS, macOS, Windows, Linux (all non-Android platforms)

#[cfg(not(target_os = "android"))]
mod cpal_renderer;

#[cfg(not(target_os = "android"))]
pub use cpal_renderer::CpalRenderer;

#[cfg(target_os = "android")]
pub struct CpalRenderer;

#[cfg(target_os = "android")]
impl CpalRenderer {
    pub fn new(_spec: podium_renderer_api::AudioSpec) -> podium_core::Result<Self> {
        Err(podium_core::AudioError::InitializationError(
            "Cpal renderer is not available on Android (use Oboe instead)".to_string(),
        ))
    }
}
