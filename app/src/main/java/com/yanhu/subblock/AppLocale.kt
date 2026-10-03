package com.yanhu.subblock

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 应用语言。
 *
 * 为什么不用 DataStore 存语言，而用 SharedPreferences？
 * 因为语言必须在 `attachBaseContext` 里、Activity 创建之前就确定下来，
 * 而 DataStore 是异步的——在那一刻根本来不及读。SharedPreferences 是同步的，适合这里。
 * （这也是"工具要匹配场景"，而不是"哪个更现代就用哪个"。）
 *
 * 生效原理：Activity 启动前，用一个改了 Locale 的 Context 包一层，
 * 于是这个 Activity 里所有 `getString(R.string.x)` 都会走对应语言的资源。
 */
object AppLocale {

    const val SYSTEM = "system"
    const val ZH = "zh"
    const val EN = "en"

    /** 界面里可选的三种取值，顺序即显示顺序 */
    val choices = listOf(SYSTEM, ZH, EN)

    private const val PREFS = "subblock_locale"
    private const val KEY = "language"

    fun read(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, SYSTEM)
            ?.takeIf { it in choices }
            ?: SYSTEM

    fun write(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, value)
            .apply()
    }

    /** 把系统语言标签翻译成语言自己的名字（永远显示自己的语言，用户才找得到） */
    fun labelOf(context: Context, value: String): String = when (value) {
        ZH -> context.getString(R.string.language_zh)
        EN -> context.getString(R.string.language_en)
        else -> context.getString(R.string.language_system)
    }

    /**
     * 按用户选择包装 Context。
     * 选"跟随系统"时原样返回——让系统自己决定用哪套资源。
     */
    fun wrap(context: Context): Context {
        val tag = read(context)
        if (tag == SYSTEM) return context

        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
