package com.opoojkk.podium.player.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 接收并处理媒体通知栏的操作按钮点击
 */
class MediaActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // 通过静态回调处理操作
        listener?.handleNotificationAction(action)
    }

    companion object {
        var listener: MediaNotificationManager? = null
    }
}
