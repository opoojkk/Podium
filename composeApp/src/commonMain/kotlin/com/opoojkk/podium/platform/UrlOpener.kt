package com.opoojkk.podium.platform

/**
 * Opens [url] in the platform's default web browser.
 *
 * @return true when the URL opening action succeeds, false otherwise.
 */
expect fun openUrl(context: PlatformContext, url: String): Boolean
