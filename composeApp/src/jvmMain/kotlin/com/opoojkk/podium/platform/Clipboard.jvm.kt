package com.opoojkk.podium.platform

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual fun copyTextToClipboard(context: PlatformContext, text: String): Boolean =
    runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val selection = StringSelection(text)
        clipboard.setContents(selection, selection)
        true
    }.getOrElse { false }
