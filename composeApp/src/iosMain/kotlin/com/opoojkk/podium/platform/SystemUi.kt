package com.opoojkk.podium.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
actual fun SetStatusBarColor(color: Color, darkIcons: Boolean) {
    // No-op: iOS handles system chrome separately on the native layer.
}
