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
        get() {
            // 只有在有多个目标且当前不是底部导航栏的主页面时才能返回
            // 如果当前在底部导航栏的任何一个主页面，应该退出应用而不是导航回去
            val isOnMainDestination = destinationStack.value.size == 1
            return !isOnMainDestination && destinationStack.value.size > 1
        }

    fun navigateTo(destination: PodiumDestination, singleTop: Boolean = true) {
        destinationStack.value = destinationStack.value.let { stack ->
            if (singleTop && stack.lastOrNull() == destination) {
                // 如果目标相同，不添加
                stack
            } else if (stack.size == 1) {
                // 如果当前堆栈只有一个元素（底部导航栏主页面），替换而不是添加
                listOf(destination)
            } else {
                // 其他情况，正常添加
                stack + destination
            }
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
