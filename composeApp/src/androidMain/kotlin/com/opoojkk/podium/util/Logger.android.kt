package com.opoojkk.podium.util

/**
 * Android implementation of debug build detection.
 * TODO: Use BuildConfig.DEBUG when available
 * For now, returns true to enable logging in development builds
 */
internal actual fun isDebugBuild(): Boolean = true
