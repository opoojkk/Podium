package com.opoojkk.podium.platform

import android.content.Intent
import android.net.Uri

actual fun openUrl(context: PlatformContext, url: String): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}
