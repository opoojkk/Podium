/// Resolve the effective output sample rate. Prefers the device-selected rate when available,
/// otherwise falls back to the decoder's rate or the provided default.
pub fn effective_output_rate(
    selected_rate: u32,
    decoder_rate: Option<u32>,
    default_rate: u32,
) -> u32 {
    if selected_rate > 0 {
        selected_rate
    } else if let Some(rate) = decoder_rate {
        rate
    } else {
        default_rate
    }
}
