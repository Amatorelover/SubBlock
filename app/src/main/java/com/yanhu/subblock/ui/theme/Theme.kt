package com.yanhu.subblock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 「遮幕」主题 —— Material 3 Expressive 观感。
 *
 * 设计取舍：
 * - Expressive 的官方 MaterialExpressiveTheme API 要到 material3 1.5.0 才转正，
 *   而 1.5.0 会强制把 Gradle/AGP/compileSdk 整条链升级。本项目是零权限自用工具，
 *   不值得为"调用一个 API"承担整条构建链的风险。
 * - 因此这里用稳定的 MaterialTheme 承载 Expressive 的三大视觉内核：
 *     ① 雕塑感圆角（ExpressiveShapes）
 *     ② 更有表现力的字重层次（ExpressiveTypography）
 *     ③ Material You 壁纸动态取色（下方 dynamic*ColorScheme）
 *   spring 回弹动效则在具体交互处用 animate*AsState + spring 实现。
 * 结果：观感贴近原生 Expressive，但构建链一行未动。
 */
@Composable
fun SubBlockTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current

    // Material You 的灵魂：Android 12+ 从壁纸提取主色调，让 App 与系统融为一体。
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = ExpressiveTypography,
        shapes = ExpressiveShapes,
        content = content
    )
}
