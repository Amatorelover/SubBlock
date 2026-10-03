package com.yanhu.subblock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.yanhu.subblock.data.SettingsStore
import com.yanhu.subblock.overlay.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机后恢复遮挡状态。
 *
 * 有些厂商 ROM 会拦截开机自启，这时这个广播根本不会送达——
 * 不是 bug，是系统策略。解决办法是把「遮幕」加入手机管家的自启动白名单。
 * 所以这里全部用 try/catch 包住，收不到也不影响使用。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val wasEnabled = SettingsStore(appContext).enabled.first()
                if (wasEnabled && Settings.canDrawOverlays(appContext)) {
                    OverlayService.start(appContext)
                }
            } catch (_: Throwable) {
                // 忽略：开机自启失败不影响手动开启
            } finally {
                runCatching { pending.finish() }
            }
        }
    }
}
