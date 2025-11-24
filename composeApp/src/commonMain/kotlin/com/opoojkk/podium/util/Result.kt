package com.opoojkk.podium.util

/**
 * A generic class that holds a value with its loading status.
 * Represents the result of an operation that can be in one of three states:
 * Loading, Success, or Error.
 */
sealed class Result<out T> {
    /**
     * Represents a loading state.
     */
    data object Loading : Result<Nothing>()

    /**
     * Represents a successful result with data.
     */
    data class Success<T>(val data: T) : Result<T>()

    /**
     * Represents an error state with an exception.
     */
    data class Error(val exception: Throwable) : Result<Nothing>()

    /**
     * Returns true if this is a Success result.
     */
    val isSuccess: Boolean
        get() = this is Success

    /**
     * Returns true if this is an Error result.
     */
    val isError: Boolean
        get() = this is Error

    /**
     * Returns true if this is a Loading result.
     */
    val isLoading: Boolean
        get() = this is Loading

    /**
     * Returns the data if this is a Success result, or null otherwise.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    /**
     * Returns the exception if this is an Error result, or null otherwise.
     */
    fun exceptionOrNull(): Throwable? = when (this) {
        is Error -> exception
        else -> null
    }
}

/**
 * Transforms the data of a Success result using the given transform function.
 * Returns Loading or Error unchanged.
 */
inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Error -> this
    is Result.Loading -> this
}

/**
 * Executes the given action if this is a Success result.
 */
inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) {
        action(data)
    }
    return this
}

/**
 * Executes the given action if this is an Error result.
 */
inline fun <T> Result<T>.onError(action: (Throwable) -> Unit): Result<T> {
    if (this is Result.Error) {
        action(exception)
    }
    return this
}

/**
 * Executes the given action if this is a Loading result.
 */
inline fun <T> Result<T>.onLoading(action: () -> Unit): Result<T> {
    if (this is Result.Loading) {
        action()
    }
    return this
}

/**
 * Converts a Kotlin Result to a custom Result.
 */
fun <T> kotlin.Result<T>.toResult(): Result<T> = fold(
    onSuccess = { Result.Success(it) },
    onFailure = { Result.Error(it) }
)

/**
 * Executes the given block and wraps the result in a Result.
 * Returns Result.Success if successful, or Result.Error if an exception is thrown.
 */
inline fun <T> resultOf(block: () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Throwable) {
    Result.Error(e)
}

/**
 * Executes the given suspend block and wraps the result in a Result.
 * Returns Result.Success if successful, or Result.Error if an exception is thrown.
 */
suspend inline fun <T> suspendResultOf(crossinline block: suspend () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Throwable) {
    Result.Error(e)
}
