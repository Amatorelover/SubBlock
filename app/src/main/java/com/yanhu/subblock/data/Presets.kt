package com.yanhu.subblock.data

import android.content.Context
import androidx.annotation.StringRes
import com.yanhu.subblock.R

/**
 * 预设模板。
 *
 * 这些数字全是"占屏幕的比例"，所以同一套预设在任何手机上都能落在同一位置。
 * 你可以照着这个格式自己加条目——这是最容易上手的改动。
 *
 * 关于多语言：这里只存**资源 id**，不存文字。
 * `Presets.all(context)` 在调用时才把 id 翻成当前语言的文字。
 * 如果这里直接写死中文字符串，那么加英文界面时就得把文件整份复制一遍——
 * 那种"两份真相"的写法，迟早会有一份忘记更新。
 */
object Presets {

    data class Preset(
        val name: String,
        val desc: String,
        val build: () -> List<BlockConfig>
    )

    /** 一块遮挡的"规格"，名字用资源 id 表示 */
    private data class Spec(
        @StringRes val nameRes: Int,
        val nx: Float, val ny: Float, val nw: Float, val nh: Float,
        val mode: BlockMode = BlockMode.SOLID,
        val alpha: Int = 240,
        val corner: Float = 14f
    )

    fun all(context: Context): List<Preset> {
        // 只持有 Application Context，避免把 Activity 挂在 lambda 里
        val app = context.applicationContext ?: context
        return listOf(
            make(
                app, R.string.preset_short_name, R.string.preset_short_desc,
                listOf(Spec(R.string.block_name_short_sub, 0.02f, 0.755f, 0.96f, 0.155f))
            ),
            make(
                app, R.string.preset_top_name, R.string.preset_top_desc,
                listOf(Spec(R.string.block_name_top_danmaku, 0f, 0.045f, 1f, 0.20f))
            ),
            make(
                app, R.string.preset_both_name, R.string.preset_both_desc,
                listOf(
                    Spec(R.string.block_name_bottom_sub, 0.03f, 0.80f, 0.94f, 0.115f),
                    Spec(R.string.block_name_top_danmaku, 0f, 0.045f, 1f, 0.20f)
                )
            ),
            make(
                app, R.string.preset_center_name, R.string.preset_center_desc,
                // 居中字幕用"渐变羽化"：边缘渐隐，压在画面上不显得像一块补丁
                listOf(Spec(R.string.block_name_center_sub, 0.08f, 0.80f, 0.84f, 0.10f, mode = BlockMode.FEATHER))
            ),
            make(
                app, R.string.preset_right_name, R.string.preset_right_desc,
                listOf(Spec(R.string.block_name_right_buttons, 0.80f, 0.42f, 0.19f, 0.42f))
            ),
            make(
                app, R.string.preset_full_name, R.string.preset_full_desc,
                listOf(Spec(R.string.block_name_bottom_full, 0f, 0.78f, 1f, 0.14f))
            )
        )
    }

    private fun make(
        context: Context,
        @StringRes nameRes: Int,
        @StringRes descRes: Int,
        specs: List<Spec>
    ): Preset {
        val name = context.getString(nameRes)
        val desc = context.getString(descRes)
        return Preset(name = name, desc = desc) {
            specs.map { spec ->
                BlockConfig(
                    id = BlockConfig.newId(),
                    name = context.getString(spec.nameRes),
                    nx = spec.nx, ny = spec.ny, nw = spec.nw, nh = spec.nh,
                    mode = spec.mode, alpha = spec.alpha, corner = spec.corner
                )
            }
        }
    }
}
