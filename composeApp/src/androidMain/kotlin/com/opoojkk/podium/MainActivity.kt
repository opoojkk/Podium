package com.opoojkk.podium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.opoojkk.podium.platform.PlatformContext

class MainActivity : ComponentActivity() {

    private var environment: PodiumEnvironment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = PlatformContext(this)
        environment = createPodiumEnvironment(context)
        setContent {
            environment?.let { PodiumApp(it) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        environment?.dispose()
        environment = null
    }
}
