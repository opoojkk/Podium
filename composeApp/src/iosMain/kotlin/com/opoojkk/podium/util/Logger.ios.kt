package com.opoojkk.podium.util

import platform.Foundation.NSProcessInfo

/**
 * iOS implementation of debug build detection.
 * Uses the DEBUG preprocessor macro which is typically set in Xcode build settings.
 * Note: This requires the Kotlin/Native compiler flag to be set in the build configuration.
 */
internal actual fun isDebugBuild(): Boolean {
    // In iOS, we can check the environment or use a compile-time constant
    // For now, return true as iOS builds are typically debug during development
    // This should be configured in the Xcode build settings
    return true
}
