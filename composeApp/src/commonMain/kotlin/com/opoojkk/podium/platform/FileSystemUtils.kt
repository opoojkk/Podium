package com.opoojkk.podium.platform

/**
 * Returns the size of the file at [path] in bytes, or null if the file cannot be accessed.
 */
expect fun fileSizeInBytes(path: String): Long?

/**
 * Returns the last modified time of the file at [path] in epoch milliseconds, or null if unavailable.
 */
expect fun fileLastModifiedMillis(path: String): Long?
