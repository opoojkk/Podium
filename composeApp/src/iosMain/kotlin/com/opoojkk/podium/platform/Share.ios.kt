package com.opoojkk.podium.platform

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

actual fun shareText(context: PlatformContext, text: String, title: String?): Boolean =
    runCatching {
        val items = listOf(text)
        val activityViewController = UIActivityViewController(items, null)

        val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootViewController?.presentViewController(
            viewControllerToPresent = activityViewController,
            animated = true,
            completion = null
        )
        true
    }.getOrElse { false }
