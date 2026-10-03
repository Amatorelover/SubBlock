package com.yanhu.subblock.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 遮挡方式。
 *
 * 注意：枚举名会被原样写进 JSON（`toJson` 存 `mode.name`），
 * 所以**这些名字是持久化格式的一部分，不能随意改**——
 * 改了会导致老配置读不回来。新增样式只能往后追加。
 */
enum class BlockMode {
    /** 实心色块：最简单可靠，所有 Android 版本都能用 */
    SOLID,

    /** 毛玻璃：模糊背后的内容，需要 Android 12+，低版本自动退化为半透明色块 */
    BLUR,

    /** 渐变羽化：上下边缘渐隐，遮挡与画面的接缝更自然 */
    FEATHER,

    /** 斜纹：斜向条纹，视觉上更像"贴上去的一条"，遮挡感明确 */
    STRIPES,

    /** 点阵：细密网点，比实心色块轻，但仍然挡住文字 */
    DOTS
}

/**
 * 单个遮挡块的配置。
 *
 * 设计上有个关键决定：位置和尺寸存的是 **0~1 的比例**，不是像素。
 * 为什么？因为手机屏幕分辨率各不相同，还可能横竖屏切换。
 * 存比例的话，同一份配置在任何屏幕上都能按同样比例还原。
 */
data class BlockConfig(
    val id: String,
    /**
     * 名称。默认留空，由界面层填上"当地语言"的默认名——
     * 数据层不该知道用户说什么语言。
     */
    val name: String = "",
    /** 左边缘位置，占屏宽的比例 0~1 */
    val nx: Float = 0.05f,
    /** 上边缘位置，占屏高的比例 0~1 */
    val ny: Float = 0.78f,
    /** 宽度占屏宽比例 */
    val nw: Float = 0.90f,
    /** 高度占屏高比例 */
    val nh: Float = 0.12f,
    /** 遮挡颜色（ARGB 整数） */
    val color: Int = 0xFF000000.toInt(),
    /** 不透明度 0~255 */
    val alpha: Int = 240,
    val mode: BlockMode = BlockMode.SOLID,
    /** 毛玻璃模糊半径，越大越糊 */
    val blurRadius: Int = 24,
    /** 圆角半径，单位 dp */
    val corner: Float = 14f,
    /** 锁定后不可拖动，防止手滑 */
    val locked: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("nx", nx.toDouble())
        put("ny", ny.toDouble())
        put("nw", nw.toDouble())
        put("nh", nh.toDouble())
        put("color", color)
        put("alpha", alpha)
        put("mode", mode.name)
        put("blur", blurRadius)
        put("corner", corner.toDouble())
        put("locked", locked)
    }

    companion object {
        fun fromJson(o: JSONObject): BlockConfig = BlockConfig(
            id = o.optString("id").ifBlank { newId() },
            name = o.optString("name", ""),
            nx = o.optDouble("nx", 0.05).toFloat(),
            ny = o.optDouble("ny", 0.78).toFloat(),
            nw = o.optDouble("nw", 0.90).toFloat(),
            nh = o.optDouble("nh", 0.12).toFloat(),
            color = o.optInt("color", 0xFF000000.toInt()),
            alpha = o.optInt("alpha", 240),
            mode = runCatching { BlockMode.valueOf(o.optString("mode", "SOLID")) }
                .getOrDefault(BlockMode.SOLID),
            blurRadius = o.optInt("blur", 24),
            corner = o.optDouble("corner", 14.0).toFloat(),
            locked = o.optBoolean("locked", false)
        )

        fun newId(): String = java.util.UUID.randomUUID().toString()

        fun listToJson(list: List<BlockConfig>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(raw: String?): List<BlockConfig> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }
    }
}
