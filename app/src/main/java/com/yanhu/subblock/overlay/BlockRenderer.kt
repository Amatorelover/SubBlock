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

    /**
     * 在 (0,0)-(width,height) 区域内绘制一块遮挡。
     *
     * @param density 屏幕密度，用于把 dp 换成像素（保证不同手机上视觉粗细一致）
     */
    fun draw(canvas: Canvas, width: Float, height: Float, density: Float, cfg: BlockConfig) {
        if (width <= 0f || height <= 0f) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val alpha = cfg.alpha.coerceIn(0, 255)
        val solid = withAlpha(cfg.color, alpha)
        val radius = cfg.corner.coerceAtLeast(0f) * density
        val rect = RectF(0f, 0f, width, height)

        when (cfg.mode) {
            // 毛玻璃：底色照画（模糊由窗口的 FLAG_BLUR_BEHIND 提供）。
            // 这样在系统不支持模糊时，它自然退化成一块半透明色块，遮挡效果不丢。
            BlockMode.SOLID, BlockMode.BLUR ->
                canvas.drawRoundRect(rect, radius, radius, paint.apply { color = solid })

            // 上下边缘渐隐：中间保持实心，两端淡出，接缝看起来更自然
            BlockMode.FEATHER ->
                canvas.drawRoundRect(rect, radius, radius, paint.apply { shader = featherShader(height, solid) })
        }
    }

    /** 上下边缘渐隐：中间保持实心，两端淡出 */
    private fun featherShader(height: Float, color: Int): Shader {
        val transparent = color and 0x00FFFFFF
        return LinearGradient(
            0f, 0f, 0f, height,
            intArrayOf(transparent, color, color, transparent),
            floatArrayOf(0f, 0.32f, 0.68f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
