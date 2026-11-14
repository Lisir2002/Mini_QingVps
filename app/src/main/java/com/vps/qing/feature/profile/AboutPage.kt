package com.vps.qing.feature.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vps.qing.core.network.UpdateChecker
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassSurface
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.ScrollGlassEffect
import com.vps.qing.core.ui.SectionTitle
import com.vps.qing.core.ui.Strings
import com.vps.qing.core.ui.UiT
import com.vps.qing.core.ui.theme.CartoonColors
import kotlinx.coroutines.launch

/**
 * 独立「关于」页面（v1.4.14）：
 *  - 官方网站：填对接站点 vps.qingyayun.cn，点击在 App 内 WebView 打开；
 *  - 官方群聊：对接官方 QQ 群 universal-share 链接，点击用浏览器/QQ 打开；
 *  - 软件更新：对接 GitHub Releases，点击检查最新版本并展示更新说明与下载入口；
 *  - 移除「官方站长 / 官方管理」两项（v1.4.14 用户要求）。
 */
private data class AboutEntry(
    val title: String,
    val value: String,
    val icon: ImageVector,
    val tint: Color,
    val soft: Color,
    val onClick: () -> Unit
)

/** 用系统浏览器 / 对应 App 打开外部链接（QQ 群、GitHub 下载等）。 */
private fun openExternal(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
fun AboutPage(
    onBack: () -> Unit,
    /** 官网在 App 内 WebView 打开（带登录态）。 */
    onOpenWeb: (String) -> Unit = {}
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // 软件更新检查状态
    var showUpdate by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<UpdateChecker.ReleaseInfo?>(null) }

    fun doCheck() {
        checking = true
        checked = false
        release = null
        scope.launch {
            release = UpdateChecker.checkLatest()
            checking = false
            checked = true
        }
    }

    val entries = listOf(
        AboutEntry(
            Strings.ABOUT_SITE, Strings.ABOUT_SITE_VALUE,
            AppIcons.Globe, CartoonColors.Mint, CartoonColors.MintSoft
        ) { onOpenWeb(Strings.ABOUT_SITE_URL) },
        AboutEntry(
            Strings.ABOUT_GROUP, Strings.ABOUT_GROUP_VALUE,
            AppIcons.Message, CartoonColors.Sky, CartoonColors.SkySoft
        ) { openExternal(ctx, Strings.ABOUT_GROUP_URL) },
        AboutEntry(
            "APP开发", "QingVPS 团队",
            AppIcons.Sparkle, CartoonColors.Lemon, CartoonColors.LemonSoft
        ) { },
        AboutEntry(
            Strings.ABOUT_UPDATE, Strings.ABOUT_UPDATE_VALUE,
            AppIcons.Update, CartoonColors.Brand, CartoonColors.BrandSoft
        ) {
            showUpdate = true
            if (!checking && !checked) doCheck()
        }
    )

    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = Strings.ABOUT,
                subtitle = Strings.ABOUT_SUBTITLE,
                navIcon = AppIcons.Back,
                onNavClick = onBack
            )
            val scroll = rememberScrollState()
            ScrollGlassEffect(scroll)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = UiT.pagePad)
                    .padding(top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 应用信息头卡
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(58.dp)
                                .clip(CartoonKit.CardShape)
                                .background(CartoonColors.BrandSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                AppIcons.Sparkle, null,
                                tint = CartoonColors.Brand,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                Strings.APP_NAME,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = CartoonColors.Ink
                            )
                            Text(
                                "版本 ${Strings.APP_VERSION}",
                                style = MaterialTheme.typography.bodySmall,
                                color = CartoonColors.InkSub
                            )
                            Text(
                                "vps.qingyayun.cn · IDCSmart 10.7.2",
                                style = MaterialTheme.typography.labelSmall,
                                color = CartoonColors.InkSub
                            )
                        }
                    }
                }

                SectionTitle(Strings.ABOUT_INFO_GROUP, CartoonColors.Lilac)
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        entries.forEachIndexed { index, entry ->
                            AboutRow(entry)
                            if (index != entries.lastIndex) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .height(1.dp)
                                        .background(CartoonColors.Line.copy(alpha = 0.6f))
                                )
                            }
                        }
                    }
                }
                Text(
                    "官网在 App 内打开，群聊与软件更新通过系统浏览器 / 对应应用打开。",
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.InkSub,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    if (showUpdate) {
        UpdateDialog(
            checking = checking,
            checked = checked,
            release = release,
            onRetry = { doCheck() },
            onOpenUrl = { url -> openExternal(ctx, url) },
            onDismiss = { showUpdate = false }
        )
    }
}

@Composable
private fun AboutRow(entry: AboutEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CartoonKit.ItemShape)
            .clickable { entry.onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CartoonKit.PillShape)
                .background(entry.soft),
            contentAlignment = Alignment.Center
        ) {
            Icon(entry.icon, null, tint = entry.tint, modifier = Modifier.size(UiT.chipIcon))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = CartoonColors.Ink
            )
            Text(
                entry.value,
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub
            )
        }
        Icon(
            AppIcons.ExternalLink, null,
            tint = CartoonColors.InkSub,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** 软件更新结果对话框：检查中 / 已是最新 / 发现新版本 / 检查失败。 */
@Composable
private fun UpdateDialog(
    checking: Boolean,
    checked: Boolean,
    release: UpdateChecker.ReleaseInfo?,
    onRetry: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = CartoonKit.SheetShape,
            alpha = 1f,
            elevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CartoonColors.White.copy(alpha = 0.97f))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Update, null, tint = CartoonColors.Brand, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        Strings.ABOUT_UPDATE,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = CartoonColors.Ink
                    )
                }

                when {
                    checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = CartoonColors.Brand,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                Strings.UPDATE_CHECKING,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CartoonColors.InkSub
                            )
                        }
                        LinearProgressIndicator(
                            color = CartoonColors.Brand,
                            modifier = Modifier.fillMaxWidth().height(3.dp)
                        )
                    }
                    release == null -> {
                        // 检查完成但拿不到数据（GitHub 被墙 / API 限流 / 解析失败）
                        Text(
                            Strings.UPDATE_FAIL,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CartoonColors.Ink
                        )
                        CartoonButton(
                            text = "打开 GitHub Releases",
                            onClick = { onOpenUrl(UpdateChecker.RELEASES_URL) },
                            icon = AppIcons.ExternalLink,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(onClick = onRetry) {
                            Text(Strings.UPDATE_RETRY, color = CartoonColors.Brand)
                        }
                    }
                    else -> {
                        val newer = UpdateChecker.isNewerVersion(release.tag, Strings.APP_VERSION)
                        // 版本对比卡
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(CartoonKit.ItemShape)
                                .background(CartoonColors.BrandSoft.copy(alpha = 0.5f))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            KeyValue(Strings.UPDATE_CURRENT, "v${Strings.APP_VERSION}")
                            KeyValue("最新版本", release.tag)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (newer) AppIcons.Sparkle else AppIcons.Check,
                                    null,
                                    tint = if (newer) CartoonColors.Coral else CartoonColors.Mint,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (newer) Strings.UPDATE_FOUND else Strings.UPDATE_LATEST,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (newer) CartoonColors.Coral else CartoonColors.Mint
                                )
                            }
                        }
                        if (release.body.isNotBlank()) {
                            Text(
                                release.body.trim().lineSequence().take(8).joinToString("\n")
                                    .let { if (it.length > 400) it.take(400) + "…" else it },
                                style = MaterialTheme.typography.labelSmall,
                                color = CartoonColors.InkSub
                            )
                        }
                        CartoonButton(
                            text = if (newer) Strings.UPDATE_GOTO else "查看 Releases",
                            onClick = { onOpenUrl(release.apkUrl ?: release.htmlUrl) },
                            icon = AppIcons.ExternalLink,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(onClick = onRetry) {
                            Text(Strings.UPDATE_RETRY, color = CartoonColors.InkSub)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(Strings.CANCEL, color = CartoonColors.InkSub)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = CartoonColors.InkSub,
            modifier = Modifier.width(72.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = CartoonColors.Ink
        )
    }
}
