package com.opoojkk.podium

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.opoojkk.podium.platform.PlatformContext

fun main() = application {
    val windowState = rememberWindowState(
        size = DpSize(1280.dp, 800.dp),
        position = WindowPosition(Alignment.Center)
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "Podium - 播客客户端",
        state = windowState
    ) {
        val environment = remember { createPodiumEnvironment(PlatformContext()) }
        DisposableEffect(Unit) {
            onDispose { environment.dispose() }
        }
        PodiumApp(environment)
    }
}
