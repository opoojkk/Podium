package com.opoojkk.podium.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

enum class PodiumDestination(
    val icon: ImageVector,
    val label: String,
) {
    Home(Icons.Default.Home, "首页"),
    Subscriptions(Icons.Default.LibraryMusic, "订阅"),
    Profile(Icons.Default.Person, "我的"),
}
