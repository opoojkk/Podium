package com.opoojkk.podium.platform

import platform.UIKit.UIApplication
import platform.Foundation.NSURL

actual fun openUrl(context: PlatformContext, url: String): Boolean {
    return try {
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl != null) {
            UIApplication.sharedApplication.openURL(nsUrl)
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }
}
