package com.opoojkk.podium.platform

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

actual fun createFileOperations(context: PlatformContext): FileOperations {
    return AndroidFileOperations(context.context)
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
        try {
            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+): Use MediaStore to save to Downloads
                saveToMediaStore(content, suggestedFileName, mimeType)
            } else {
                // Android 9 and below: Use legacy Downloads directory
                saveToLegacyDownloads(content, suggestedFileName)
            }

            // Show toast notification
            if (success) {
                showToast(context, "文件已保存到下载文件夹: $suggestedFileName")
            } else {
                showToast(context, "保存文件失败，请重试")
            }

            success
        } catch (e: Exception) {
            println("❌ Error saving file to Downloads: ${e.message}")
            e.printStackTrace()
            showToast(context, "保存文件时出错: ${e.message}")
            false
        }
    }

    /**
     * Save file using MediaStore API (Android 10+)
     * No permissions required for Downloads directory
     */
    private fun saveToMediaStore(content: String, fileName: String, mimeType: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return false

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray())
                outputStream.flush()
            }

            println("✅ File saved successfully to Downloads: $fileName")
            return true
        } catch (e: Exception) {
            println("❌ MediaStore save error: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Save file to legacy Downloads directory (Android 9 and below)
     * No permissions required for public Downloads directory
     */
    private fun saveToLegacyDownloads(content: String, fileName: String): Boolean {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            // Create Downloads directory if it doesn't exist
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val file = File(downloadsDir, fileName)
            file.writeText(content)

            println("✅ File saved successfully to Downloads: ${file.absolutePath}")
            return true
        } catch (e: Exception) {
            println("❌ Legacy save error: ${e.message}")
            e.printStackTrace()
            return false
        }
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
