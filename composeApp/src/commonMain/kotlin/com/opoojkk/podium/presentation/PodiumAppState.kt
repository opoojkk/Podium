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
    private val destinationStack: MutableState<List<PodiumDestination>>,
) {
    val currentDestination: PodiumDestination
        get() = destinationStack.value.last()

    val canGoBack: Boolean
        get() = destinationStack.value.size > 1

    fun navigateTo(destination: PodiumDestination, singleTop: Boolean = true) {
        destinationStack.value = destinationStack.value.let { stack ->
            if (singleTop && stack.lastOrNull() == destination) stack else stack + destination
        }
    }

    fun navigateBack(): Boolean {
        if (!canGoBack) return false
        destinationStack.value = destinationStack.value.dropLast(1)
        return true
    }
}

@Composable
fun rememberPodiumAppState(environment: PodiumEnvironment): PodiumAppState {
    val controller = remember(environment) { environment.createController() }
    val destinationState = remember { mutableStateOf(listOf(PodiumDestination.Home)) }
    return remember(controller) { PodiumAppState(controller, destinationState) }
}
