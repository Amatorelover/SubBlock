package com.yanhu.subblock.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yanhu.subblock.BuildConfig

/**
 * 「关于」页。
 *
 * 这是开源项目的"身份证"：版本号、作者、开源协议、作者声明、隐私承诺。
 * 注意版本号取自 BuildConfig —— 它在打包时由构建系统自动注入，
 * 所以永远不可能和真实版本对不上（写死的版本号迟早会过期）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ---------------- 抬头：应用名与版本 ----------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "遮幕 · SubBlock",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "把视频里的硬字幕盖掉",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    Spacer(Modifier.height(14.dp))
                    InfoRow("版本", "${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
                    InfoRow("作者", BuildConfig.AUTHOR_NAME)
                    InfoRow("协议", "MIT License")
                    InfoRow("Android", "${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")
                }
            }

            // ---------------- 作者声明 ----------------
            SectionCard(
                icon = Icons.Filled.Info,
                title = "作者声明"
            ) {
                BodyText(
                    "「遮幕」诞生于一个很朴素的需求：看视频时不想被画面上烧死的字幕挡住画面，\n" +
                        "而能找到的同类工具要么年久失修，要么只允许一块固定区域。于是我自己写了一个。"
                )
                Spacer(Modifier.height(10.dp))
                BodyText(
                    "它只做一件事：在你指定的位置盖一层遮挡。\n" +
                        "它不读取屏幕内容、不做文字识别、不修改也不下载任何视频。"
                )
                Spacer(Modifier.height(10.dp))
                BodyText(
                    "本项目以 MIT 协议开源，仅供个人学习与自用。使用者应自行确保使用方式符合" +
                        "当地法律法规及相关服务的使用条款，因使用本软件产生的后果由使用者自行承担。"
                )
            }

            // ---------------- 隐私承诺 ----------------
            SectionCard(
                icon = Icons.Filled.Lock,
                title = "隐私承诺"
            ) {
                Bullet("不申请联网权限——可自行核对 AndroidManifest.xml，其中没有 INTERNET")
                Bullet("不收集任何数据：没有统计、没有埋点、没有广告、没有账号")
                Bullet("所有配置只保存在你自己的手机上")
            }

            // ---------------- 开源 ----------------
            SectionCard(
                icon = Icons.Filled.Code,
                title = "开源与源码"
            ) {
                BodyText("代码全部公开，欢迎提 Issue 与 Pull Request：")
                Spacer(Modifier.height(8.dp))
                Text(
                    BuildConfig.GITHUB_URL,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.GITHUB_URL))
                            runCatching { context.startActivity(intent) }
                        }
                    ) {
                        Text("查看源码")
                    }
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(
                                AnnotatedString(
                                    "遮幕 v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
                                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                                )
                            )
                        }
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("复制版本信息")
                    }
                }
            }

            // ---------------- 致谢 ----------------
            SectionCard(
                icon = Icons.Filled.Favorite,
                title = "致谢"
            ) {
                BodyText(
                    "本项目站在这些开源项目肩上：Kotlin 语言、Jetpack Compose 界面框架、" +
                        "AndroidX 与 Material Design 组件库。\n" +
                        "也感谢所有提出过建议与问题的人。"
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.width(72.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun BodyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            "·",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
