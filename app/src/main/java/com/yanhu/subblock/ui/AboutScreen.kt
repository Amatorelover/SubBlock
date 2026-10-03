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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.yanhu.subblock.BuildConfig
import com.yanhu.subblock.R

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
    var qrDialogRes: Int? by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title), fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
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
                        stringResource(R.string.app_name) + " · SubBlock",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.about_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    Spacer(Modifier.height(14.dp))
                    InfoRow(
                        stringResource(R.string.about_label_version),
                        "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                    )
                    InfoRow(stringResource(R.string.about_label_author), BuildConfig.AUTHOR_NAME)
                    InfoRow(stringResource(R.string.about_label_license), "MIT License")
                    InfoRow(
                        stringResource(R.string.about_label_android),
                        "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                    )
                }
            }

            // ---------------- 作者声明 ----------------
            SectionCard(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.about_section_author)
            ) {
                BodyText(stringResource(R.string.about_author_p1))
                Spacer(Modifier.height(10.dp))
                BodyText(stringResource(R.string.about_author_p2))
                Spacer(Modifier.height(10.dp))
                BodyText(stringResource(R.string.about_author_p3))
            }

            // ---------------- 隐私承诺 ----------------
            SectionCard(
                icon = Icons.Filled.Lock,
                title = stringResource(R.string.about_section_privacy)
            ) {
                Bullet(stringResource(R.string.about_privacy_1))
                Bullet(stringResource(R.string.about_privacy_2))
                Bullet(stringResource(R.string.about_privacy_3))
            }

            // ---------------- 开源 ----------------
            SectionCard(
                icon = Icons.Filled.Code,
                title = stringResource(R.string.about_section_opensource)
            ) {
                BodyText(stringResource(R.string.about_opensource_body))
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
                        Text(stringResource(R.string.about_view_source))
                    }
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(
                                AnnotatedString(
                                    context.getString(
                                        R.string.about_version_line,
                                        BuildConfig.VERSION_NAME,
                                        BuildConfig.VERSION_CODE,
                                        Build.VERSION.RELEASE,
                                        Build.VERSION.SDK_INT
                                    )
                                )
                            )
                        }
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.about_copy_version))
                    }
                }
            }

            // ---------------- 致谢 ----------------
            SectionCard(
                icon = Icons.Filled.Favorite,
                title = stringResource(R.string.about_section_thanks)
            ) {
                BodyText(stringResource(R.string.about_thanks_body))
            }

            // ---------------- 支持作者 ----------------
            SectionCard(
                icon = Icons.Filled.Favorite,
                title = stringResource(R.string.about_section_sponsor)
            ) {
                BodyText(stringResource(R.string.about_sponsor_body))
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(context.getString(R.string.about_sponsor_url_afdian))
                            )
                            runCatching { context.startActivity(intent) }
                        }
                    ) {
                        Text(stringResource(R.string.about_sponsor_afdian))
                    }
                    OutlinedButton(onClick = { qrDialogRes = R.drawable.donate_wechat }) {
                        Text(stringResource(R.string.about_sponsor_wechat))
                    }
                    OutlinedButton(onClick = { qrDialogRes = R.drawable.donate_alipay }) {
                        Text(stringResource(R.string.about_sponsor_alipay))
                    }
                }
            }
        }
    }

    // ---------------- 收款二维码弹窗 ----------------
    if (qrDialogRes != null) {
        val resId: Int = qrDialogRes!!
        AlertDialog(
            onDismissRequest = { qrDialogRes = null },
            confirmButton = {},
            title = {
                Text(
                    stringResource(
                        if (resId == R.drawable.donate_wechat) R.string.about_qr_title_wechat
                        else R.string.about_qr_title_alipay
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(resId),
                        contentDescription = null,
                        modifier = Modifier.size(220.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.about_qr_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
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
