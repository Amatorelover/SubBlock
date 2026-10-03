package com.yanhu.subblock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yanhu.subblock.R
import java.util.Locale

/**
 * 自选颜色。
 *
 * 刻意不引入第三方取色库：一个取色器要 5 个依赖，而它本身只是
 * "一个二维渐变 + 一段拖动手势 + 一次 HSV 换算"。自己写反而更可控，
 * 也让项目继续保持零第三方依赖 —— 这是能进 F-Droid 的硬条件之一。
 *
 * 说明：这里只管 RGB。不透明度由编辑面板的滑杆负责，
 * 免得同一个概念在两个地方各有一个入口。
 */
@Composable
fun ColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    val hsv = remember(initialColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor and 0xFFFFFF, it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var hex by remember { mutableStateOf(toHex(hue, sat, value)) }

    fun syncHex() {
        hex = toHex(hue, sat, value)
    }

    val current = hsvToArgb(hue, sat, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picker_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {

                // ---- 预览 ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(current))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(14.dp))
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { input ->
                            hex = input
                            // 只有输入合法时才真的改色，否则用户删到一半就会被"纠正"
                            parseHex(input)?.let { argb ->
                                val out = FloatArray(3)
                                android.graphics.Color.colorToHSV(argb, out)
                                hue = out[0]
                                sat = out[1]
                                value = out[2]
                            }
                        },
                        label = { Text(stringResource(R.string.picker_hex)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ---- 饱和度 / 明度面板 ----
                Text(
                    stringResource(R.string.picker_area_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                SaturationValuePad(
                    hue = hue,
                    sat = sat,
                    value = value,
                    onChange = { s, v ->
                        sat = s
                        value = v
                        syncHex()
                    }
                )

                Spacer(Modifier.height(16.dp))

                // ---- 色相滑条 ----
                Text(
                    stringResource(R.string.picker_hue),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                HueSlider(
                    hue = hue,
                    onChange = {
                        hue = it
                        syncHex()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(current) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/**
 * 饱和度（横轴，白→纯色）/ 明度（纵轴，亮→黑）二维面板。
 *
 * 画法：先铺一层"白到当前色相"的横向渐变，再叠一层"透明到黑"的纵向渐变。
 * 两层叠加得到的，正好是整个 HSV 色彩面——不需要为每个像素做换算。
 */
@Composable
private fun SaturationValuePad(
    hue: Float,
    sat: Float,
    value: Float,
    onChange: (Float, Float) -> Unit
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val pureHue = Color.hsv(hue, 1f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(Color.White, pureHue)))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun handle(p: Offset) {
                        if (size.width <= 0 || size.height <= 0) return
                        val s = (p.x / size.width).coerceIn(0f, 1f)
                        val v = 1f - (p.y / size.height).coerceIn(0f, 1f)
                        onChange(s, v)
                    }
                    handle(down.position)
                    drag(down.id) { change ->
                        handle(change.position)
                        change.consume()
                    }
                }
            }
    ) {
        // 指示圆环：白圈 + 黑描边，无论压在什么颜色上都看得见
        Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            val cx = sat * this.size.width
            val cy = (1f - value) * this.size.height
            drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 3.dp.toPx()))
            drawCircle(Color.Black.copy(alpha = 0.45f), radius = 9.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 1.dp.toPx()))
        }
    }
}

/** 色相滑条：一条彩虹渐变 + 一个可拖动的指示器 */
@Composable
private fun HueSlider(hue: Float, onChange: (Float) -> Unit) {
    var width by remember { mutableStateOf(0) }
    val rainbow = listOf(
        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { width = it.width }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun handle(x: Float) {
                        if (width <= 0) return
                        onChange((x / width).coerceIn(0f, 1f) * 360f)
                    }
                    handle(down.position.x)
                    drag(down.id) { change ->
                        handle(change.position.x)
                        change.consume()
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            val h = this.size.height
            val r = h / 2f
            // 用一串小色块拼出渐变，省掉 Shader 依赖，边缘也更圆润
            val steps = 60
            val w = this.size.width / steps
            for (i in 0 until steps) {
                val c = Color.hsv(i * 360f / steps, 1f, 1f)
                drawRoundRect(
                    color = c,
                    topLeft = Offset(i * w, 0f),
                    size = Size(w + 1f, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                )
            }
            val cx = hue / 360f * this.size.width
            drawCircle(Color.White, radius = r * 0.8f, center = Offset(cx, r), style = Stroke(width = 3.dp.toPx()))
            drawCircle(Color.Black.copy(alpha = 0.45f), radius = r * 0.8f, center = Offset(cx, r), style = Stroke(width = 1.dp.toPx()))
        }
    }
}

// ------------------------------------------------------------------ 颜色换算

private fun hsvToArgb(h: Float, s: Float, v: Float): Int =
    android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))

private fun toHex(h: Float, s: Float, v: Float): String =
    String.format(Locale.US, "#%06X", hsvToArgb(h, s, v) and 0xFFFFFF)

/** 解析 #RGB / #RRGGBB，非法输入返回 null */
private fun parseHex(text: String): Int? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6 || t.any { it !in "0123456789abcdefABCDEF" }) return null
    return runCatching { android.graphics.Color.parseColor("#$t") }.getOrNull()
}
