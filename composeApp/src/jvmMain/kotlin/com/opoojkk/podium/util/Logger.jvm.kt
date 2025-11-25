package com.opoojkk.podium.util

/**
 * JVM implementation of debug build detection.
 * Checks if assertions are enabled, which is a common indicator of debug builds.
 * Alternatively, this could check for system properties or environment variables.
 */
internal actual fun isDebugBuild(): Boolean {
    // Check if assertions are enabled (typically enabled in debug builds)
    var assertionsEnabled = false
    assert({ assertionsEnabled = true; true }())
    return assertionsEnabled
}
