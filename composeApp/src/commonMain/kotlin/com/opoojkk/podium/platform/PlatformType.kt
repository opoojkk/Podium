package com.opoojkk.podium.platform

/**
 * Platform types supported by Podium.
 */
enum class PlatformType {
    /** Desktop platforms (JVM) */
    DESKTOP,

    /** Mobile platforms (Android, iOS) */
    MOBILE
}

/**
 * Get the current platform type.
 * This is an expect function implemented differently for each platform.
 */
expect fun getPlatformType(): PlatformType
