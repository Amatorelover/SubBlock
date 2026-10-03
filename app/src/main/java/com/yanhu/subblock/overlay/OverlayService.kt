package com.yanhu.subblock.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
     *
     * **注意这里是 get，不是 `by lazy`**。早先写成 `by lazy` 是错的：
     * 它只包一次、之后永不更新，于是用户中途切语言后，服务这边仍握着
     * 旧语言的 Context——"界面全英文、通知栏还是中文"就是这么来的。
     * 改成每次现取，配合 [localeListener] 触发刷新，通知栏才能跟上语言。
     * （语言偏好存在内存里，read 很便宜，不必担心每次都读盘的性能问题。）
     */
    private val uiContext: Context get() = AppLocale.wrap(this)

    private fun tr(@StringRes id: Int, vararg args: Any): String =
        uiContext.getString(id, *args)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable {
        if (!enabled) {
            OverlayStatusBus.setOnScreen(0)
            stopSelf()
        }
    }

    /**
     * 语言变化的监听器。
     *
     * 两个坑都要避开：
     *  1. **不能**在字段初始化时就注册——字段初始化跑在构造函数里，
     *     而 Context 要等框架稍后调 attachBaseContext 才挂上，
     *     那一刻调 getSharedPreferences 会直接抛异常。
     *  2. **必须**存成字段而不是局部变量——SharedPreferences 只持弱引用，
     *     局部变量会被 GC 回收，然后监听器静默失效（不报错，只是再也不触发）。
     */
    private var localeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

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

        // 语言一变就重建通知。注意这里是在 onCreate 里注册，不能写成字段初始化——
        // 字段初始化时 Context 还没挂上（见 localeListener 的注释）。
        localeListener = AppLocale.registerLanguageObserver(this) { onLocaleChanged() }
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
        // 注销监听器：服务都没了，还挂着一个"语言一变就刷新通知"的回调，
        // 既多余、又可能让本应回收的服务被弱引用拖住。
        localeListener?.let { AppLocale.unregisterLanguageObserver(this, it) }
        localeListener = null
        removeAll()
        scope.cancel()
        // 只报"屏幕上有 0 块"。服务退场并不改变"有没有出错"这个事实——
        // 比如权限仍然缺失，那条错误对它依然成立，不该被这行顺手抹掉。
        OverlayStatusBus.setOnScreen(0)
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
            OverlayStatusBus.setOnScreen(0)
            // 服务马上要停了，不会再有"重试"这件事，上一轮的错误此时是无主残影，清掉。
            // 这是全项目仅有的两处允许清错误的地方之一（另一处见下面的挂窗尝试）。
            OverlayStatusBus.clearError()
            updateNotification()
            mainHandler.removeCallbacks(stopRunnable)
            mainHandler.postDelayed(stopRunnable, STOP_DELAY_MS)
            return
        }
        mainHandler.removeCallbacks(stopRunnable)
        if (!Settings.canDrawOverlays(this)) {
            // 权限被关了，不画，但保留服务让用户能看到提示
            removeAll()
            OverlayStatusBus.setOnScreen(0)
            OverlayStatusBus.reportError(tr(R.string.service_err_no_permission))
            updateNotification()
            return
        }

        // ↓ 走到这里，说明这一轮是真的要往屏幕上挂窗口了。
        //
        // 先清掉上一轮的失败记录：若这次仍然失败，addBlock 会重新报上来。
        // 于是 error 的含义变得非常明确——它永远反映**最近一次真实尝试**的结果，
        // 而不是某次失败的残影；也永远不会因为"顺手改个数字"而消失。
        //
        // 修复前，清空动作藏在 setOnScreen（当时的 update(onScreen)）里，
        // 于是它既发生在"正要重试"时，也发生在"只是改了个数"时——
        // 而后者会在几微秒内把刚写下的错误擦掉，让错误卡片永远不可达。
        OverlayStatusBus.clearError()

        val wantedIds = blocks.map { it.id }.toSet()
        holders.keys.filterNot { it in wantedIds }.toList().forEach { removeBlock(it) }

        blocks.forEach { cfg ->
            val holder = holders[cfg.id]
            if (holder == null) addBlock(cfg) else syncBlock(holder, cfg)
        }
        OverlayStatusBus.setOnScreen(holders.size)
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
                // 只写错误，不碰数量：屏幕上有几块由 refresh() 收尾时统一上报。
                // 而且这条错误会**粘住**，直到下一轮挂窗尝试才开始清算——
                // 用户因此有充足时间看清失败原因，不会一眨眼就"自愈"。
                OverlayStatusBus.reportError(tr(R.string.service_err_add_view, reason))
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
        // 「读」这一侧的兜底：即便磁盘上还留着历史脏数据（老版本拖出过屏幕、
        // 或有人手改过配置文件），画出来的一定完整在屏内。
        // 与写入侧的 constrained() 是同一套规则——约束在两头都执行，
        // 中间存的是什么就不重要了。
        val c = cfg.constrained()
        val w = (c.nw * s.x).roundToInt().coerceIn(dp(48).coerceAtMost(s.x), s.x)
        val h = (c.nh * s.y).roundToInt().coerceIn(dp(24).coerceAtMost(s.y), s.y)
        val left = (c.nx * s.x).roundToInt().coerceIn(0, (s.x - w).coerceAtLeast(0))
        val top = (c.ny * s.y).roundToInt().coerceIn(0, (s.y - h).coerceAtLeast(0))
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
            val s = screenSize()
            // 与数据层同一套边界，只是换算到像素：窗口必须**完整**留在屏内。
            // 这条以前是缺的——同一件事的两条路径（拖动 / 缩放），只有缩放被
            // 加固过，拖动可以一路把窗口推出屏幕。推出去之后用户既看不见、
            // 也点不到，而界面状态条照样说"屏幕上有 1 块"。
            val maxX = (s.x - params.width).coerceAtLeast(0)
            val maxY = (s.y - params.height).coerceAtLeast(0)
            params.x = (params.x + dx).coerceIn(0, maxX)
            params.y = (params.y + dy).coerceIn(0, maxY)
            view?.let { safeUpdate(it, params) }
        }

        override fun resizeBy(dw: Int, dh: Int) {
            val s = screenSize()
            val minW = dp(48)
            val minH = dp(24)
            // 上限由"离屏幕边缘还剩多少"决定，所以缩放到边就停，不会溢出屏外。
            val maxW = (s.x - params.x).coerceAtLeast(minW)
            val maxH = (s.y - params.y).coerceAtLeast(minH)
            params.width = (params.width + dw).coerceIn(minW, maxW)
            params.height = (params.height + dh).coerceIn(minH, maxH)
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
        val name = tr(R.string.notif_channel_name)
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        // 通知渠道的名字是"创建即冻结"的：即便重建服务、重装更新，也不会自己改。
        // 所以语言变化后，要主动用同名再创建一次来刷新名称与描述
        // （Android 8+ 对已存在的渠道，createNotificationChannel 会更新 name/description）。
        //
        // 这里刻意**不**用"删除后重建"的激进做法：删除渠道在某些系统上不可靠，
        // 弄不好通知渠道整个消失——那比"名称停在旧语言"严重得多。
        // 最坏情况也只是设置页里的渠道名没跟上，不影响遮挡功能。
        if (existing == null || existing.name != name) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    name,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = tr(R.string.notif_channel_desc)
                    setShowBadge(false)
                }
            )
        }
    }

    /** 语言变化时的统一响应：渠道名与通知文案都要跟着走 */
    private fun onLocaleChanged() {
        createChannel()
        updateNotification()
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
