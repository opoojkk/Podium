package com.opoojkk.podium.platform

import androidx.compose.runtime.Composable

/**
 * iOS 平台实现：iOS 使用手势返回（从左边缘滑动），系统会自动处理
 * 我们不需要特别拦截，所以这里是空实现
 */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS 平台使用原生的手势返回，不需要特殊处理
}
