package com.opoojkk.podium.platform

import androidx.compose.runtime.Composable

/**
 * 跨平台的返回按钮处理
 * 在 Android 上处理系统返回按钮
 * 在其他平台上可能不需要特别处理（因为没有系统返回按钮）
 */
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
