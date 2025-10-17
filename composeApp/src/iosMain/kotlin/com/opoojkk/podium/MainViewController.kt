package com.opoojkk.podium

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.opoojkk.podium.platform.PlatformContext

fun MainViewController() = ComposeUIViewController {
    val environment = remember { createPodiumEnvironment(PlatformContext()) }
    DisposableEffect(Unit) {
        onDispose { environment.dispose() }
    }
    PodiumApp(environment)
}
