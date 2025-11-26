package com.opoojkk.podium.platform

import android.content.Intent

actual fun shareText(context: PlatformContext, text: String, title: String?): Boolean =
    runCatching {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
            title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }
        val shareIntent = Intent.createChooser(sendIntent, title)
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.context.startActivity(shareIntent)
        true
    }.getOrElse { false }
