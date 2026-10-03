package com.yanhu.subblock

import android.content.Context
import android.content.SharedPreferences
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

    /** 存放语言偏好的 SharedPreferences 名与键。公开出来，是为了让服务能监听它的变化。 */
    private const val PREFS = "subblock_locale"
    const val KEY_LANGUAGE = "language"

    fun read(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, SYSTEM)
            ?.takeIf { it in choices }
            ?: SYSTEM

    fun write(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, value)
            .apply()
    }

    /**
     * 监听"应用语言"的变化。
     *
     * 为什么需要它？悬浮窗服务是个前台服务，用户切语言时它**不会被重建**
     * （重建的只有 Activity），如果没人告诉它"语言变了"，通知栏文案就会一直
     * 停在旧语言：界面全英文、通知栏还是中文，看着像两个 App。
     *
     * 相比"在 Activity 里改完语言主动去通知服务"，用偏好监听更干净：
     * 服务自己关心自己该关心的事，不必让界面层替它记着。
     *
     * 返回的监听器**必须由调用方持有强引用**（否则会被 GC 回收、静默失效），
     * 并在不再需要时用 [unregisterLanguageObserver] 注销。
     */
    fun registerLanguageObserver(
        context: Context,
        onChange: () -> Unit
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LANGUAGE) onChange()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun unregisterLanguageObserver(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(listener)
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

        // 这里**刻意不**调用 Locale.setDefault(locale)。
        //
        // setDefault 改的是**整个进程**的默认 Locale，波及所有隐式依赖它的地方：
        // String.format、toUpperCase()、无参 SimpleDateFormat……
        // 而我们要影响的只是"这个 Context 取文案"。
        // 一个名叫 wrap（包装）的函数顺手改了全局状态，是最阴的那种陷阱——
        // 将来某处出现"土耳其 i 问题"（"i".uppercase() 变成 İ）时，
        // 没人会想到凶手指向这里。
        //
        // 把语言注入 Context（下一行）就已经足够：本应用所有 getString
        // 都走这个 Context，压根不需要动全局默认值。
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
