package com.opoojkk.podium.util

import kotlinx.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * User-friendly error representation with localized messages.
 */
data class UserFriendlyError(
    val title: String,
    val message: String,
    val originalException: Throwable? = null,
    val isRecoverable: Boolean = true
)

/**
 * Centralized error handler that converts technical exceptions to user-friendly error messages.
 * This provides a consistent error handling experience across the application.
 */
object ErrorHandler {
    /**
     * Converts a Throwable to a user-friendly error message.
     */
    fun handle(error: Throwable): UserFriendlyError {
        Logger.e("ErrorHandler", "Handling error: ${error.message}", error)

        return when (error) {
            is UnknownHostException -> UserFriendlyError(
                title = "网络连接失败",
                message = "无法连接到服务器，请检查您的网络连接。",
                originalException = error,
                isRecoverable = true
            )

            is SocketTimeoutException -> UserFriendlyError(
                title = "请求超时",
                message = "网络请求超时，请稍后重试。",
                originalException = error,
                isRecoverable = true
            )

            is ConnectException -> UserFriendlyError(
                title = "连接失败",
                message = "无法建立网络连接，请检查您的网络设置。",
                originalException = error,
                isRecoverable = true
            )

            is IOException -> UserFriendlyError(
                title = "网络错误",
                message = "网络通信出现问题，请检查您的网络连接后重试。",
                originalException = error,
                isRecoverable = true
            )

            is IllegalArgumentException -> UserFriendlyError(
                title = "参数错误",
                message = error.message ?: "提供的参数无效，请检查输入。",
                originalException = error,
                isRecoverable = true
            )

            is IllegalStateException -> UserFriendlyError(
                title = "状态错误",
                message = error.message ?: "应用程序处于无效状态，请重试。",
                originalException = error,
                isRecoverable = true
            )

            else -> UserFriendlyError(
                title = "发生错误",
                message = error.message ?: "发生了未知错误，请稍后重试。",
                originalException = error,
                isRecoverable = true
            )
        }
    }

    /**
     * Logs and converts an error to a user-friendly message.
     */
    fun logAndHandle(tag: String, error: Throwable): UserFriendlyError {
        Logger.e(tag, "Error occurred: ${error.message}", error)
        return handle(error)
    }

    /**
     * Returns a simple error message string for quick use.
     */
    fun getErrorMessage(error: Throwable): String {
        return handle(error).message
    }
}

/**
 * Extension function to convert Result.Error to UserFriendlyError.
 */
fun Result.Error.toUserFriendlyError(): UserFriendlyError {
    return ErrorHandler.handle(this.exception)
}

/**
 * Extension function to handle errors in Kotlin Result.
 */
inline fun <T> kotlin.Result<T>.handleError(
    tag: String,
    onSuccess: (T) -> Unit,
    onError: (UserFriendlyError) -> Unit = {}
) {
    this.onSuccess { value ->
        onSuccess(value)
    }.onFailure { error ->
        val userFriendlyError = ErrorHandler.logAndHandle(tag, error)
        onError(userFriendlyError)
    }
}

/**
 * Extension function for logging with error handling.
 */
inline fun <T> Result<T>.logError(tag: String): Result<T> {
    if (this is Result.Error) {
        ErrorHandler.logAndHandle(tag, this.exception)
    }
    return this
}
