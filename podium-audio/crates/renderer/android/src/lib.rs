// Android audio renderer using Oboe

#[cfg(target_os = "android")]
mod oboe_renderer;

#[cfg(target_os = "android")]
pub use oboe_renderer::OboeRenderer;

#[cfg(not(target_os = "android"))]
pub struct OboeRenderer;

#[cfg(not(target_os = "android"))]
impl OboeRenderer {
    pub fn new(_spec: podium_renderer::AudioSpec) -> podium_core::Result<Self> {
        Err(podium_core::AudioError::InitializationError(
            "Oboe renderer is only available on Android".to_string(),
        ))
    }
}
