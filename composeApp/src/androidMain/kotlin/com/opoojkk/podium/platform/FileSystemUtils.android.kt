package com.opoojkk.podium.platform

import java.io.File

actual fun fileSizeInBytes(path: String): Long? = runCatching {
    val file = File(path)
    if (file.exists()) file.length() else null
}.getOrNull()

actual fun fileLastModifiedMillis(path: String): Long? = runCatching {
    val file = File(path)
    if (file.exists()) file.lastModified() else null
}.getOrNull()
