package com.opoojkk.podium

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.opoojkk.podium.platform.PlatformContext

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Podium") {
        val environment = remember { createPodiumEnvironment(PlatformContext()) }
        DisposableEffect(Unit) {
            onDispose { environment.dispose() }
        }
        PodiumApp(environment)
    }
}
