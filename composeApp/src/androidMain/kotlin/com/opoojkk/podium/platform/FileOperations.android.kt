package com.opoojkk.podium.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

actual fun createFileOperations(context: PlatformContext): FileOperations {
    return AndroidFileOperations(context.androidContext)
}

class AndroidFileOperations(private val context: Context) : FileOperations {

    override suspend fun pickFileToImport(): String? = withContext(Dispatchers.IO) {
        // Note: This requires launching an activity with ActivityResultContract
        // For now, return null as this needs UI integration
        // The proper implementation would use Activity Result API
        null
    }

    override suspend fun saveToFile(
        content: String,
        suggestedFileName: String,
        mimeType: String
    ): Boolean = withContext(Dispatchers.IO) {
        // Note: This requires launching an activity with ActivityResultContract
        // For now, return false as this needs UI integration
        // The proper implementation would use Activity Result API
        false
    }

    /**
     * Read content from a URI.
     * This can be called after receiving the URI from an Activity Result.
     */
    suspend fun readFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            println("Error reading from URI: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Write content to a URI.
     * This can be called after receiving the URI from an Activity Result.
     */
    suspend fun writeToUri(uri: Uri, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray())
            }
            true
        } catch (e: Exception) {
            println("Error writing to URI: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}

/**
 * Helper object for creating file picker intents.
 */
object AndroidFileIntents {
    /**
     * Create an intent to pick a file for import.
     */
    fun createImportIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "text/xml",
                    "text/x-opml",
                    "application/xml",
                    "application/json",
                    "text/plain"
                )
            )
        }
    }

    /**
     * Create an intent to save a file for export.
     */
    fun createExportIntent(suggestedFileName: String, mimeType: String): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, suggestedFileName)
        }
    }
}
