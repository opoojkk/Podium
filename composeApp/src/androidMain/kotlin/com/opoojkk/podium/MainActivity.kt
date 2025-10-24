package com.opoojkk.podium

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.opoojkk.podium.platform.PlatformContext

class MainActivity : ComponentActivity() {

    private var environment: PodiumEnvironment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge and transparent system bars
        enableEdgeToEdge()
        
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
