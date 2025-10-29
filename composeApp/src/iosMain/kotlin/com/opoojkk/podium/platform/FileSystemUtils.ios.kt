package com.opoojkk.podium.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSNumber
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970

@OptIn(ExperimentalForeignApi::class)
actual fun fileSizeInBytes(path: String): Long? = memScoped {
    val errorPtr = alloc<ObjCObjectVar<NSError?>>()
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, errorPtr.ptr)
    if (attributes == null) {
        null
    } else {
        (attributes[NSFileSize] as? NSNumber)?.longLongValue
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun fileLastModifiedMillis(path: String): Long? = memScoped {
    val errorPtr = alloc<ObjCObjectVar<NSError?>>()
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, errorPtr.ptr)
    if (attributes == null) {
        null
    } else {
        val date = attributes[NSFileModificationDate] as? platform.Foundation.NSDate
        date?.timeIntervalSince1970()?.times(1000)?.toLong()
    }
}
