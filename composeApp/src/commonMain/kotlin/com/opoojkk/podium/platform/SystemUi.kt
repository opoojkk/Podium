package com.opoojkk.podium.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Update the status bar color and icon appearance for the current platform.
 * Platforms without a status bar can provide a no-op implementation.
 */
@Composable
expect fun SetStatusBarColor(color: Color, darkIcons: Boolean)
