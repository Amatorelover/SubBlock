package com.yanhu.subblock.data

/**
 * 预设模板。
 *
 * 这些数字全是"占屏幕的比例"，所以同一套预设在任何手机上都能落在同一位置。
 * 你可以照着这个格式自己加条目——这是最容易上手的改动。
 */
object Presets {

    data class Preset(
        val name: String,
        val desc: String,
        val build: () -> List<BlockConfig>
    )

    private fun block(
        name: String,
        nx: Float, ny: Float, nw: Float, nh: Float,
        color: Int = 0xFF000000.toInt(),
        alpha: Int = 240,
        mode: BlockMode = BlockMode.SOLID,
        corner: Float = 14f
    ) = BlockConfig(
        id = BlockConfig.newId(),
        name = name,
        nx = nx, ny = ny, nw = nw, nh = nh,
        color = color, alpha = alpha, mode = mode, corner = corner
    )

    val all: List<Preset> = listOf(
        Preset(
            name = "短视频底部字幕",
            desc = "抖音 / 快手 / TikTok / 小红书 的硬字幕",
            build = {
                listOf(
                    block("短视频字幕", nx = 0.02f, ny = 0.755f, nw = 0.96f, nh = 0.155f)
                )
            }
        ),
        Preset(
            name = "顶部弹幕",
            desc = "B站 / 直播 顶部飘过的弹幕区",
            build = {
                listOf(
                    block("顶部弹幕", nx = 0f, ny = 0.045f, nw = 1f, nh = 0.20f)
                )
            }
        ),
        Preset(
            name = "底部字幕 + 顶部弹幕",
            desc = "追剧最常用：上下一起挡住",
            build = {
                listOf(
                    block("底部字幕", nx = 0.03f, ny = 0.80f, nw = 0.94f, nh = 0.115f),
                    block("顶部弹幕", nx = 0f, ny = 0.045f, nw = 1f, nh = 0.20f)
                )
            }
        ),
        Preset(
            name = "居中大字幕",
            desc = "外语片 / 纪录片常见的画面中央字幕",
            build = {
                listOf(
                    block("中央字幕", nx = 0.08f, ny = 0.80f, nw = 0.84f, nh = 0.10f)
                )
            }
        ),
        Preset(
            name = "短视频右侧按钮",
            desc = "挡住点赞 / 评论 / 分享那一列图标",
            build = {
                listOf(
                    block("右侧按钮", nx = 0.80f, ny = 0.42f, nw = 0.19f, nh = 0.42f)
                )
            }
        ),
        Preset(
            name = "竖屏底部全宽",
            desc = "最省事的兜底方案：底部一整条全盖",
            build = {
                listOf(
                    block("底部全宽", nx = 0f, ny = 0.78f, nw = 1f, nh = 0.14f)
                )
            }
        )
    )
}
