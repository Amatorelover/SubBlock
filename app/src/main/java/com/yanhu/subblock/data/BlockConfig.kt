package com.yanhu.subblock.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 遮挡方式。
 *
 * 注意：枚举名会被原样写进 JSON（`toJson` 存 `mode.name`），
 * 所以**这些名字是持久化格式的一部分，不能随意改**——
 * 改了会导致老配置读不回来。新增样式只能往后追加。
 *
 * 关于被删掉的 STRIPES / DOTS：
 * 这两个名字**曾经存在过**，所以老用户的数据里可能还留着它们。
 * [BlockConfig.fromJson] 用 `runCatching { BlockMode.valueOf(...) }.getOrDefault(SOLID)`
 * 兜底，读不出来的值会静默降级成实心色块——老配置不会崩，只是那条遮挡变回纯色。
 * 名字已被"占用过"，所以将来若想恢复这两个样式，直接沿用旧名即可，不会与历史数据冲突。
 */
enum class BlockMode {
    /** 实心色块：最简单可靠，所有 Android 版本都能用 */
    SOLID,

    /** 毛玻璃：模糊背后的内容，需要 Android 12+，低版本自动退化为半透明色块 */
    BLUR,

    /** 渐变羽化：上下边缘渐隐，遮挡与画面的接缝更自然 */
    FEATHER
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
    /**
     * 把配置收束到「合法且完整可见」的范围。
     *
     * 这是**全项目唯一**一处定义几何边界的代码。手指拖动、缩放落盘、
     * 界面里的快捷定位、导入解析、DataStore 落盘——所有写入路径都经过它，
     * 于是「数据是否越界」这个问题的答案只有一个来源，不会各写各的。
     *
     * 为什么是「完整可见」而不是「至少露出一部分」？
     * 遮挡条的本职就是在屏幕内挡住字幕：一块大半个在屏外的遮挡，既挡不住东西，
     * 用户也很难再把它抓回来。让它永远完整待在屏内，「拖丢了找不回来」
     * 就在结构上不可能发生——而那正是用户最初报上来的症状。
     *
     * 关键在**跨字段**约束：nx 与 nw 是互相牵连的，宽度越大，左边缘可移动的
     * 范围越小（nx 的上限是 `1 - nw`）。逐字段各自限制在 [0,1] 是不够的——
     * `nx=0.98` 与 `nw=1.0` 各自都「合法」，合起来却跑到屏幕外。
     * 这就是 [ConfigIO] 里那套「逐字段校验」漏掉的一环。
     */
    fun constrained(): BlockConfig {
        val w = nw.coerceIn(MIN_NW, MAX_N)
        val h = nh.coerceIn(MIN_NH, MAX_N)
        return copy(
            nw = w,
            nh = h,
            // 先定尺寸、再定位置：位置的可动范围由尺寸反推而来，顺序不能颠倒。
            nx = nx.coerceIn(0f, MAX_N - w),
            ny = ny.coerceIn(0f, MAX_N - h),
            alpha = alpha.coerceIn(0, MAX_ALPHA),
            blurRadius = blurRadius.coerceIn(0, MAX_BLUR),
            corner = corner.coerceIn(0f, MAX_CORNER)
        )
    }

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
        // 几何与外观的取值范围。全项目只在 [constrained] 一处使用它们——
        // 想调整边界，改这里就够了，不必去各个写入路径里逐个找。
        /** 最小宽度（占屏宽比例）。太窄会连拖动手柄都捏不住 */
        const val MIN_NW = 0.05f
        /** 最小高度（占屏高比例） */
        const val MIN_NH = 0.03f
        /** 位置与尺寸的统一上限，同时也是"屏幕"的归一化边界 */
        const val MAX_N = 1f
        const val MAX_ALPHA = 255
        const val MAX_BLUR = 100
        const val MAX_CORNER = 64f

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
