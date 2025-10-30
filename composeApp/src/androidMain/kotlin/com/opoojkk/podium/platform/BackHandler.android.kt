package com.opoojkk.podium.platform

import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler as AndroidBackHandler

/**
 * Android 平台实现：使用 AndroidX 的 BackHandler
 */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    AndroidBackHandler(enabled = enabled, onBack = onBack)
}
