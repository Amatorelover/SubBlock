package com.yanhu.subblock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.yanhu.subblock.overlay.OverlayService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private var language by mutableStateOf(AppLocale.SYSTEM)

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * 语言必须在 Activity 创建之前注入：这里把系统给的 Context
     * 换成"带指定语言"的 Context，之后这个界面里所有取字符串的地方
     * 都会自动走对应语言的资源，不需要每个控件自己判断语言。
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        focusBlockId = intent?.getStringExtra(EXTRA_BLOCK_ID)
        hasOverlayPermission = Settings.canDrawOverlays(this)
        language = AppLocale.read(this)
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
                        language = language,
                        onFocusHandled = { focusBlockId = null },
                        onRequestOverlayPermission = ::requestOverlayPermission,
                        onOpenSystemSettings = ::openAppSystemSettings,
                        onOpenAbout = { showAbout = true },
                        onLanguageChange = ::applyLanguage
                    )
                }
            }
        }
    }

    /**
     * 换语言：先落盘，再让系统重建界面。
     * 用 recreate() 而不是自己手动改所有文案——把"资源随语言变"这件事
     * 交给系统，代码里就不会到处散落"当前是什么语言"的判断。
     */
    private fun applyLanguage(tag: String) {
        AppLocale.write(this, tag)
        language = tag
        recreate()
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
        // 重新打开 App（例如从最近任务划掉后）时，进程是全新的，
        // 悬浮窗服务已随旧进程一起被系统杀掉；但 enabled 仍留在 DataStore 里为 true，
        // 于是首屏开关显示"开"、状态卡却说"等待中"、屏幕上没有任何遮挡块。
        // 这里在每次回到前台时，只要开关是开、且悬浮窗权限已授予，就确保服务在跑，
        // 补齐"持久化状态"与"实际运行"之间的鸿沟。重复调用是幂等的：
        // 服务已在跑时只会再收到一次 onStartCommand，不会叠加窗口。
        if (hasOverlayPermission) {
            lifecycleScope.launch {
                if (store.enabled.first()) OverlayService.start(this@MainActivity)
            }
        }
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
