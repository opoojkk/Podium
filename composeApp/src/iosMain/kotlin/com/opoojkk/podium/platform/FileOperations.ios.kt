package com.opoojkk.podium.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToFile

actual fun createFileOperations(context: PlatformContext): FileOperations {
    return IosFileOperations()
}

class IosFileOperations : FileOperations {

    override suspend fun pickFileToImport(): String? = withContext(Dispatchers.Main) {
        // Note: This requires UIDocumentPickerViewController
        // For now, return null as this needs UI integration
        // The proper implementation would use UIDocumentPickerViewController
        null
    }

    override suspend fun saveToFile(
        content: String,
        suggestedFileName: String,
        mimeType: String
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            // Save to Documents directory
            val paths = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory,
                NSUserDomainMask,
                true
            )
            val documentsDirectory = paths.firstOrNull() as? String ?: return@withContext false
            val filePath = "$documentsDirectory/$suggestedFileName"

            val nsString = NSString.create(string = content)
            val data = nsString.dataUsingEncoding(NSUTF8StringEncoding)
                ?: return@withContext false

            data.writeToFile(filePath, atomically = true)
            println("File saved successfully to: $filePath")
            true
        } catch (e: Exception) {
            println("Error saving file: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Read a file from the Documents directory.
     */
    suspend fun readFile(fileName: String): String? = withContext(Dispatchers.Default) {
        try {
            val paths = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory,
                NSUserDomainMask,
                true
            )
            val documentsDirectory = paths.firstOrNull() as? String ?: return@withContext null
            val filePath = "$documentsDirectory/$fileName"

            val fileManager = NSFileManager.defaultManager
            if (!fileManager.fileExistsAtPath(filePath)) {
                return@withContext null
            }

            val data = NSData.create(contentsOfFile = filePath) ?: return@withContext null
            NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
        } catch (e: Exception) {
            println("Error reading file: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
