package com.opoojkk.podium.platform

/**
 * Cross-platform file operations for importing and exporting subscriptions.
 */
interface FileOperations {
    /**
     * Pick a file to import subscriptions from.
     * @return The file content as a string, or null if cancelled.
     */
    suspend fun pickFileToImport(): String?

    /**
     * Save content to a file.
     * @param content The content to save
     * @param suggestedFileName The suggested file name
     * @param mimeType The MIME type of the file
     * @return true if successful, false otherwise
     */
    suspend fun saveToFile(
        content: String,
        suggestedFileName: String,
        mimeType: String = "text/plain"
    ): Boolean
}

/**
 * Factory for creating platform-specific file operations.
 */
expect fun createFileOperations(context: PlatformContext): FileOperations
