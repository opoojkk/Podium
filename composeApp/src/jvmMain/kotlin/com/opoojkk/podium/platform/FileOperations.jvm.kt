package com.opoojkk.podium.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual fun createFileOperations(context: PlatformContext): FileOperations {
    return JvmFileOperations()
}

class JvmFileOperations : FileOperations {

    override suspend fun pickFileToImport(): String? = withContext(Dispatchers.IO) {
        try {
            val dialog = FileDialog(null as Frame?, "选择订阅文件", FileDialog.LOAD)
            dialog.setFilenameFilter { _, name ->
                name.endsWith(".opml", ignoreCase = true) ||
                name.endsWith(".xml", ignoreCase = true) ||
                name.endsWith(".json", ignoreCase = true)
            }
            dialog.isVisible = true

            val directory = dialog.directory
            val fileName = dialog.file

            if (directory != null && fileName != null) {
                val file = File(directory, fileName)
                if (file.exists() && file.isFile) {
                    file.readText()
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error picking file: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    override suspend fun saveToFile(
        content: String,
        suggestedFileName: String,
        mimeType: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val dialog = FileDialog(null as Frame?, "保存订阅文件", FileDialog.SAVE)
            dialog.file = suggestedFileName
            dialog.isVisible = true

            val directory = dialog.directory
            val fileName = dialog.file

            if (directory != null && fileName != null) {
                val file = File(directory, fileName)
                file.writeText(content)
                println("File saved successfully to: ${file.absolutePath}")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            println("Error saving file: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}
