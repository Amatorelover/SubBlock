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
 * ---------- 关于多语言（这里曾经有个坑，值得记住）----------
 * 预设里的文字必须跟着用户选的语言走，但**不能为此持有 Context**。两条错路：
 *
 *   错路一：用调用方传进来的 Activity Context 取文字。
 *           文字是对的，但 [Preset] 会被界面长期持有（用户可能一直不点它），
 *           等于把整个 Activity 钉在内存里 —— 内存泄漏。
 *
 *   错路二：用 `context.applicationContext` 取文字。
 *           不会泄漏，但 Application 的资源**永远是系统语言**
 *           （`AppLocale.wrap` 的语言包装只在 Activity 上生效），
 *           于是预设名字死活不跟着语言变。**这曾经就是本项目的 bug**：
 *           用户把界面切成中文，预设列表还是英文。
 *
 * 破法是**两条路都不走**：在 [all] 里就把文字翻译好，存成普通 String。
 * 翻译发生在调用那一刻（此刻传入的 context 还带着正确的语言），
 * 而产出的 [Preset]、[Blueprint] 里**没有任何 Context** ——
 * 既不泄漏，也不可能说错语言。结构上让 bug 无处藏身，比事后打补丁更可靠。
 */
object Presets {

    /**
     * 一条预设。
     *
     * 注意它里面全是纯数据加一个"造块函数"：没有任何 Context，也没有资源 id，
     * 所以它可以被界面安全地长期持有。
     *
     * 为什么存一个 lambda 而不是直接存 `List<BlockConfig>` 模板？
     * 因为模板里的 id 会是假的（真正要用的 id 必须每次现生成），
     * 留一个"不是 id 的 id"在数据里，迟早有人误用。存"造法"比存"半成品"更诚实。
     *
     * 这里也**不能**把参数类型写成私有的 `Blueprint`：Kotlin 不允许
     * "public 的构造器接收 private 的类型"（公开的门通不到私有的房间）。
     * 所以 lambda 的签名只用公开类型 `() -> List<BlockConfig>`，
     * 私有类型只作为 lambda 的**捕获内容**存在——捕获不进签名，编译器就不拦。
     */
    class Preset(
        val name: String,
        val desc: String,
        /** 这个预设会一次创建几块遮挡。界面只要展示这个数字，不必真去 build 一遍 */
        val count: Int,
        private val factory: () -> List<BlockConfig>
    ) {
        /** 每次调用都产出**全新的 id**：同一条预设可以被反复使用，互不干扰 */
        fun build(): List<BlockConfig> = factory()
    }

    /** 已经翻译好的一块遮挡"规格"：纯数据，不含 Context，也不含资源 id */
    private data class Blueprint(
        val name: String,
        val nx: Float, val ny: Float, val nw: Float, val nh: Float,
        val mode: BlockMode = BlockMode.SOLID,
        val alpha: Int = 240,
        val corner: Float = 14f
    ) {
        fun toBlockConfig() = BlockConfig(
            id = BlockConfig.newId(),
            name = name,
            nx = nx, ny = ny, nw = nw, nh = nh,
            mode = mode, alpha = alpha, corner = corner
        )
    }

    /** 声明一条遮挡时用的原始写法：名字还是资源 id，等 [all] 时再按当前语言翻译 */
    private data class Spec(
        @StringRes val nameRes: Int,
        val nx: Float, val ny: Float, val nw: Float, val nh: Float,
        val mode: BlockMode = BlockMode.SOLID,
        val alpha: Int = 240,
        val corner: Float = 14f
    )

    fun all(context: Context): List<Preset> = listOf(
        make(
            context, R.string.preset_short_name, R.string.preset_short_desc,
            listOf(Spec(R.string.block_name_short_sub, 0.02f, 0.755f, 0.96f, 0.155f))
        ),
        make(
            context, R.string.preset_top_name, R.string.preset_top_desc,
            listOf(Spec(R.string.block_name_top_danmaku, 0f, 0.045f, 1f, 0.20f))
        ),
        make(
            context, R.string.preset_both_name, R.string.preset_both_desc,
            listOf(
                Spec(R.string.block_name_bottom_sub, 0.03f, 0.80f, 0.94f, 0.115f),
                Spec(R.string.block_name_top_danmaku, 0f, 0.045f, 1f, 0.20f)
            )
        ),
        make(
            context, R.string.preset_center_name, R.string.preset_center_desc,
            // 居中字幕用"渐变羽化"：边缘渐隐，压在画面上不显得像一块补丁
            listOf(Spec(R.string.block_name_center_sub, 0.08f, 0.80f, 0.84f, 0.10f, mode = BlockMode.FEATHER))
        ),
        make(
            context, R.string.preset_right_name, R.string.preset_right_desc,
            listOf(Spec(R.string.block_name_right_buttons, 0.80f, 0.42f, 0.19f, 0.42f))
        ),
        make(
            context, R.string.preset_full_name, R.string.preset_full_desc,
            listOf(Spec(R.string.block_name_bottom_full, 0f, 0.78f, 1f, 0.14f))
        )
    )

    /**
     * 翻译就发生在这里：context 由界面传入，此刻正带着用户选择的语言。
     * 出了这个函数，Context 不再被任何地方引用 —— [Preset] 里只剩纯字符串。
     */
    private fun make(
        context: Context,
        @StringRes nameRes: Int,
        @StringRes descRes: Int,
        specs: List<Spec>
    ): Preset {
        val blueprint = specs.map { spec ->
            Blueprint(
                name = context.getString(spec.nameRes),
                nx = spec.nx, ny = spec.ny, nw = spec.nw, nh = spec.nh,
                mode = spec.mode, alpha = spec.alpha, corner = spec.corner
            )
        }
        return Preset(
            name = context.getString(nameRes),
            desc = context.getString(descRes),
            count = blueprint.size,
            factory = { blueprint.map { it.toBlockConfig() } }
        )
    }
}
