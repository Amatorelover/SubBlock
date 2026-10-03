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

        val next = runBlocking {
            val current = store.enabled.first()
            val target = !current
            store.setEnabled(target)
            target
        }

        syncTile(next)

        if (next) {
            if (Settings.canDrawOverlays(this)) {
                OverlayService.start(this)
            } else {
                // 没有悬浮窗权限，只能回到 App 里引导授权
                openApp()
            }
        } else {
            OverlayService.stop(this)
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
