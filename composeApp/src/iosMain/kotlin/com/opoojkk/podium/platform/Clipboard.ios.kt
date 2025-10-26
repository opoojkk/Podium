package com.opoojkk.podium.platform

import platform.UIKit.UIPasteboard

actual fun copyTextToClipboard(context: PlatformContext, text: String): Boolean =
    runCatching {
        UIPasteboard.generalPasteboard().string = text
        true
    }.getOrElse { false }
