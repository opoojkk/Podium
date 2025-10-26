package com.opoojkk.podium.platform

import java.awt.Desktop
import java.net.URI

actual fun openUrl(context: PlatformContext, url: String): Boolean {
    return try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }
}
