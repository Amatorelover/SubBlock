package com.yanhu.subblock.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 配置的导入 / 导出。
 *
 * 这一层刻意**不碰任何 Android 文件 API**，只在 String 和数据结构之间转换。
 * 为什么这样切？因为"读文件"和"校验数据"是两件独立的事：
 *   - 文件可能读不到（用户取消、权限、Uri 失效）—— 那是 UI 层的事
 *   - 内容可能是坏的、伪造的、来自别的 App —— 那是这一层的事
 * 混在一起写，报错时就分不清是"读失败"还是"内容不对"。
 *
 * 同理，它也不产出任何**人类语言**：失败与警告都以"类型"返回
 * （[Reason] / [Warning]），由界面层翻译成中文或英文。
 * 否则加英文版时就得在这一层里塞 if-else 判断语言——那才是真正的坏味道。
 *
 * 导出的 JSON 长这样（带元信息，便于识别、排错和将来升级格式）：
 * ```
 * {
 *   "app": "SubBlock",
 *   "schema": 1,
 *   "appVersion": "1.3.0",
 *   "exportedAt": "2026-10-03T16:10:00+0800",
 *   "enabled": true,
 *   "count": 3,
 *   "blocks": [ { ... }, { ... } ]
 * }
 * ```
 */
object ConfigIO {

    /** 写入文件的身份标记。导入时用它判断"这是不是遮幕的配置"。 */
    const val APP_TAG = "SubBlock"

    /** 配置格式版本。将来字段结构升级时递增，导入端据此决定兼容策略。 */
    const val SCHEMA = 1

    /** 单次导入的块数上限。防的是"损坏或恶意文件塞进十万块"导致卡死/OOM。 */
    const val MAX_BLOCKS = 50

    private const val MAX_NAME_LEN = 40

    /** 导入失败的原因（不含文字，由界面翻译） */
    sealed interface Reason {
        object EmptyFile : Reason
        object TooLarge : Reason
        object NotJson : Reason
        data class ForeignApp(val app: String) : Reason
        object NoBlocksField : Reason
        object NotAContainer : Reason
        object NoBlocks : Reason
        object AllUnrecognized : Reason
    }

    /** 导入过程中自动修正过的地方——必须让用户看见，不能悄悄改他的数据 */
    sealed interface Warning {
        data class SchemaNewer(val schema: Int) : Warning
        data class Truncated(val total: Int, val max: Int) : Warning
        data class Skipped(val count: Int) : Warning
        data class IdFixed(val count: Int) : Warning
        data class ParamFixed(val count: Int) : Warning
    }

    sealed interface ImportResult {
        data class Success(val blocks: List<BlockConfig>, val warnings: List<Warning>) : ImportResult
        data class Failure(val reason: Reason) : ImportResult
    }

    // ---------------------------------------------------------------- 导出

    fun exportJson(blocks: List<BlockConfig>, enabled: Boolean, appVersion: String): String {
        val root = JSONObject().apply {
            put("app", APP_TAG)
            put("schema", SCHEMA)
            put("appVersion", appVersion)
            put("exportedAt", timestamp())
            put("enabled", enabled)
            put("count", blocks.size)
            put("blocks", JSONArray().apply { blocks.forEach { put(it.toJson()) } })
        }
        // 缩进 2 空格：导出文件用任何文本编辑器打开都能读懂，便于手动改
        return root.toString(2)
    }

    /** 建议的文件名。带上日期时间，导出多份时不会互相覆盖、也分得清先后。 */
    fun suggestFileName(): String =
        "subblock-config-" +
            SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) +
            ".json"

    // ---------------------------------------------------------------- 导入

    /**
     * 解析导入内容。**永远不抛异常**，所有异常路径都翻译成可翻译的"原因类型"。
     *
     * 校验分三层，缺一不可：
     *   1. 结构性：是不是合法 JSON？是不是遮幕导出的？
     *   2. 值域性：位置/透明度这些数字是否在合理区间？（外部输入一律不可信）
     *   3. 一致性：id 是否重复？块数是否超限？
     *
     * @param defaultName 名称为空时使用的兜底名（由界面层传入，已按当前语言翻译好）
     */
    fun parse(raw: String, defaultName: String): ImportResult {
        val text = raw.trim()
        if (text.isEmpty()) return ImportResult.Failure(Reason.EmptyFile)
        if (text.length > 4 * 1024 * 1024) return ImportResult.Failure(Reason.TooLarge)

        val value = runCatching { JSONTokener(text).nextValue() }.getOrNull()
            ?: return ImportResult.Failure(Reason.NotJson)

        var schema = SCHEMA
        val blocksArray: JSONArray = when (val v = value) {
            // 兼容形态一：直接就是块数组（方便手写配置）
            is JSONArray -> v

            // 兼容形态二：带元信息的完整配置对象（正常导出的样子）
            is JSONObject -> {
                val tag = v.optString("app", "")
                if (tag.isNotEmpty() && tag != APP_TAG) {
                    return ImportResult.Failure(Reason.ForeignApp(tag))
                }
                schema = v.optInt("schema", SCHEMA)
                v.optJSONArray("blocks")
                    ?: return ImportResult.Failure(Reason.NoBlocksField)
            }

            else -> return ImportResult.Failure(Reason.NotAContainer)
        }

        if (blocksArray.length() == 0) {
            return ImportResult.Failure(Reason.NoBlocks)
        }

        val warnings = mutableListOf<Warning>()
        val result = mutableListOf<BlockConfig>()
        val takenIds = mutableSetOf<String>()

        var skipped = 0
        var idFixed = 0
        var paramFixed = 0

        val limit = minOf(blocksArray.length(), MAX_BLOCKS)
        for (i in 0 until limit) {
            // 数组里可能混进非对象元素（比如手写配置时写错了），跳过并记账。
            // 注意这里用显式 if 而不是 `?: run { ...; continue }` ——
            // 后者把 continue 写在 lambda 里，依赖"非局部跳转"的编译支持，不值得赌。
            val obj = blocksArray.optJSONObject(i)
            if (obj == null) {
                skipped++
                continue
            }

            val original = BlockConfig.fromJson(obj)
            val normalized = normalize(original, takenIds, defaultName)

            if (normalized.id != original.id) idFixed++
            // 只比较除 id 之外的字段，避免与上面的 id 统计重复计数
            if (normalized.copy(id = original.id) != original) paramFixed++

            result += normalized
        }

        if (result.isEmpty()) {
            return ImportResult.Failure(Reason.AllUnrecognized)
        }

        if (schema > SCHEMA) warnings += Warning.SchemaNewer(schema)
        if (blocksArray.length() > MAX_BLOCKS) {
            warnings += Warning.Truncated(total = blocksArray.length(), max = MAX_BLOCKS)
        }
        if (skipped > 0) warnings += Warning.Skipped(skipped)
        if (idFixed > 0) warnings += Warning.IdFixed(idFixed)
        if (paramFixed > 0) warnings += Warning.ParamFixed(paramFixed)

        return ImportResult.Success(result, warnings)
    }

    /**
     * 把外部数据"驯服"成安全可用的配置。
     *
     * 这里体现一条重要原则：**来自文件的数据和来自用户手指的数据，可信度不一样。**
     * 手指拖出来的位置天然在屏幕内；文件里的 0.0~1.0 之外的值却可能让遮挡块
     * 直接跑到屏幕外，用户只会看到"导入了但什么都没有"。所以边界必须在这里收住。
     */
    private fun normalize(raw: BlockConfig, takenIds: MutableSet<String>, defaultName: String): BlockConfig {
        val candidateId = raw.id.trim().ifBlank { BlockConfig.newId() }
        val uniqueId = if (takenIds.add(candidateId)) {
            candidateId
        } else {
            BlockConfig.newId().also { takenIds.add(it) }
        }

        return raw.copy(
            id = uniqueId,
            name = raw.name.trim().ifBlank { defaultName }.take(MAX_NAME_LEN),
            nx = raw.nx.coerceIn(0f, 0.98f),
            ny = raw.ny.coerceIn(0f, 0.98f),
            nw = raw.nw.coerceIn(0.05f, 1f),
            nh = raw.nh.coerceIn(0.03f, 1f),
            alpha = raw.alpha.coerceIn(0, 255),
            blurRadius = raw.blurRadius.coerceIn(0, 100),
            corner = raw.corner.coerceIn(0f, 64f)
        )
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date())
}
