package com.opoojkk.podium.util

import kotlinx.coroutines.CancellationException

/**
 * Common extension functions to reduce code duplication across the app.
 */

/**
 * Executes a suspend block with standardized error handling.
 * Automatically logs errors and converts them to user-friendly messages.
 *
 * @param tag The log tag for error logging
 * @param onSuccess Called with the result if successful
 * @param onError Called with a UserFriendlyError if an error occurs (optional)
 * @param block The suspend block to execute
 */
suspend inline fun <T> withErrorHandling(
    tag: String,
    crossinline onSuccess: (T) -> Unit = {},
    crossinline onError: (UserFriendlyError) -> Unit = {},
    crossinline block: suspend () -> T
) {
    try {
        val result = block()
        onSuccess(result)
    } catch (cancellation: CancellationException) {
        // Re-throw cancellation to preserve coroutine cancellation
        throw cancellation
    } catch (error: Throwable) {
        val userFriendlyError = ErrorHandler.logAndHandle(tag, error)
        onError(userFriendlyError)
    }
}

/**
 * Executes a suspend block that returns a Result, with standardized error handling.
 *
 * @param tag The log tag for error logging
 * @param block The suspend block that returns a Result
 * @return Result with success or error
 */
suspend inline fun <T> tryCatching(
    tag: String,
    crossinline block: suspend () -> T
): Result<T> {
    return try {
        Result.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        ErrorHandler.logAndHandle(tag, error)
        Result.Error(error)
    }
}

/**
 * Safely executes a block and returns null on any exception (except CancellationException).
 * Useful for optional operations that should not crash the app.
 *
 * @param tag The log tag for error logging
 * @param block The block to execute
 * @return The result or null if an exception occurs
 */
suspend inline fun <T> tryOrNull(
    tag: String,
    crossinline block: suspend () -> T
): T? {
    return try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        ErrorHandler.logAndHandle(tag, error)
        null
    }
}

/**
 * Executes a block and returns a default value on any exception (except CancellationException).
 *
 * @param tag The log tag for error logging
 * @param default The default value to return on error
 * @param block The block to execute
 * @return The result or the default value if an exception occurs
 */
suspend inline fun <T> tryOrDefault(
    tag: String,
    default: T,
    crossinline block: suspend () -> T
): T {
    return try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        ErrorHandler.logAndHandle(tag, error)
        default
    }
}
