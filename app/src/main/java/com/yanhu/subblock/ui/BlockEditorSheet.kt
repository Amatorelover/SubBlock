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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yanhu.subblock.data.BlockConfig
import com.yanhu.subblock.data.BlockMode
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

    // 这几个是连续调节的，用本地状态保证拖动顺滑，停手 350ms 后再写回存储
    var name by remember(block.id) { mutableStateOf(block.name) }
    var alpha by remember(block.id) { mutableFloatStateOf(block.alpha.toFloat()) }
    var blur by remember(block.id) { mutableFloatStateOf(block.blurRadius.toFloat()) }
    var corner by remember(block.id) { mutableFloatStateOf(block.corner) }

    LaunchedEffect(name, alpha, blur, corner) {
        delay(350)
        val merged = latest.copy(
            name = name.ifBlank { "遮挡块" },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                "编辑遮挡块",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(16.dp))
            BlockPreview(preview)

            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(18.dp))
            SectionLabel("遮挡方式")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = block.mode == BlockMode.SOLID,
                    onClick = { onChange(latest.copy(mode = BlockMode.SOLID)) },
                    label = { Text("实心色块") }
                )
                FilterChip(
                    selected = block.mode == BlockMode.BLUR,
                    onClick = { onChange(latest.copy(mode = BlockMode.BLUR)) },
                    label = { Text("毛玻璃模糊") }
                )
            }
            if (block.mode == BlockMode.BLUR) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "毛玻璃需要 Android 12 以上；旧系统会自动退化为半透明色块，遮挡依然有效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel("颜色")
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            }

            Spacer(Modifier.height(20.dp))
            SliderRow(
                label = "不透明度",
                valueText = "${(alpha / 2.55f).roundToInt()}%",
                value = alpha,
                range = 0f..255f,
                onValueChange = { alpha = it }
            )

            if (block.mode == BlockMode.BLUR) {
                SliderRow(
                    label = "模糊强度",
                    valueText = "${blur.roundToInt()}",
                    value = blur,
                    range = 1f..60f,
                    onValueChange = { blur = it }
                )
            }

            SliderRow(
                label = "圆角",
                valueText = "${corner.roundToInt()} dp",
                value = corner,
                range = 0f..40f,
                onValueChange = { corner = it }
            )

            Spacer(Modifier.height(10.dp))
            SectionLabel("快速定位")
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickPosition("底部字幕", 0.03f, 0.80f, 0.94f, 0.115f, latest, onChange)
                QuickPosition("居中", 0.08f, 0.82f, 0.84f, 0.09f, latest, onChange)
                QuickPosition("顶部", 0f, 0.045f, 1f, 0.16f, latest, onChange)
                QuickPosition("铺满全屏", 0f, 0f, 1f, 1f, latest, onChange)
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("锁定位置", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "锁定后拖不动，避免看视频时手滑移位",
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
                Text("删除这一块")
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
 */
@Composable
private fun BlockPreview(block: BlockConfig) {
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
            "原本这里有一行碍事的字幕",
            color = Color(0xFFEDEDED),
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
        )

        // 遮挡块本体
        Box(
            modifier = Modifier
                .offset(x = w * block.nx, y = h * block.ny)
                .size(width = w * block.nw, height = h * block.nh)
                .clip(RoundedCornerShape(block.corner))
                .background(
                    Color(block.color).copy(alpha = block.alpha / 255f)
                )
                .border(
                    1.dp,
                    Color(0x44FFFFFF),
                    RoundedCornerShape(block.corner)
                )
        )
    }
}
