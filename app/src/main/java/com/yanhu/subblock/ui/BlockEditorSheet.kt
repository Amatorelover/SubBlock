package com.yanhu.subblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yanhu.subblock.R
import com.yanhu.subblock.data.BlockConfig
import com.yanhu.subblock.data.BlockMode
import com.yanhu.subblock.overlay.BlockRenderer
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** 可选颜色。前几个是中性色，后面几个适合不同底色场景 */
private val SWATCHES = listOf(
    0xFF000000.toInt(), 0xFF1C1B1F.toInt(), 0xFF3F3F46.toInt(), 0xFF71717A.toInt(),
    0xFFFFFFFF.toInt(), 0xFFF3E7CE.toInt(), 0xFFF5C4B3.toInt(), 0xFFD85A30.toInt(),
    0xFF185FA5.toInt(), 0xFF1D9E75.toInt(), 0xFF534AB7.toInt(), 0xFFE24B4A.toInt()
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlockEditorSheet(
    block: BlockConfig,
    onDismiss: () -> Unit,
    onChange: (BlockConfig) -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val latest by rememberUpdatedState(block)
    val context = LocalContext.current

    // 这几个是连续调节的，用本地状态保证拖动顺滑，停手 350ms 后再写回存储
    var name by remember(block.id) { mutableStateOf(block.name) }
    var alpha by remember(block.id) { mutableFloatStateOf(block.alpha.toFloat()) }
    var blur by remember(block.id) { mutableFloatStateOf(block.blurRadius.toFloat()) }
    var corner by remember(block.id) { mutableFloatStateOf(block.corner) }
    var showColorPicker by remember { mutableStateOf(false) }

    val defaultName = stringResource(R.string.block_default_name)

    LaunchedEffect(name, alpha, blur, corner) {
        delay(350)
        val merged = latest.copy(
            name = name.ifBlank { defaultName },
            alpha = alpha.roundToInt().coerceIn(0, 255),
            blurRadius = blur.roundToInt().coerceIn(1, 60),
            corner = corner
        )
        if (merged != latest) onChange(merged)
    }

    val preview = latest.copy(alpha = alpha.roundToInt().coerceIn(0, 255))

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 必须手动挂滚动：ModalBottomSheet 的内容不会自己滚，
                // 面板一高，底部的删除按钮就会被切在屏幕外、点不到。
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                stringResource(R.string.editor_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(16.dp))
            BlockPreview(preview)

            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.label_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(18.dp))
            SectionLabel(stringResource(R.string.label_mode))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BlockMode.entries.forEach { mode ->
                    FilterChip(
                        selected = block.mode == mode,
                        onClick = { onChange(latest.copy(mode = mode)) },
                        label = { Text(modeLabel(context, mode)) }
                    )
                }
            }
            if (block.mode == BlockMode.BLUR) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.blur_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel(stringResource(R.string.label_color))
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SWATCHES.forEach { value ->
                    val selected = block.color == value
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(value))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .clickable { onChange(latest.copy(color = value)) }
                    )
                }

                // 自选颜色：色板之外的颜色都在这里挑。
                // 当前色不在色板里时，这个圆点就是它的"回显"，用户一眼知道自己在用什么颜色。
                FilterChip(
                    selected = block.color !in SWATCHES,
                    onClick = { showColorPicker = true },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(block.color))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                    },
                    label = { Text(stringResource(R.string.color_custom)) }
                )
            }

            Spacer(Modifier.height(20.dp))
            SliderRow(
                label = stringResource(R.string.label_alpha),
                valueText = "${(alpha / 2.55f).roundToInt()}%",
                value = alpha,
                range = 0f..255f,
                onValueChange = { alpha = it }
            )

            if (block.mode == BlockMode.BLUR) {
                SliderRow(
                    label = stringResource(R.string.label_blur_strength),
                    valueText = "${blur.roundToInt()}",
                    value = blur,
                    range = 1f..60f,
                    onValueChange = { blur = it }
                )
            }

            SliderRow(
                label = stringResource(R.string.label_corner),
                valueText = "${corner.roundToInt()} dp",
                value = corner,
                range = 0f..40f,
                onValueChange = { corner = it }
            )

            Spacer(Modifier.height(10.dp))
            SectionLabel(stringResource(R.string.section_quick_position))
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickPosition(stringResource(R.string.quick_bottom), 0.03f, 0.80f, 0.94f, 0.115f, latest, onChange)
                QuickPosition(stringResource(R.string.quick_center), 0.08f, 0.82f, 0.84f, 0.09f, latest, onChange)
                QuickPosition(stringResource(R.string.quick_top), 0f, 0.045f, 1f, 0.16f, latest, onChange)
                QuickPosition(stringResource(R.string.quick_full), 0f, 0f, 1f, 1f, latest, onChange)
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.label_lock), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.lock_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = block.locked,
                    onCheckedChange = { onChange(latest.copy(locked = it)) }
                )
            }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_delete_mask))
            }

            // 取色器：只在需要时才出现，不占版面
            if (showColorPicker) {
                ColorPickerDialog(
                    initialColor = block.color,
                    onDismiss = { showColorPicker = false },
                    onPick = { picked ->
                        onChange(latest.copy(color = picked))
                        showColorPicker = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun QuickPosition(
    label: String,
    nx: Float, ny: Float, nw: Float, nh: Float,
    block: BlockConfig,
    onChange: (BlockConfig) -> Unit
) {
    FilterChip(
        selected = false,
        onClick = { onChange(block.copy(nx = nx, ny = ny, nw = nw, nh = nh)) },
        label = { Text(label) }
    )
}

/**
 * 实时预览：模拟一块手机屏幕，假字幕在底下，遮挡块浮在上面。
 * 调颜色和透明度时不用来回切到真屏幕去对。
 *
 * 关键点：遮挡块**调用的是和悬浮窗完全相同的 [BlockRenderer]**，
 * 所以预览里漏出的"马脚"，在真实屏幕上不可能不出现。
 */
@Composable
private fun BlockPreview(block: BlockConfig) {
    val density = LocalDensity.current.density

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF23232B))
    ) {
        val w = maxWidth
        val h = maxHeight

        // 假的视频画面
        Text(
            "SUBTITLE  ·  DEMO",
            color = Color(0x33FFFFFF),
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        )

        // 假字幕：就是要被遮住的那行
        Text(
            stringResource(R.string.preview_demo_subtitle),
            color = Color(0xFFEDEDED),
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
        )

        // 遮挡块本体：与悬浮窗共用同一套绘制
        Box(
            modifier = Modifier
                .offset(x = w * block.nx, y = h * block.ny)
                .size(width = w * block.nw, height = h * block.nh)
                .drawBehind {
                    drawIntoCanvas { canvas ->
                        BlockRenderer.draw(
                            canvas = canvas.nativeCanvas,
                            width = size.width,
                            height = size.height,
                            density = density,
                            cfg = block
                        )
                    }
                }
                .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(block.corner))
        )
    }
}
