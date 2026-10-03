package com.yanhu.subblock

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yanhu.subblock.data.SettingsStore
import com.yanhu.subblock.ui.AboutScreen
import com.yanhu.subblock.ui.HomeScreen
import com.yanhu.subblock.ui.theme.SubBlockTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BLOCK_ID = "extra_block_id"
    }

    private val store by lazy { SettingsStore(this) }

    private var hasOverlayPermission by mutableStateOf(false)
    private var focusBlockId by mutableStateOf<String?>(null)
    private var showAbout by mutableStateOf(false)

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        focusBlockId = intent?.getStringExtra(EXTRA_BLOCK_ID)
        hasOverlayPermission = Settings.canDrawOverlays(this)
        askNotificationPermission()

        setContent {
            SubBlockTheme {
                if (showAbout) {
                    AboutScreen(onBack = { showAbout = false })
                } else {
                    HomeScreen(
                        store = store,
                        hasOverlayPermission = hasOverlayPermission,
                        focusBlockId = focusBlockId,
                        onFocusHandled = { focusBlockId = null },
                        onRequestOverlayPermission = ::requestOverlayPermission,
                        onOpenSystemSettings = ::openAppSystemSettings,
                        onOpenAbout = { showAbout = true }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        focusBlockId = intent.getStringExtra(EXTRA_BLOCK_ID)
    }

    override fun onResume() {
        super.onResume()
        // 用户可能刚从系统设置页回来，这里刷新一下权限状态
        hasOverlayPermission = Settings.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(intent) }
    }

    private fun openAppSystemSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { notificationLauncher.launch("android.permission.POST_NOTIFICATIONS") }
        }
    }
}
