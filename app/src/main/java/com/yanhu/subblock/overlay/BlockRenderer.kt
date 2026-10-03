package com.yanhu.subblock.overlay

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.yanhu.subblock.data.BlockConfig
import com.yanhu.subblock.data.BlockMode

/**
 * 遮挡块的绘制逻辑，抽出来给**两个地方共用**：
 *   1. 悬浮窗里的 [BlockView]（真实上屏的样子）
 *   2. 编辑面板里的实时预览（BlockPreview）
 *
 * 为什么不各写一份？因为只要有两份，就一定会有一天画得不一样，
 * 用户在预览里调好的样子到了屏幕上却不是那个样子——这种 bug 极难查，
 * 因为两边代码"看起来都对"。**共用同一份绘制代码，这类 bug 从结构上就不可能产生。**
 *
 * 用 android.graphics 而不是 Compose 的 DrawScope，就是为了让两侧都能调用：
 * Compose 里可以拿到原生 Canvas（drawIntoCanvas），反过来则做不到。
 *
 * 曾经这里还有「斜纹」与「点阵」两种画法，已随枚举一起移除。
 * 顺带一提：它们用到的 `Path` / `clipPath` 全都依赖"先建一条圆角路径再裁剪"，
 * 而剩下这三种样式用 `drawRoundRect` 一行就够——纹样越花哨，越容易在
 * 高 DPI 或极端长宽比下露出马脚（当时斜纹的覆盖范围算错、点阵只有约 45% 覆盖率，
 * 实际上挡不住字幕）。样式少而可靠，比样式多而漏字要强。
 */
object BlockRenderer {

    // ---------------------------------------------------------------- 复用对象
    //
    // 这里原来在每次 draw() 里都 new 一个 Paint、一个 RectF，羽化时还要 new 一个
    // LinearGradient。看着无害，但拖动或缩放时**每一帧都要重画**，而 Paint /
    // LinearGradient 背后各挂着一个 native 对象——每帧新建等于每帧给 GC 递活儿，
    // 分配压力会随屏幕上的遮挡块数量成倍放大。这类开销不会让程序立刻出错，
    // 它只会让低端机在拖动时掉帧，属于"迟早显形"的性能债。
    //
    // 改成单例里复用一个实例后，代价是**必须自己负责擦干净**：
    // 对象上的状态会跨帧存活，上一帧留下的 `shader` 会渗进这一帧的画里。
    // 所以下面 draw() 的纪律是——**凡是 draw 会读到的可变状态，每条分支都必须显式赋值**，
    // 一个字都不能靠"上次是什么"。
    //
    // 线程前提：所有调用都发生在主线程（悬浮窗的 onDraw、Compose 预览的 drawBehind），
    // 因此这个共享缓存不需要加锁。若将来有后台线程加入绘制，必须改成 ThreadLocal，
    // 否则两个线程会互相把对方刚设好的 paint 抢掉。
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    /** 羽化渐变只跟 (高度, 颜色) 有关，缓存最后一次即可 */
    private var shader: LinearGradient? = null
    private var shaderHeight = Float.NaN
    private var shaderColor = 0

    /**
     * 在 (0,0)-(width,height) 区域内绘制一块遮挡。
     *
     * @param density 屏幕密度，用于把 dp 换成像素（保证不同手机上视觉粗细一致）
     */
    fun draw(canvas: Canvas, width: Float, height: Float, density: Float, cfg: BlockConfig) {
        if (width <= 0f || height <= 0f) return

        val alpha = cfg.alpha.coerceIn(0, 255)
        val solid = withAlpha(cfg.color, alpha)
        val radius = cfg.corner.coerceAtLeast(0f) * density

        // 复用 RectF：set 会覆盖全部四个值，不留残迹
        rect.set(0f, 0f, width, height)

        // color 在所有分支里都相同，可以提到 when 之前统一赋值。
        // shader 各分支不同，所以必须在每条分支里各自写死——
        // 「实心块」那条分支里那句 `paint.shader = null` 就是防这个的：
        // 少了它，一块实心遮挡紧接着一块羽化遮挡绘制时（顺序取决于窗口层级），
        // 实心块会莫名其妙地画成渐变。这类 bug 只在特定绘制顺序下出现，极难复现。
        paint.color = solid

        when (cfg.mode) {
            // 毛玻璃：底色照画（模糊由窗口的 FLAG_BLUR_BEHIND 提供）。
            // 这样在系统不支持模糊时，它自然退化成一块半透明色块，遮挡效果不丢。
            BlockMode.SOLID, BlockMode.BLUR -> {
                paint.shader = null
                canvas.drawRoundRect(rect, radius, radius, paint)
            }

            // 上下边缘渐隐：中间保持实心，两端淡出，接缝看起来更自然
            BlockMode.FEATHER -> {
                paint.shader = featherShader(height, solid)
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
        }
    }

    /**
     * 上下边缘渐隐：中间保持实心，两端淡出。
     *
     * 渐变只由 (高度, 颜色) 决定，同一块遮挡连续重画时这两个值都不变，
     * 所以缓存最后一份的命中率接近 100%。
     *
     * 已知的边界：屏幕上同时有多块**不同颜色**的羽化块、且它们交替重画时，
     * 单条缓存会被反复冲掉，退化成"每次新建"——也就是修复前的代价，不会更糟。
     * 之所以不为此上 LRU，是因为那种场景并不常见，而多一层缓存管理带来的
     * 复杂度和内存占用是实打实的。**用真实收益换复杂度，而不是用可能性换。**
     */
    private fun featherShader(height: Float, color: Int): LinearGradient {
        val cached = shader
        if (cached != null && height == shaderHeight && color == shaderColor) return cached

        val transparent = color and 0x00FFFFFF
        val fresh = LinearGradient(
            0f, 0f, 0f, height,
            intArrayOf(transparent, color, color, transparent),
            floatArrayOf(0f, 0.32f, 0.68f, 1f),
            Shader.TileMode.CLAMP
        )
        shader = fresh
        shaderHeight = height
        shaderColor = color
        return fresh
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
