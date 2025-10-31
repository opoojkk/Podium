package com.opoojkk.podium.platform

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Show a toast message on Android
 */
suspend fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
    withContext(Dispatchers.Main) {
        Toast.makeText(context, message, duration).show()
    }
}
