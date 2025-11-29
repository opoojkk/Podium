package com.opoojkk.podium.platform

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow

actual fun shareText(context: PlatformContext, text: String, title: String?): Boolean =
    runCatching {
        // Build items list, include title if provided
        val items = buildList {
            title?.let { add(it) }
            add(text)
        }
        val activityViewController = UIActivityViewController(items, null)

        // Get the root view controller from connected scenes (iOS 13+)
        val windowScene = UIApplication.sharedApplication.connectedScenes
            .firstOrNull() as? platform.UIKit.UIWindowScene
        val rootViewController = (windowScene?.windows?.firstOrNull() as? UIWindow)?.rootViewController

        rootViewController?.presentViewController(
            viewControllerToPresent = activityViewController,
            animated = true,
            completion = null
        )
        true
    }.getOrElse { false }
