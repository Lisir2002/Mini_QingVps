package com.vps.qing.feature.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vps.qing.core.network.UpdateChecker
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassLoadingBar
import com.vps.qing.core.ui.GlassSurface
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.ScrollGlassEffect
import com.vps.qing.core.ui.SectionTitle
import com.vps.qing.core.ui.Strings
import com.vps.qing.core.ui.UiT
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.core.update.ApkDownloader
import com.vps.qing.core.update.ApkInstaller
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 独立「关于」页面：
 *  - 官方网站：App 内 WebView 打开；官方群聊 / 开发团队链接 / 软件更新：系统浏览器或对应应用；
 *  - APP 开发下方显示「MiniMe-Core开发团队」及官方链接（v1.4.19）；
 *  - 软件更新：v1.4.19 起支持 **App 内真实下载**，进度来自真实字节数，
 *    下载完成后更换为「是否立即安装」确认弹窗，通过 FileProvider 拉起系统安装器。
 */
private data class AboutEntry(
    val title: String,
    val value: String,
    val icon: ImageVector,
    val tint: Color,
    val soft: Color,
    val onClick: () -> Unit,
    /** v1.4.19：可选的第三行链接文字（如开发团队链接），点击整行即打开。 */
    val link: String = ""
)

/** 用系统浏览器 / 对应 App 打开外部链接（QQ 群、GitHub 下载等）。 */
private fun openExternal(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * 软件更新弹窗的阶段状态机（v1.4.19）。
 * 用 StateFlow 承载，下载回调在 IO 线程写值、UI 在主线程 collect 重组，线程安全。
 */
private sealed interface UpdatePhase {
    data object Checking : UpdatePhase
    data object CheckFailed : UpdatePhase
    data class Ready(val release: UpdateChecker.ReleaseInfo, val newer: Boolean) : UpdatePhase
    data class Downloading(val release: UpdateChecker.ReleaseInfo, val progress: ApkDownloader.Progress) : UpdatePhase
    data class DownloadFailed(val release: UpdateChecker.ReleaseInfo, val message: String) : UpdatePhase
    /** 下载完成：切换为独立的「是否立即安装」确认弹窗。 */
    data class Downloaded(val release: UpdateChecker.ReleaseInfo, val apk: File) : UpdatePhase
}

@Composable
fun AboutPage(
    onBack: () -> Unit,
    /** 官网在 App 内 WebView 打开（带登录态）。 */
    onOpenWeb: (String) -> Unit = {}
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var showUpdate by remember { mutableStateOf(false) }
    val phaseFlow = remember { MutableStateFlow<UpdatePhase>(UpdatePhase.Checking) }
    val phase by phaseFlow.collectAsState()
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    fun doCheck() {
        downloadJob?.cancel()
        downloadJob = null
        phaseFlow.value = UpdatePhase.Checking
        scope.launch {
            val r = UpdateChecker.checkLatest()
            phaseFlow.value = if (r == null) UpdatePhase.CheckFailed
            else UpdatePhase.Ready(r, UpdateChecker.isNewerVersion(r.tag, Strings.APP_VERSION))
        }
    }

    fun startDownload(r: UpdateChecker.ReleaseInfo) {
        val url = r.apkUrl ?: return
        val dest = ApkInstaller.apkFile(ctx, r.tag)
        downloadJob?.cancel()
        phaseFlow.value = UpdatePhase.Downloading(r, ApkDownloader.Progress(0, r.apkSize))
        downloadJob = scope.launch {
            val result = runCatching {
                ApkDownloader.download(url, dest, r.apkSize) { p ->
                    // 回调在 IO 线程；StateFlow.value 线程安全，UI 侧 collect 在主线程重组
                    phaseFlow.value = UpdatePhase.Downloading(r, p)
                }
            }
            result.onSuccess { file ->
                phaseFlow.value = UpdatePhase.Downloaded(r, file)
            }.onFailure { e ->
                // 主动取消会走到 cancelDownload 覆盖为 Ready；这里兜底展示可重试错误
                phaseFlow.value = UpdatePhase.DownloadFailed(
                    r, e.message?.takeIf { it.isNotBlank() } ?: "下载失败，请检查网络后重试"
                )
            }
            downloadJob = null
        }
    }

    fun cancelDownload(r: UpdateChecker.ReleaseInfo) {
        downloadJob?.cancel()
        downloadJob = null
        phaseFlow.value = UpdatePhase.Ready(r, true)
    }

    fun dismiss() {
        downloadJob?.cancel()
        downloadJob = null
        showUpdate = false
    }

    val entries = listOf(
        AboutEntry(
            Strings.ABOUT_SITE, Strings.ABOUT_SITE_VALUE,
            AppIcons.Globe, CartoonColors.Mint, CartoonColors.MintSoft,
            onClick = { onOpenWeb(Strings.ABOUT_SITE_URL) }
        ),
        AboutEntry(
            Strings.ABOUT_GROUP, Strings.ABOUT_GROUP_VALUE,
            AppIcons.Message, CartoonColors.Sky, CartoonColors.SkySoft,
            onClick = { openExternal(ctx, Strings.ABOUT_GROUP_URL) }
        ),
        // v1.4.19：APP 开发下方显示开发团队署名与官方链接，点击打开
        AboutEntry(
            Strings.ABOUT_DEV, Strings.ABOUT_DEV_TEAM,
            AppIcons.Sparkle, CartoonColors.Lemon, CartoonColors.LemonSoft,
            onClick = { openExternal(ctx, Strings.ABOUT_DEV_TEAM_URL) },
            link = Strings.ABOUT_DEV_TEAM_URL
        ),
        AboutEntry(
            Strings.ABOUT_UPDATE, Strings.ABOUT_UPDATE_VALUE,
            AppIcons.Update, CartoonColors.Brand, CartoonColors.BrandSoft,
            onClick = {
                showUpdate = true
                doCheck()
            }
        )
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
                    "官网在 App 内打开；群聊、开发团队与软件更新通过系统浏览器 / 对应应用打开。",
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.InkSub,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    // v1.4.19：下载完成后「更换弹窗」为独立的安装确认，其余阶段用更新弹窗
    if (showUpdate) {
        val p = phase
        if (p is UpdatePhase.Downloaded) {
            InstallConfirmDialog(
                release = p.release,
                apk = p.apk,
                onInstall = {
                    if (ApkInstaller.needsInstallPermission(ctx)) {
                        ApkInstaller.openInstallPermissionSettings(ctx)
                    } else {
                        ApkInstaller.install(ctx, p.apk)
                    }
                },
                onLater = { dismiss() }
            )
        } else {
            UpdateDialog(
                phase = p,
                onRetry = { doCheck() },
                onOpenUrl = { url -> openExternal(ctx, url) },
                onDownload = { r -> startDownload(r) },
                onCancelDownload = { r -> cancelDownload(r) },
                onDismiss = { dismiss() }
            )
        }
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
            if (entry.link.isNotBlank()) {
                Text(
                    entry.link,
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.Brand,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            if (entry.link.isNotBlank()) AppIcons.ExternalLink else AppIcons.Forward, null,
            tint = CartoonColors.InkSub,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** 弹窗通用外壳：糖果渐变头部徽章 + 近实色白底内容，背后不透字。 */
@Composable
private fun UpdateDialogShell(
    icon: ImageVector,
    iconTint: Color,
    iconSoft: Color,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
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
                    .padding(horizontal = 22.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 头部：圆形徽章 + 标题/副标题
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(iconSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = iconTint, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = CartoonColors.Ink
                        )
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = CartoonColors.InkSub
                        )
                    }
                }
                content()
            }
        }
    }
}

/**
 * 软件更新弹窗：检查中 / 检查失败 / 发现新版本 / 下载中（真实进度）/ 下载失败。
 * 版本对比、更新日志、包体大小都来自 Release 真实数据。
 */
@Composable
private fun UpdateDialog(
    phase: UpdatePhase,
    onRetry: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDownload: (UpdateChecker.ReleaseInfo) -> Unit,
    onCancelDownload: (UpdateChecker.ReleaseInfo) -> Unit,
    onDismiss: () -> Unit
) {
    UpdateDialogShell(
        icon = AppIcons.Update,
        iconTint = CartoonColors.Brand,
        iconSoft = CartoonColors.BrandSoft,
        title = Strings.ABOUT_UPDATE,
        subtitle = "检查更新 · App 内下载 · 确认安装",
        onDismiss = onDismiss
    ) {
        when (phase) {
            is UpdatePhase.Checking -> {
                GlassLoadingBar(
                    modifier = Modifier.fillMaxWidth(),
                    message = Strings.UPDATE_CHECKING
                )
                DialogActions(onDismiss)
            }
            is UpdatePhase.CheckFailed -> {
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onRetry) { Text(Strings.UPDATE_RETRY, color = CartoonColors.Brand) }
                    TextButton(onClick = onDismiss) { Text(Strings.CANCEL, color = CartoonColors.InkSub) }
                }
            }
            is UpdatePhase.Ready -> ReadyContent(phase, onDownload, onOpenUrl, onRetry, onDismiss)
            is UpdatePhase.Downloading -> DownloadingContent(phase, onCancelDownload, onDismiss)
            is UpdatePhase.DownloadFailed -> {
                Text(
                    "${Strings.UPDATE_DOWNLOAD_FAILED}：${phase.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CartoonColors.Coral
                )
                CartoonButton(
                    text = Strings.UPDATE_DOWNLOAD_RETRY,
                    onClick = { onDownload(phase.release) },
                    icon = AppIcons.Refresh,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onOpenUrl(phase.release.htmlUrl) }) {
                        Text(Strings.UPDATE_OPEN_RELEASES, color = CartoonColors.InkSub)
                    }
                    TextButton(onClick = onDismiss) { Text(Strings.CANCEL, color = CartoonColors.InkSub) }
                }
            }
            is UpdatePhase.Downloaded -> { /* 由外层渲染独立安装确认弹窗 */ }
        }
    }
}

/** 发现新版本：版本对比卡 + 更新日志 + 下载入口。 */
@Composable
private fun ReadyContent(
    phase: UpdatePhase.Ready,
    onDownload: (UpdateChecker.ReleaseInfo) -> Unit,
    onOpenUrl: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val r = phase.release
    // 版本对比卡
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CartoonKit.ItemShape)
            .background(CartoonColors.BrandSoft.copy(alpha = 0.5f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VersionChip("当前 v${Strings.APP_VERSION}", CartoonColors.InkSub)
            Spacer(Modifier.width(8.dp))
            Icon(AppIcons.Forward, null, tint = CartoonColors.Brand, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            VersionChip(r.tag, if (phase.newer) CartoonColors.Coral else CartoonColors.Mint)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (phase.newer) AppIcons.Sparkle else AppIcons.Check,
                null,
                tint = if (phase.newer) CartoonColors.Coral else CartoonColors.Mint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (phase.newer) Strings.UPDATE_FOUND else Strings.UPDATE_LATEST,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (phase.newer) CartoonColors.Coral else CartoonColors.Mint
            )
            Spacer(Modifier.weight(1f))
            if (r.apkSize > 0) {
                Text(
                    ApkDownloader.formatSize(r.apkSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.InkSub
                )
            }
        }
    }

    // 更新日志：v1.4.19 起 Release 写规范版本日志，完整可滚动展示，不再粗暴截断
    if (r.body.isNotBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                Strings.UPDATE_CHANGELOG,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = CartoonColors.Ink
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 150.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CartoonColors.Line.copy(alpha = 0.18f))
                    .padding(12.dp)
            ) {
                Text(
                    r.body.trim(),
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.InkSub,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                )
            }
        }
    }

    // 主操作：有新版本且有 APK 资产 → App 内下载；否则用浏览器打开
    if (phase.newer && r.apkUrl != null) {
        CartoonButton(
            text = Strings.UPDATE_DOWNLOAD,
            onClick = { onDownload(r) },
            icon = AppIcons.Update,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        CartoonButton(
            text = if (phase.newer) Strings.UPDATE_GOTO else "查看 Releases",
            onClick = { onOpenUrl(r.apkUrl ?: r.htmlUrl) },
            icon = AppIcons.ExternalLink,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onRetry) { Text(Strings.UPDATE_RETRY, color = CartoonColors.InkSub) }
        TextButton(onClick = onDismiss) { Text(Strings.CANCEL, color = CartoonColors.InkSub) }
    }
}

@Composable
private fun VersionChip(text: String, color: Color) {
    Box(
        Modifier
            .clip(CartoonKit.PillShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

/** 下载中：真实字节进度（自绘圆角进度条，避免假动画）+ 百分比 + 已下载/总量。 */
@Composable
private fun DownloadingContent(
    phase: UpdatePhase.Downloading,
    onCancelDownload: (UpdateChecker.ReleaseInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val p = phase.progress
    val animated by animateFloatAsState(targetValue = p.fraction, label = "downloadProgress")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = CartoonColors.Brand
            )
            Spacer(Modifier.width(8.dp))
            Text(
                Strings.UPDATE_DOWNLOADING,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = CartoonColors.Ink
            )
        }
        // 真实进度：fraction 完全来自已写入字节 / 总字节
        if (p.indeterminate) {
            Text(
                "已下载 ${ApkDownloader.formatSize(p.bytesRead)}（总量未知）",
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CartoonKit.PillShape)
                    .background(CartoonColors.BrandSoft)
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${ApkDownloader.formatSize(p.bytesRead)} / ${ApkDownloader.formatSize(p.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.InkSub
                )
                Text(
                    "${p.percent}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = CartoonColors.Brand
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CartoonKit.PillShape)
                    .background(CartoonColors.BrandSoft)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animated.coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(CartoonKit.PillShape)
                        .background(CartoonColors.Brand)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onCancelDownload(phase.release) }) {
                Text(Strings.UPDATE_CANCEL_DOWNLOAD, color = CartoonColors.InkSub)
            }
            TextButton(onClick = onDismiss) { Text(Strings.CANCEL, color = CartoonColors.InkSub) }
        }
    }
}

@Composable
private fun DialogActions(onDismiss: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text(Strings.CANCEL, color = CartoonColors.InkSub) }
    }
}

/** 下载成功后的独立确认弹窗：换一套成功视觉，明确询问「是否直接安装」。 */
@Composable
private fun InstallConfirmDialog(
    release: UpdateChecker.ReleaseInfo,
    apk: File,
    onInstall: () -> Unit,
    onLater: () -> Unit
) {
    val ctx = LocalContext.current
    val needPermission = ApkInstaller.needsInstallPermission(ctx)
    UpdateDialogShell(
        icon = AppIcons.Check,
        iconTint = CartoonColors.Mint,
        iconSoft = CartoonColors.MintSoft,
        title = Strings.UPDATE_DOWNLOADED,
        subtitle = "${release.tag} · ${ApkDownloader.formatSize(apk.length())}",
        onDismiss = onLater
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(CartoonKit.ItemShape)
                .background(CartoonColors.MintSoft.copy(alpha = 0.6f))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                Strings.UPDATE_INSTALL_ASK,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = CartoonColors.Ink
            )
            Text(
                "安装包已保存到应用私有目录：${apk.name}",
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub
            )
            if (needPermission) {
                Spacer(Modifier.height(2.dp))
                Text(
                    Strings.UPDATE_INSTALL_PERMISSION,
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.Coral
                )
            }
        }
        CartoonButton(
            text = Strings.UPDATE_INSTALL_NOW,
            onClick = onInstall,
            icon = AppIcons.Check,
            containerColor = CartoonColors.Mint,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
            Text(Strings.UPDATE_INSTALL_LATER, color = CartoonColors.InkSub)
        }
    }
}
