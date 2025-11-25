package com.opoojkk.podium.util

import com.opoojkk.podium.BuildConfig

/**
 * Android implementation of debug build detection.
 * Uses BuildConfig.DEBUG which is set by the Android Gradle plugin.
 */
internal actual fun isDebugBuild(): Boolean = BuildConfig.DEBUG
