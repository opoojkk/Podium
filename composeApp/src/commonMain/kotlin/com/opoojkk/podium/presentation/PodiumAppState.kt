package com.opoojkk.podium.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.opoojkk.podium.PodiumEnvironment
import com.opoojkk.podium.createController
import com.opoojkk.podium.navigation.PodiumDestination

class PodiumAppState internal constructor(
    val controller: PodiumController,
    private val selectedDestination: MutableState<PodiumDestination>,
) {
    val currentDestination: PodiumDestination
        get() = selectedDestination.value

    fun navigateTo(destination: PodiumDestination) {
        selectedDestination.value = destination
    }
}

@Composable
fun rememberPodiumAppState(environment: PodiumEnvironment): PodiumAppState {
    val controller = remember(environment) { environment.createController() }
    val destinationState = remember { mutableStateOf(PodiumDestination.Home) }
    return remember(controller) { PodiumAppState(controller, destinationState) }
}
