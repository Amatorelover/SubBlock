package com.yanhu.subblock.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.yanhu.subblock.AppLocale
import com.yanhu.subblock.MainActivity
import com.yanhu.subblock.R
import com.yanhu.subblock.data.SettingsStore
import com.yanhu.subblock.overlay.OverlayService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 下拉控制中心里的磁贴。
 *
 * 这是"年久失修"最值得补上的一块：不需要打开 App，
 * 从屏幕顶部下拉、点一下，遮挡立刻开关。
 *
 * 注意 onClick 运行在主线程，而 DataStore 读写是磁盘操作，
 * 所以这里用 runBlocking 把它走完再返回——宁可多花几毫秒，也不能出现"点开又弹回去"。
 */
@RequiresApi(Build.VERSION_CODES.N)
class BlockTileService : TileService() {

    private val store by lazy { SettingsStore(this) }

    /** 磁贴运行在系统进程语境里，取文案要走"带当前语言"的 Context */
    private val uiContext by lazy { AppLocale.wrap(this) }

    override fun onStartListening() {
        super.onStartListening()
        syncTile(readEnabled())
    }

    override fun onClick() {
        super.onClick()

        // 与主页总开关（HomeScreen.applyEnabled）保持同一套语义：
        // 开启前先确认悬浮窗权限，没权限就只引导授权、**不改 enabled**。
        // 旧实现无条件先写 enabled=true，再发现没权限 → 回到 App 开关显示"开"、
        // 屏幕上却什么都没有，正是"开关开着却没遮挡"那类状态撒谎的又一个入口。
        val current = runBlocking { store.enabled.first() }

        if (current) {
            // ---- 关闭：写状态 + 停服务 ----
            runBlocking { store.setEnabled(false) }
            OverlayService.stop(this)
            syncTile(false)
        } else {
            // ---- 开启：先验权限，再写状态（顺序不能反）----
            if (!Settings.canDrawOverlays(this)) {
                openApp()
                return
            }
            runBlocking { store.setEnabled(true) }
            OverlayService.start(this)
            syncTile(true)
        }
    }

    private fun readEnabled(): Boolean = runBlocking { store.enabled.first() }

    private fun syncTile(on: Boolean) {
        qsTile?.apply {
            state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = uiContext.getString(R.string.app_name)
            updateTile()
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
