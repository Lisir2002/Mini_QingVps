package com.vps.qing.feature.profile

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.ScrollGlassEffect
import com.vps.qing.core.ui.SectionTitle
import com.vps.qing.core.ui.Strings
import com.vps.qing.core.ui.UiT
import com.vps.qing.core.ui.theme.CartoonColors

/**
 * 独立「关于」页面（v1.4.12）：取代原先的简单弹窗。
 * 官方网站 / 官方站长 / 官方管理 / 官方群聊 / APP开发 五项先留占位，
 * 后续由用户逐项告知内容后再填充（当前点击仅给出友好提示，不假装有数据）。
 */
private data class AboutEntry(
    val title: String,
    val icon: ImageVector,
    val tint: Color,
    val soft: Color
)

@Composable
fun AboutPage(onBack: () -> Unit) {
    val entries = listOf(
        AboutEntry(Strings.ABOUT_SITE, AppIcons.Globe, CartoonColors.Mint, CartoonColors.MintSoft),
        AboutEntry(Strings.ABOUT_OWNER, AppIcons.Profile, CartoonColors.Brand, CartoonColors.BrandSoft),
        AboutEntry(Strings.ABOUT_ADMIN, AppIcons.Shield, CartoonColors.Lilac, CartoonColors.LilacSoft),
        AboutEntry(Strings.ABOUT_GROUP, AppIcons.Message, CartoonColors.Sky, CartoonColors.SkySoft),
        AboutEntry(Strings.ABOUT_DEV, AppIcons.Sparkle, CartoonColors.Lemon, CartoonColors.LemonSoft)
    )
    val ctx = LocalContext.current

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
                            AboutRow(entry) {
                                android.widget.Toast.makeText(
                                    ctx, Strings.ABOUT_PLACEHOLDER, android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
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
                    Strings.ABOUT_PLACEHOLDER_HINT,
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.InkSub,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AboutRow(entry: AboutEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CartoonKit.ItemShape)
            .clickable { onClick() }
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
                Strings.ABOUT_PLACEHOLDER,
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub
            )
        }
        Icon(
            AppIcons.Forward, null,
            tint = CartoonColors.InkSub,
            modifier = Modifier.size(16.dp)
        )
    }
}
