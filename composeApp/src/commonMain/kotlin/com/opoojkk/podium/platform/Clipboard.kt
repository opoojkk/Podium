package com.opoojkk.podium.platform

/**
 * Copies [text] into the platform clipboard.
 *
 * @return true when the copy action succeeds, false otherwise.
 */
expect fun copyTextToClipboard(context: PlatformContext, text: String): Boolean
