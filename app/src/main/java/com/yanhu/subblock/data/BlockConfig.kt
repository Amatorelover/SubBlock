package com.yanhu.subblock.data

import org.json.JSONArray
import org.json.JSONObject

/** 遮挡方式：纯色块 / 毛玻璃模糊 */
enum class BlockMode { SOLID, BLUR }

/**
 * 单个遮挡块的配置。
 *
 * 设计上有个关键决定：位置和尺寸存的是 **0~1 的比例**，不是像素。
 * 为什么？因为手机屏幕分辨率各不相同，还可能横竖屏切换。
 * 存比例的话，同一份配置在任何屏幕上都能按同样比例还原。
 */
data class BlockConfig(
    val id: String,
    val name: String = "遮挡块",
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
            name = o.optString("name", "遮挡块"),
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
