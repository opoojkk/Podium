package com.opoojkk.podium.platform

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual fun shareText(context: PlatformContext, text: String, title: String?): Boolean =
    runCatching {
        // Desktop platforms don't have native share dialog,
        // so we copy to clipboard as fallback
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
        true
    }.getOrElse { false }
