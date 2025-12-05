// Audio resampling and channel conversion


/// Audio resampler
pub struct Resampler {
    input_rate: u32,
    output_rate: u32,
    input_channels: u16,
    output_channels: u16,
}

impl Resampler {
    pub fn new(input_rate: u32, output_rate: u32, input_channels: u16, output_channels: u16) -> Self {
        Self {
            input_rate,
            output_rate,
            input_channels,
            output_channels,
        }
    }

    /// Resample and convert channels if needed
    pub fn process(&self, input: &[f32]) -> Vec<f32> {
        let mut output = input.to_vec();

        // First, convert channels if needed
        if self.input_channels != self.output_channels {
            output = self.convert_channels(&output);
        }

        // Then, resample if needed
        if self.input_rate != self.output_rate {
            output = self.resample(&output);
        }

        output
    }

    /// Convert between different channel configurations
    fn convert_channels(&self, input: &[f32]) -> Vec<f32> {
        match (self.input_channels, self.output_channels) {
            (1, 2) => {
                // Mono to stereo: duplicate each sample
                let mut output = Vec::with_capacity(input.len() * 2);
                for &sample in input {
                    output.push(sample);
                    output.push(sample);
                }
                output
            }
            (2, 1) => {
                // Stereo to mono: average L and R
                let mut output = Vec::with_capacity(input.len() / 2);
                for chunk in input.chunks_exact(2) {
                    output.push((chunk[0] + chunk[1]) * 0.5);
                }
                output
            }
            _ => {
                // Same channel count or unsupported conversion
                input.to_vec()
            }
        }
    }

    /// Simple linear interpolation resampling
    fn resample(&self, input: &[f32]) -> Vec<f32> {
        let ratio = self.output_rate as f64 / self.input_rate as f64;
        let input_frames = input.len() / self.output_channels as usize;
        let output_frames = (input_frames as f64 * ratio) as usize;
        let mut output = Vec::with_capacity(output_frames * self.output_channels as usize);

        for frame_idx in 0..output_frames {
            let src_frame = (frame_idx as f64 / ratio) as f64;
            let src_frame_floor = src_frame.floor() as usize;
            let src_frame_ceil = (src_frame_floor + 1).min(input_frames - 1);
            let frac = src_frame - src_frame_floor as f64;

            for ch in 0..self.output_channels as usize {
                let sample1 = input[src_frame_floor * self.output_channels as usize + ch];
                let sample2 = input[src_frame_ceil * self.output_channels as usize + ch];
                let interpolated = sample1 + (sample2 - sample1) * frac as f32;
                output.push(interpolated);
            }
        }

        output
    }
}

/// Check if resampling is needed
pub fn needs_resampling(input_rate: u32, output_rate: u32, input_channels: u16, output_channels: u16) -> bool {
    input_rate != output_rate || input_channels != output_channels
}
