package com.yanhu.subblock.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yanhu.subblock.AppLocale
import com.yanhu.subblock.MainActivity
import com.yanhu.subblock.R
import com.yanhu.subblock.data.BlockConfig
import com.yanhu.subblock.data.BlockMode
import com.yanhu.subblock.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 遮挡窗的宿主服务。
 *
 * 一个窗口 = 一块遮挡区域。窗口类型是 TYPE_APPLICATION_OVERLAY，
 * 也就是"系统级悬浮窗"，可以盖在任何 App 上面。
 *
 * 数据流：DataStore -> 本服务 -> WindowManager。
 * 服务只负责"把数据画到屏幕上"，不持有任何业务状态，所以永远不会有状态不同步的问题。
 */
class OverlayService : Service() {

    private class Holder(
        val view: BlockView,
        val params: WindowManager.LayoutParams,
        var styleKey: String
    )

    private lateinit var wm: WindowManager
    private lateinit var store: SettingsStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val holders = LinkedHashMap<String, Holder>()

    private var enabled = false
    private var blocks: List<BlockConfig> = emptyList()

    /**
     * 服务跑在 Application 语境里，本身不认识用户选的语言。
     * 这里包一层带语言的 Context，专门用来取文案——
     * 否则用户切成英文后，通知栏和 Toast 还是中文，显得很割裂。
     */
    private val uiContext: Context by lazy { AppLocale.wrap(this) }

    private fun tr(@StringRes id: Int, vararg args: Any): String =
        uiContext.getString(id, *args)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable {
        if (!enabled) {
            OverlayStatusBus.update(onScreen = 0)
            stopSelf()
        }
    }

    private val displayManager: DisplayManager by lazy {
        getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = reflowGeometry()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        store = SettingsStore(this)

        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        // 唯一的数据订阅：开关或遮挡块一变，立刻重画
        scope.launch {
            combine(store.enabled, store.blocks) { e, b -> e to b }
                .collect { (e, b) ->
                    enabled = e
                    blocks = b
                    refresh()
                }
        }

        displayManager.registerDisplayListener(displayListener, null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISABLE -> scope.launch { store.setEnabled(false) }
            ACTION_STOP -> {
                removeAll()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { displayManager.unregisterDisplayListener(displayListener) }
        removeAll()
        scope.cancel()
        OverlayStatusBus.update(onScreen = 0)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- 渲染

    private fun refresh() {
        if (!enabled) {
            // 注意：不要立刻自杀。数据订阅的第一帧可能是写入前的旧值，
            // 如果马上 stopSelf，真正的"开启"状态到了也没人听——
            // 表现就是"界面开关是开的，屏幕上却什么都没有"。
            // 所以先摘掉窗口、广播状态，延迟几秒确认仍然是关的才退出。
            removeAll()
            // 窗口已全部摘除，如实上报"屏幕上有 0 块"。
            // 这里以前上报的是 running = true，而服务其实正要退出——
            // 一个字段同时说"活着"和"要走了"，谁读谁糊涂。现在只报事实。
            OverlayStatusBus.update(onScreen = 0)
            updateNotification()
            mainHandler.removeCallbacks(stopRunnable)
            mainHandler.postDelayed(stopRunnable, STOP_DELAY_MS)
            return
        }
        mainHandler.removeCallbacks(stopRunnable)
        if (!Settings.canDrawOverlays(this)) {
            // 权限被关了，不画，但保留服务让用户能看到提示
            removeAll()
            OverlayStatusBus.update(onScreen = 0, error = tr(R.string.service_err_no_permission))
            updateNotification()
            return
        }

        val wantedIds = blocks.map { it.id }.toSet()
        holders.keys.filterNot { it in wantedIds }.toList().forEach { removeBlock(it) }

        blocks.forEach { cfg ->
            val holder = holders[cfg.id]
            if (holder == null) addBlock(cfg) else syncBlock(holder, cfg)
        }
        OverlayStatusBus.update(onScreen = holders.size)
        updateNotification()
    }

    private fun addBlock(cfg: BlockConfig) {
        val rect = toPixels(cfg)
        val params = WindowManager.LayoutParams(
            rect.width(),
            rect.height(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            BASE_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rect.left
            y = rect.top
        }
        applyBlur(params, cfg)

        val view = BlockView(this, cfg, BlockHost(cfg.id, params))
        holders[cfg.id] = Holder(view, params, styleKeyOf(cfg))

        runCatching { wm.addView(view, params) }
            .onFailure { e ->
                holders.remove(cfg.id)
                // 失败绝不能静默：把原因喊出来，否则"没显示"永远查不到原因
                val reason = e.javaClass.simpleName + ": " +
                    (e.message ?: tr(R.string.service_err_unknown))
                OverlayStatusBus.update(
                    onScreen = holders.size,
                    error = tr(R.string.service_err_add_view, reason)
                )
                toast(tr(R.string.service_err_add_view, reason))
            }
    }

    private fun syncBlock(holder: Holder, cfg: BlockConfig) {
        holder.view.applyConfig(cfg)
        if (holder.view.interacting) return

        val newStyle = styleKeyOf(cfg)
        val styleChanged = newStyle != holder.styleKey
        if (styleChanged) {
            applyBlur(holder.params, cfg)
            holder.styleKey = newStyle
        }

        val rect = toPixels(cfg)
        val geometryChanged = rect.left != holder.params.x ||
            rect.top != holder.params.y ||
            rect.width() != holder.params.width ||
            rect.height() != holder.params.height
        if (geometryChanged) {
            holder.params.x = rect.left
            holder.params.y = rect.top
            holder.params.width = rect.width()
            holder.params.height = rect.height()
        }

        if (geometryChanged || styleChanged) safeUpdate(holder.view, holder.params)
    }

    /** 分辨率或方向变化后，按比例重新摆放所有窗口 */
    private fun reflowGeometry() {
        holders.forEach { (id, holder) ->
            if (holder.view.interacting) return@forEach
            val cfg = blocks.firstOrNull { it.id == id } ?: return@forEach
            val rect = toPixels(cfg)
            holder.params.x = rect.left
            holder.params.y = rect.top
            holder.params.width = rect.width()
            holder.params.height = rect.height()
            safeUpdate(holder.view, holder.params)
        }
    }

    private fun removeBlock(id: String) {
        val holder = holders.remove(id) ?: return
        runCatching { wm.removeViewImmediate(holder.view) }
    }

    private fun removeAll() {
        holders.values.forEach { runCatching { wm.removeViewImmediate(it.view) } }
        holders.clear()
    }

    private fun safeUpdate(view: View, params: WindowManager.LayoutParams) {
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun styleKeyOf(cfg: BlockConfig) = "${cfg.mode}|${cfg.blurRadius}"

    /**
     * 毛玻璃：Android 12 起支持让窗口"模糊背后的内容"。
     * 低版本或系统关闭了模糊时，这一步静默失效——
     * 但我们始终会绘制半透明底色，所以遮挡效果永远不会丢。
     */
    private fun applyBlur(params: WindowManager.LayoutParams, cfg: BlockConfig) {
        if (cfg.mode == BlockMode.BLUR && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            params.blurBehindRadius = cfg.blurRadius.coerceIn(1, 150)
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.blurBehindRadius = 0
            }
        }
    }

    // ---------------------------------------------------------------- 几何换算

    private fun screenSize(): Point =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            Point(b.width(), b.height())
        } else {
            val p = Point()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(p)
            p
        }

    private fun toPixels(cfg: BlockConfig): Rect {
        val s = screenSize()
        val left = (cfg.nx * s.x).roundToInt()
        val top = (cfg.ny * s.y).roundToInt()
        val w = (cfg.nw * s.x).roundToInt().coerceAtLeast(dp(48))
        val h = (cfg.nh * s.y).roundToInt().coerceAtLeast(dp(24))
        return Rect(left, top, left + w, top + h)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private inner class BlockHost(
        private val id: String,
        private val params: WindowManager.LayoutParams
    ) : BlockView.Host {

        private val view: BlockView? get() = holders[id]?.view

        override fun bounds(): Rect =
            Rect(params.x, params.y, params.x + params.width, params.y + params.height)

        override fun moveBy(dx: Int, dy: Int) {
            params.x += dx
            params.y += dy
            view?.let { safeUpdate(it, params) }
        }

        override fun resizeBy(dw: Int, dh: Int) {
            val s = screenSize()
            val minW = dp(48)
            val minH = dp(24)
            var w = (params.width + dw).coerceAtLeast(minW)
            var h = (params.height + dh).coerceAtLeast(minH)
            if (params.x + w > s.x) w = (s.x - params.x).coerceAtLeast(minW)
            if (params.y + h > s.y) h = (s.y - params.y).coerceAtLeast(minH)
            params.width = w
            params.height = h
            view?.let { safeUpdate(it, params) }
        }

        override fun commit() {
            val s = screenSize()
            if (s.x <= 0 || s.y <= 0) return
            val targetId = id
            val nx = params.x.toFloat() / s.x
            val ny = params.y.toFloat() / s.y
            val nw = params.width.toFloat() / s.x
            val nh = params.height.toFloat() / s.y
            scope.launch { store.updateGeometry(targetId, nx, ny, nw, nh) }
        }

        override fun openEditor() {
            val intent = Intent(this@OverlayService, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_BLOCK_ID, id)
            }
            runCatching { startActivity(intent) }
        }

        override fun toggleLock() {
            val targetId = id
            scope.launch {
                val cfg = store.blocks.first().firstOrNull { it.id == targetId } ?: return@launch
                val next = !cfg.locked
                store.upsert(cfg.copy(locked = next))
                toast(if (next) tr(R.string.toast_locked) else tr(R.string.toast_unlocked))
            }
        }
    }

    private fun toast(text: String) {
        runCatching { Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show() }
    }

    // ---------------------------------------------------------------- 通知

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    tr(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = tr(R.string.notif_channel_desc)
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val disable = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_DISABLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = if (blocks.isEmpty()) {
            tr(R.string.notif_text_empty)
        } else {
            tr(R.string.notif_text_count, blocks.size)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(tr(R.string.notif_title))
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, tr(R.string.notif_action_disable), disable)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification())
        }
    }

    companion object {
        private const val CHANNEL_ID = "subblock_overlay"
        private const val NOTIF_ID = 1001

        const val ACTION_DISABLE = "com.yanhu.subblock.action.DISABLE"
        const val ACTION_STOP = "com.yanhu.subblock.action.STOP"

        private const val STOP_DELAY_MS = 3000L

        private const val BASE_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }
}
