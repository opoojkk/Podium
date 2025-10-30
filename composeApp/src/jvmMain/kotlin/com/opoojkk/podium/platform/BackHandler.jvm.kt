package com.opoojkk.podium.platform

import androidx.compose.runtime.Composable

/**
 * JVM/Desktop 平台实现：桌面平台没有系统返回按钮，所以这里是空实现
 * 桌面应用通常只使用 UI 上的返回按钮
 */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // 桌面平台不需要特殊处理，因为没有系统返回按钮
    // 用户只能通过 UI 上的返回按钮来返回
}
