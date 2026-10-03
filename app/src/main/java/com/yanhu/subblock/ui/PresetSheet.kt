package com.yanhu.subblock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yanhu.subblock.R
import com.yanhu.subblock.data.Presets

/**
 * 预设模板面板。
 *
 * 预设列表由调用方传入（已按当前语言翻译好），
 * 这一层只负责展示——这样"有哪些预设"的知识只存在于 Presets 一处。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetSheet(
    presets: List<Presets.Preset>,
    onDismiss: () -> Unit,
    onPick: (Presets.Preset) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                stringResource(R.string.preset_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.preset_sheet_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                presets.forEach { preset ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(preset) }
                            .padding(vertical = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                preset.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                pluralStringResource(
                                    R.plurals.mask_count,
                                    preset.build().size,
                                    preset.build().size
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            preset.desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
