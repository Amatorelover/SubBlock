package com.yanhu.subblock.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive 形状：比基础 MD3 更大、更连续的圆角，
 * 让卡片与控件呈现"雕塑感"的柔和外观 —— 这是 Expressive 最直观的视觉特征。
 *
 * 原项目多处卡片写死 RoundedCornerShape(20.dp)，会绕过主题 shape；
 * 这里提供统一档位，并把主要卡片改回引用 MaterialTheme.shapes，
 * 让整站圆角风格可被一处控制。
 */
val ExpressiveShapes = Shapes(
    small = RoundedCornerShape(14.dp),    // chip / 小控件
    medium = RoundedCornerShape(20.dp),   // 常规卡片
    large = RoundedCornerShape(28.dp),    // 主开关卡 / 大面板
    extraLarge = RoundedCornerShape(34.dp) // 底部弹层 / 对话框
)
