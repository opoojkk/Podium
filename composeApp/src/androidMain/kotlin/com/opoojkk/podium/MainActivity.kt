package com.opoojkk.podium

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.opoojkk.podium.platform.PlatformContext
import com.opoojkk.podium.player.android.MediaNotificationManager

class MainActivity : ComponentActivity() {

    private var environment: PodiumEnvironment? = null
    // 用于控制是否显示播放详情页
    internal val showPlayerDetailFromNotification = mutableStateOf(false)

    // 注册权限请求launcher
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 权限已授予
            println("✅ 通知权限已授予")
        } else {
            // 权限被拒绝
            println("❌ 通知权限被拒绝")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge and transparent system bars
        enableEdgeToEdge()

        // 请求通知权限（Android 13+）
        requestNotificationPermission()

        // 检查是否从通知栏点击打开
        handleIntent(intent)

        val context = PlatformContext(this)
        environment = createPodiumEnvironment(context)
        setContent {
            environment?.let { PodiumApp(it, showPlayerDetailFromNotification) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * 处理从通知栏点击打开的 Intent
     */
    private fun handleIntent(intent: Intent?) {
        intent?.let {
            if (it.getBooleanExtra(MediaNotificationManager.EXTRA_SHOW_PLAYER_DETAIL, false)) {
                println("🔔 MainActivity: 从通知栏点击打开，显示播放详情页")
                showPlayerDetailFromNotification.value = true
            }
        }
    }

    /**
     * 请求通知权限
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // 权限已授予
                    println("✅ 通知权限已存在")
                }
                else -> {
                    // 请求权限
                    requestNotificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        environment?.dispose()
        environment = null
    }
}
