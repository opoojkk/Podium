package com.opoojkk.podium.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

actual fun copyTextToClipboard(context: PlatformContext, text: String): Boolean =
    runCatching {
        val clipboard = context.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("Podium OPML", text))
        true
    }.getOrElse { false }
