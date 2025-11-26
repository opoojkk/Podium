package com.opoojkk.podium.platform

/**
 * Shares [text] using the platform's native share functionality.
 *
 * @param context Platform context
 * @param text The text content to share
 * @param title Optional title for the share dialog
 * @return true when the share action succeeds, false otherwise.
 */
expect fun shareText(context: PlatformContext, text: String, title: String? = null): Boolean
