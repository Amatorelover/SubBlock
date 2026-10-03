package com.yanhu.subblock.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.yanhu.subblock.data.BlockConfig

/**
 * 一块遮挡区域。
 *
 * 它是挂在一个独立系统窗口上的 View，因此可以自由拖动、缩放，
 * 并且不会挡住底层 App 的操作（窗口只占据自己那一小块区域）。
 *
 * 手势约定：
 *  - 按住拖动        -> 移动
 *  - 右下角手柄拖动  -> 缩放
 *  - 双击            -> 锁定 / 解锁
 *  - 长按            -> 跳到 App 里编辑这一块
 */
@SuppressLint("ViewConstructor")
class BlockView(
    context: Context,
    initial: BlockConfig,
    private val host: Host
) : View(context) {

    interface Host {
        /** 当前这块在屏幕上的像素位置 */
        fun bounds(): Rect
        fun moveBy(dx: Int, dy: Int)
        fun resizeBy(dw: Int, dh: Int)
        /** 手势结束，把新位置写回存储 */
        fun commit()
        fun openEditor()
        fun toggleLock()
    }

    private enum class DragMode { NONE, MOVE, RESIZE }

    var cfg: BlockConfig = initial
    /** 用户正在拖动时，服务端不要来改几何信息，否则会打架 */
    var interacting: Boolean = false
        private set

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = 0x99FFFFFF.toInt()
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val handleRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = 0xFF534AB7.toInt()
    }
    private val centerDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x77000000 }

    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable {
        showHandles = false
        invalidate()
    }
    private val longPressRunnable = Runnable {
        mode = DragMode.NONE
        interacting = false
        host.openEditor()
    }

    private var showHandles = false
    private var mode = DragMode.NONE
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var accX = 0f
    private var accY = 0f
    private var lastDownTime = 0L

    init {
        // 让 View 自己拿到焦点事件，但不要拿走输入焦点（否则底层 App 打不了字）
        isFocusable = false
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val r = cfg.corner * density

        // 底色始终绘制：当设备不支持毛玻璃时，它就是唯一的遮挡手段
        fillPaint.color = withAlpha(cfg.color, cfg.alpha)
        canvas.drawRoundRect(0f, 0f, w, h, r, r, fillPaint)

        if (showHandles) {
            canvas.drawRoundRect(0f, 0f, w, h, r, r, framePaint)

            val cx = w - handleRadius
            val cy = h - handleRadius
            canvas.drawCircle(cx, cy, handleRadius, handleFill)
            canvas.drawCircle(cx, cy, handleRadius, handleRing)
            canvas.drawCircle(w / 2f, h / 2f, dp(3f), centerDot)
        }
    }

    private val handleRadius get() = dp(11f)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                val now = SystemClock.uptimeMillis()
                if (now - lastDownTime < DOUBLE_TAP_MS) {
                    // 双击：锁定 / 解锁
                    lastDownTime = 0L
                    handler.removeCallbacks(longPressRunnable)
                    host.toggleLock()
                    showHandlesNow()
                    return true
                }
                lastDownTime = now
                interacting = true
                accX = 0f
                accY = 0f
                lastRawX = event.rawX
                lastRawY = event.rawY
                mode = if (!cfg.locked && isOnResizeHandle(event.x, event.y)) {
                    DragMode.RESIZE
                } else {
                    DragMode.MOVE
                }
                showHandlesNow()
                handler.removeCallbacks(hideRunnable)
                if (!cfg.locked) {
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastRawX
                val dy = event.rawY - lastRawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                if (kotlin.math.abs(dx) > 2f || kotlin.math.abs(dy) > 2f) {
                    handler.removeCallbacks(longPressRunnable)
                }
                if (cfg.locked) return true

                // 累积小数部分，避免慢速拖动时像素被"吃掉"
                accX += dx
                accY += dy
                val ix = accX.toInt()
                val iy = accY.toInt()
                if (ix == 0 && iy == 0) return true
                accX -= ix
                accY -= iy

                when (mode) {
                    DragMode.MOVE -> host.moveBy(ix, iy)
                    DragMode.RESIZE -> host.resizeBy(ix, iy)
                    DragMode.NONE -> Unit
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                if (interacting) {
                    interacting = false
                    if (mode != DragMode.NONE) host.commit()
                }
                mode = DragMode.NONE
                handler.removeCallbacks(hideRunnable)
                handler.postDelayed(hideRunnable, HANDLE_TIMEOUT_MS)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun applyConfig(newCfg: BlockConfig) {
        if (cfg == newCfg) return
        cfg = newCfg
        invalidate()
    }

    private fun showHandlesNow() {
        showHandles = true
        invalidate()
    }

    private fun isOnResizeHandle(x: Float, y: Float): Boolean {
        val reach = dp(34f)
        return x >= width - reach && y >= height - reach
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val DOUBLE_TAP_MS = 300L
        private const val HANDLE_TIMEOUT_MS = 2200L
        private val LONG_PRESS_MS = ViewConfiguration.getLongPressTimeout().toLong()
    }
}
