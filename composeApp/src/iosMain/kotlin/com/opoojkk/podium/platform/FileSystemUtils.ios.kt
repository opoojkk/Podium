package com.opoojkk.podium.platform

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSNumber
import platform.Foundation.NSError
import platform.Foundation.attributesOfItemAtPath
import platform.Foundation.timeIntervalSince1970

actual fun fileSizeInBytes(path: String): Long? = memScoped {
    val errorPtr = alloc<ObjCObjectVar<NSError?>>()
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, errorPtr.ptr)
    if (attributes == null) {
        null
    } else {
        (attributes.objectForKey(NSFileSize) as? NSNumber)?.longLongValue
    }
}

actual fun fileLastModifiedMillis(path: String): Long? = memScoped {
    val errorPtr = alloc<ObjCObjectVar<NSError?>>()
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, errorPtr.ptr)
    if (attributes == null) {
        null
    } else {
        val date = attributes.objectForKey(NSFileModificationDate) as? platform.Foundation.NSDate
        date?.timeIntervalSince1970()?.times(1000)?.toLong()
    }
}
