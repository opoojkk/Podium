package com.opoojkk.podium.util

/**
 * Simple conditional logger to replace println statements
 * Logs are only executed in debug builds to improve production performance
 */
object Logger {
    // TODO: Replace with BuildConfig.DEBUG when build configuration is set up
    // For now, set this to false in production builds
    const val DEBUG = true

    /**
     * Debug log - only executed in debug builds
     * Use inline lambda to avoid string concatenation overhead when DEBUG=false
     */
    inline fun d(tag: String, message: () -> String) {
        if (DEBUG) {
            println("🐛 [$tag] ${message()}")
        }
    }

    /**
     * Info log - only executed in debug builds
     */
    inline fun i(tag: String, message: () -> String) {
        if (DEBUG) {
            println("ℹ️ [$tag] ${message()}")
        }
    }

    /**
     * Warning log - only executed in debug builds
     */
    inline fun w(tag: String, message: () -> String) {
        if (DEBUG) {
            println("⚠️ [$tag] ${message()}")
        }
    }

    /**
     * Error log - always executed, even in production
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        println("❌ [$tag] $message")
        throwable?.printStackTrace()
    }

    /**
     * Performance measurement log - only in debug builds
     */
    inline fun <T> measureTime(tag: String, operation: String, block: () -> T): T {
        if (DEBUG) {
            val startTime = System.currentTimeMillis()
            return try {
                block()
            } finally {
                val duration = System.currentTimeMillis() - startTime
                println("⏱️ [$tag] $operation took ${duration}ms")
            }
        } else {
            return block()
        }
    }
}
