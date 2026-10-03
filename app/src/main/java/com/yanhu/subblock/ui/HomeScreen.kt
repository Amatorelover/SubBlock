package com.yanhu.subblock.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yanhu.subblock.data.BlockConfig
import com.yanhu.subblock.data.BlockMode
import com.yanhu.subblock.data.Presets
import com.yanhu.subblock.data.SettingsStore
import com.yanhu.subblock.overlay.OverlayService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    store: SettingsStore,
    hasOverlayPermission: Boolean,
    focusBlockId: String?,
    onFocusHandled: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val enabled by store.enabled.collectAsState(initial = false)
    val blocks by store.blocks.collectAsState(initial = emptyList())
    val overlayStatus by com.yanhu.subblock.overlay.OverlayStatusBus.state.collectAsState()

    var editingId by remember { mutableStateOf<String?>(null) }
    var showPresets by remember { mutableStateOf(false) }

    // 从悬浮窗长按跳进来时，直接打开对应那块的编辑面板
    LaunchedEffect(focusBlockId) {
        if (focusBlockId != null) {
            editingId = focusBlockId
            onFocusHandled()
        }
    }

    fun applyEnabled(on: Boolean) {
        if (on && !hasOverlayPermission) {
            onRequestOverlayPermission()
            return
        }
        // 关键：先写数据，写完再启动服务。
        // 如果两件事并行，服务可能在数据落盘前醒来，读到旧的"关闭"状态——
        // 曾经因此出现"开关显示已开启、屏幕上却没有遮挡块"的竞态。
        scope.launch {
            store.setEnabled(on)
            if (on) OverlayService.start(context) else OverlayService.stop(context)
        }
    }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("遮幕", fontWeight = FontWeight.Medium) },
                actions = {
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Filled.Info, contentDescription = "关于")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item { MasterSwitchCard(enabled = enabled, onToggle = ::applyEnabled) }

            if (enabled) {
                item { ServiceStatusCard(status = overlayStatus) }
            }

            if (!hasOverlayPermission) {
                item {
                    PermissionCard(
                        onGrant = onRequestOverlayPermission,
                        onOpenSettings = onOpenSystemSettings
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "遮挡区域",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${blocks.size} 块",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (blocks.isEmpty()) {
                item { EmptyHint() }
            }

            items(blocks, key = { it.id }) { block ->
                BlockCard(
                    block = block,
                    onClick = { editingId = block.id },
                    onDelete = {
                        scope.launch { store.remove(block.id) }
                        toast("已删除「${block.name}」")
                    }
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = {
                            val newBlock = BlockConfig(
                                id = BlockConfig.newId(),
                                name = "遮挡块 ${blocks.size + 1}"
                            )
                            scope.launch { store.upsert(newBlock) }
                            if (!enabled) applyEnabled(true)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("新建一块")
                    }
                    OutlinedButton(
                        onClick = { showPresets = true },
                        enabled = Presets.all.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("用预设模板")
                    }
                }
            }

            item { GestureHintCard(hasOverlayPermission = hasOverlayPermission) }
        }
    }

    if (showPresets) {
        PresetSheet(
            onDismiss = { showPresets = false },
            onPick = { preset ->
                val created = preset.build()
                scope.launch { store.saveBlocks(blocks + created) }
                if (!enabled) applyEnabled(true)
                showPresets = false
                toast("已添加「${preset.name}」")
            }
        )
    }

    val editing = blocks.firstOrNull { it.id == editingId }
    if (editing != null) {
        BlockEditorSheet(
            block = editing,
            onDismiss = { editingId = null },
            onChange = { updated -> scope.launch { store.upsert(updated) } },
            onDelete = {
                scope.launch { store.remove(editing.id) }
                editingId = null
                toast("已删除「${editing.name}」")
            }
        )
    }
}

@Composable
private fun MasterSwitchCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val container = if (enabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val onContainer = if (enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (enabled) "遮挡已开启" else "遮挡已关闭",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = onContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (enabled) "下拉通知栏磁贴也能一键开关" else "打开后遮挡块会立即出现在屏幕上",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer.copy(alpha = 0.8f)
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun ServiceStatusCard(status: com.yanhu.subblock.overlay.OverlayStatus) {
    val hasError = status.error != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasError) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    status.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            } else {
                Text(
                    if (status.onScreen > 0) "服务运行中 · ${status.onScreen} 块遮挡已上屏"
                    else "服务运行中 · 等待遮挡块上屏…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "还差一步：悬浮窗权限",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "遮挡块本质上是系统级悬浮窗，必须由你手动授权。打开后找到「遮幕」，把开关打开即可。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onGrant) { Text("去授权") }
                OutlinedButton(onClick = onOpenSettings) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("应用设置")
                }
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "还没有遮挡区域",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "点「新建一块」从零开始，或直接套用一个预设模板。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BlockCard(
    block: BlockConfig,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 颜色预览：一眼看出这块是什么颜色、多透明
            Box(
                modifier = Modifier
                    .size(width = 46.dp, height = 32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(block.color).copy(alpha = block.alpha / 255f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(9.dp)
                    )
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        block.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    if (block.locked) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "已锁定",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (block.mode == BlockMode.BLUR) Icons.Filled.BlurOn
                        else Icons.Filled.Square,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        buildString {
                            append(if (block.mode == BlockMode.BLUR) "毛玻璃" else "实心色块")
                            append(" · ")
                            append("${(block.alpha * 100 / 255)}% 不透明")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GestureHintCard(hasOverlayPermission: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "屏幕上怎么操作",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("按住拖动 = 移动") })
                AssistChip(onClick = {}, label = { Text("右下角手柄 = 缩放") })
                AssistChip(onClick = {}, label = { Text("双击 = 锁定") })
                AssistChip(onClick = {}, label = { Text("长按 = 回到这里编辑") })
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (hasOverlayPermission) {
                    "提示：如果遮挡块在重启后消失，把「遮幕」加入系统的自启动白名单即可。"
                } else {
                    "先完成上面的悬浮窗授权，手势才能生效。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
