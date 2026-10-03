package com.yanhu.subblock.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Square
import androidx.compose.ui.graphics.vector.ImageVector
import com.yanhu.subblock.R
import com.yanhu.subblock.data.BlockConfig
import com.yanhu.subblock.data.BlockMode
import com.yanhu.subblock.data.ConfigIO

/**
 * 数据层 → 界面文字的翻译层。
 *
 * 数据层（ConfigIO）只产出"原因类型"，不认识任何语言；
 * 这里负责把它翻成当前语言的句子。好处是：
 *   - 加一门语言 = 加一份 strings.xml，不用改任何逻辑代码
 *   - 逻辑层可以脱离 Android 单独跑测试（没有 Context 依赖）
 *
 * 为什么是普通函数 + Context 参数，而不是 `@Composable` + stringResource？
 * 因为这里的结果同时要用在**两种场合**：组合环境（界面里显示），
 * 以及**普通回调**（点击、文件选择器返回后弹 Toast）。
 * `@Composable` 函数只能在前者里调用，写成 Context 版本两种场合都能用。
 * Context 本身已带着当前语言（见 AppLocale），所以翻译结果一样准确。
 */

/** 某一块遮挡的显示名：用户没起名就用当前语言的默认名 */
fun displayName(context: Context, block: BlockConfig): String =
    block.name.ifBlank { context.getString(R.string.block_default_name) }

/** 遮挡方式的名字 */
fun modeLabel(context: Context, mode: BlockMode): String = context.getString(
    when (mode) {
        BlockMode.SOLID -> R.string.mode_solid
        BlockMode.BLUR -> R.string.mode_blur
        BlockMode.FEATHER -> R.string.mode_feather
    }
)

/** 遮挡方式的图标（列表里一眼区分） */
fun modeIconOf(mode: BlockMode): ImageVector = when (mode) {
    BlockMode.SOLID -> Icons.Filled.Square
    BlockMode.BLUR -> Icons.Filled.BlurOn
    BlockMode.FEATHER -> Icons.Filled.Gradient
}

/** 导入失败的原因 */
fun reasonText(context: Context, reason: ConfigIO.Reason): String = when (reason) {
    ConfigIO.Reason.EmptyFile -> context.getString(R.string.import_err_empty)
    ConfigIO.Reason.TooLarge -> context.getString(R.string.import_err_too_large)
    ConfigIO.Reason.NotJson -> context.getString(R.string.import_err_not_json)
    is ConfigIO.Reason.ForeignApp ->
        context.getString(R.string.import_err_foreign_app, reason.app)
    ConfigIO.Reason.NoBlocksField -> context.getString(R.string.import_err_no_blocks_field)
    ConfigIO.Reason.NotAContainer -> context.getString(R.string.import_err_top_level)
    ConfigIO.Reason.NoBlocks -> context.getString(R.string.import_err_no_blocks)
    ConfigIO.Reason.AllUnrecognized -> context.getString(R.string.import_err_all_unrecognized)
}

/** 导入时自动修正的说明 */
fun warningText(context: Context, warning: ConfigIO.Warning): String = when (warning) {
    is ConfigIO.Warning.SchemaNewer ->
        context.getString(R.string.import_warn_schema_newer, warning.schema)
    is ConfigIO.Warning.Truncated ->
        context.getString(R.string.import_warn_truncated, warning.total, warning.max)
    is ConfigIO.Warning.Skipped ->
        context.getString(R.string.import_warn_skipped, warning.count)
    is ConfigIO.Warning.IdFixed ->
        context.getString(R.string.import_warn_id, warning.count)
    is ConfigIO.Warning.ParamFixed ->
        context.getString(R.string.import_warn_param, warning.count)
}
