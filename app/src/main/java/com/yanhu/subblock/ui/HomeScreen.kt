package com.yanhu.subblock.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yanhu.subblock.AppLocale
import com.yanhu.subblock.BuildConfig
import com.yanhu.subblock.R
import com.yanhu.subblock.data.BlockConfig
import com.yanhu.subblock.data.ConfigIO
import com.yanhu.subblock.data.Presets
import com.yanhu.subblock.data.SettingsStore
import com.yanhu.subblock.overlay.OverlayService
import com.yanhu.subblock.overlay.OverlayStatus
import com.yanhu.subblock.overlay.OverlayStatusBus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    store: SettingsStore,
    hasOverlayPermission: Boolean,
    focusBlockId: String?,
    language: String,
    onFocusHandled: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onLanguageChange: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val enabled by store.enabled.collectAsState(initial = false)
    val blocks by store.blocks.collectAsState(initial = emptyList())
    val overlayStatus by OverlayStatusBus.state.collectAsState()

    var editingId by remember { mutableStateOf<String?>(null) }
    var showPresets by remember { mutableStateOf(false) }
    // 刚从文件里解析出来、等待用户确认后才落库的配置
    var pendingImport by remember { mutableStateOf<ConfigIO.ImportResult.Success?>(null) }

    // 预设模板里的文字是"当前语言"的，所以每次重组都按当前语言取一遍
    val presets = remember(context, language) { Presets.all(context) }
    val defaultBlockName = stringResource(R.string.block_default_name)

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

    // ---------------- 配置导出 ----------------
    // 用系统的"新建文档"选择器：用户自己决定存到下载目录、U 盘还是网盘。
    // 关键收益：App 全程不需要任何存储权限，也就不需要向用户解释"为什么我要读你的文件"。
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult   // 用户取消了，什么都不做
        val payload = ConfigIO.exportJson(blocks, enabled, BuildConfig.VERSION_NAME)
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(payload.encodeToByteArray())
            } ?: error("cannot open output stream")
        }.isSuccess
        toast(
            if (ok) context.getString(R.string.toast_exported, blocks.size)
            else context.getString(R.string.toast_export_failed)
        )
    }

    // ---------------- 配置导入 ----------------
    // 顺序很重要：读文件 -> 解析校验 -> 等用户确认 -> 才写库。
    // 少了"确认"这一步，一次误点就会覆盖掉用户辛苦调好的配置。
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().decodeToString()
            }
        }.getOrNull()
        if (text == null) {
            toast(context.getString(R.string.toast_read_failed))
        } else {
            when (val parsed = ConfigIO.parse(text, defaultBlockName)) {
                is ConfigIO.ImportResult.Failure -> {
                    // 数据层只给"原因类型"，翻译成当前语言是界面层的职责
                    toast(reasonText(context, parsed.reason))
                }
                is ConfigIO.ImportResult.Success -> pendingImport = parsed
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Medium) },
                actions = {
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.cd_about))
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
                        stringResource(R.string.section_masks),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        pluralStringResource(R.plurals.mask_count, blocks.size, blocks.size),
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
                        toast(context.getString(R.string.toast_deleted, displayName(context, block)))
                    }
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = {
                            val newBlock = BlockConfig(
                                id = BlockConfig.newId(),
                                name = context.getString(R.string.new_mask_name, blocks.size + 1)
                            )
                            scope.launch { store.upsert(newBlock) }
                            if (!enabled) applyEnabled(true)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_new_mask))
                    }
                    OutlinedButton(
                        onClick = { showPresets = true },
                        enabled = presets.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.action_use_preset))
                    }
                }
            }

            item {
                LanguageCard(
                    current = language,
                    onPick = onLanguageChange
                )
            }

            item {
                BackupCard(
                    blockCount = blocks.size,
                    onExport = {
                        runCatching {
                            exportLauncher.launch(ConfigIO.suggestFileName())
                        }.onFailure { toast(context.getString(R.string.toast_no_file_picker)) }
                    },
                    onImport = {
                        runCatching {
                            // 这里故意用 */* 放宽类型：从微信/QQ 转存过来的文件常被系统
                            // 标成 application/octet-stream，只筛 json 会让用户"找不到文件"。
                            // 内容是否合规由 ConfigIO.parse 兜住，过滤器不必承担校验职责。
                            importLauncher.launch(arrayOf("*/*"))
                        }.onFailure { toast(context.getString(R.string.toast_no_file_picker)) }
                    }
                )
            }

            item { GestureHintCard(hasOverlayPermission = hasOverlayPermission) }
        }
    }

    if (showPresets) {
        PresetSheet(
            presets = presets,
            onDismiss = { showPresets = false },
            onPick = { preset ->
                val created = preset.build()
                scope.launch { store.saveBlocks(blocks + created) }
                if (!enabled) applyEnabled(true)
                showPresets = false
                toast(context.getString(R.string.toast_added_preset, preset.name))
            }
        )
    }

    pendingImport?.let { incoming ->
        ImportConfirmDialog(
            incoming = incoming,
            currentCount = blocks.size,
            onDismiss = { pendingImport = null },
            onConfirm = {
                // 写进 DataStore 就够了 —— 悬浮窗服务订阅着同一个数据源，
                // 会自己重画，不需要任何"通知服务去刷新"的代码。
                scope.launch { store.saveBlocks(incoming.blocks) }
                pendingImport = null
                toast(context.getString(R.string.toast_imported, incoming.blocks.size))
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
                toast(context.getString(R.string.toast_deleted, displayName(context, editing)))
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
                    stringResource(if (enabled) R.string.master_on_title else R.string.master_off_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = onContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(if (enabled) R.string.master_on_sub else R.string.master_off_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer.copy(alpha = 0.8f)
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun ServiceStatusCard(status: OverlayStatus) {
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
                    if (status.onScreen > 0) {
                        pluralStringResource(R.plurals.masks_on_screen, status.onScreen, status.onScreen)
                    } else {
                        stringResource(R.string.service_waiting)
                    },
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
                    stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onGrant) { Text(stringResource(R.string.permission_grant)) }
                OutlinedButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.permission_app_settings))
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
                stringResource(R.string.empty_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.empty_body),
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
    val context = LocalContext.current
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
                        displayName(context, block),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    if (block.locked) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = stringResource(R.string.cd_locked),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = modeIconOf(block.mode),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        stringResource(
                            R.string.block_meta,
                            modeLabel(context, block.mode),
                            block.alpha * 100 / 255
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.cd_delete),
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
                stringResource(R.string.gestures_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.gesture_move)) })
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.gesture_resize)) })
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.gesture_lock)) })
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.gesture_edit)) })
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(
                    if (hasOverlayPermission) R.string.gesture_hint_autostart
                    else R.string.gesture_hint_permission
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 语言选择。
 *
 * 三个选项的名字刻意用**各自的语言**书写（"简体中文"、"English"），
 * 而不是把它们翻译成当前界面语言——否则一个只会中文的用户
 * 在英文界面里看到的全是英文选项，反而找不到怎么切回来。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageCard(current: String, onPick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Language,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.language_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppLocale.choices.forEach { value ->
                    FilterChip(
                        selected = current == value,
                        onClick = { if (current != value) onPick(value) },
                        label = { Text(AppLocale.labelOf(LocalContext.current, value)) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.language_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BackupCard(blockCount: Int, onExport: () -> Unit, onImport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.backup_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.backup_body, blockCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Filled.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.backup_export))
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Filled.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.backup_import))
                }
            }
        }
    }
}

@Composable
private fun ImportConfirmDialog(
    incoming: ConfigIO.ImportResult.Success,
    currentCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.import_body, incoming.blocks.size, currentCount),
                    style = MaterialTheme.typography.bodyMedium
                )
                // 自动修正过的地方必须让用户看见，不能悄悄改掉他的数据
                if (incoming.warnings.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    incoming.warnings.forEach { w ->
                        Text(
                            "· ${warningText(context, w)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (currentCount > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.import_overwrite_hint, currentCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.import_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
