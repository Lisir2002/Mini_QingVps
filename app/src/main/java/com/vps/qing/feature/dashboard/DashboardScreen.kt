package com.vps.qing.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vps.qing.core.AppContainer
import com.vps.qing.core.simpleViewModelFactory
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.core.ui.CartoonIconChip
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.SectionTitle
import com.vps.qing.core.ui.Strings
import com.vps.qing.data.repository.HostSummary
import com.vps.qing.data.repository.NewsItem

private data class QuickEntry(val title: String, val path: String, val icon: ImageVector, val accent: Color, val soft: Color)

private val QUICK_ENTRIES = listOf(
    QuickEntry(Strings.MY_ORDERS, "clientarea/order.htm", AppIcons.Order, CartoonColors.Brand, CartoonColors.BrandSoft),
    QuickEntry(Strings.MY_HOSTS, "clientarea/host.htm", AppIcons.Server, CartoonColors.Mint, CartoonColors.MintSoft),
    QuickEntry(Strings.FINANCE, "clientarea/finance.htm", AppIcons.Wallet, CartoonColors.Lemon, CartoonColors.LemonSoft),
    QuickEntry(Strings.TICKETS, "plugin/27/ticket.htm", AppIcons.Ticket, CartoonColors.Sky, CartoonColors.SkySoft),
    QuickEntry(Strings.CERTIFICATION, "plugin/22/certification.htm", AppIcons.Shield, CartoonColors.Peach, CartoonColors.PeachSoft),
    QuickEntry(Strings.SECURITY, "clientarea/security.htm", AppIcons.Lock, CartoonColors.Lilac, CartoonColors.LilacSoft)
)

/**
 * 首页仪表盘（纯白卡通重构）：欢迎卡 + 概览数字 + 已购服务 + 公告 + 快捷入口。
 * 公告 / 菜单匿名可用；主机与账号需登录态，未登录时降级为提示。
 */
@Composable
fun DashboardScreen(onOpenWeb: (String) -> Unit) {
    val vm: DashboardViewModel = viewModel(
        factory = simpleViewModelFactory {
            DashboardViewModel(AppContainer.catalogRepository, AppContainer.profileRepository)
        }
    )
    val state by vm.ui.collectAsState()

    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = Strings.DASHBOARD_TITLE,
                subtitle = Strings.WELCOME_SUB,
                actions = {
                    CartoonIconChip(
                        icon = AppIcons.Refresh,
                        onClick = { vm.load(force = true) },
                        tint = CartoonColors.Mint,
                        bg = CartoonColors.MintSoft
                    )
                }
            )

            if (state.loading) LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                color = CartoonColors.Brand
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { WelcomeCard(state) }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(
                            Strings.SERVICE_COUNT,
                            if (state.loggedIn) state.hosts.size.toString() else Strings.UNKNOWN,
                            AppIcons.Server, CartoonColors.Mint, CartoonColors.MintSoft,
                            Modifier.weight(1f)
                        )
                        StatCard(
                            Strings.NEWS_COUNT, state.news.size.toString(),
                            AppIcons.Bell, CartoonColors.Sky, CartoonColors.SkySoft,
                            Modifier.weight(1f)
                        )
                        StatCard(
                            Strings.BALANCE, state.account?.balance ?: Strings.UNKNOWN,
                            AppIcons.Wallet, CartoonColors.Lemon, CartoonColors.LemonSoft,
                            Modifier.weight(1f)
                        )
                    }
                }

                item { SectionTitle(Strings.MY_SERVICES, CartoonColors.Mint) }
                if (state.hosts.isEmpty()) {
                    item { HintCard(if (state.loggedIn) Strings.NO_SERVICE else Strings.NOT_LOGGED_TIP) }
                } else {
                    items(state.hosts.take(5)) { host -> HostCard(host) }
                }

                item { SectionTitle(Strings.ANNOUNCEMENTS, CartoonColors.Peach) }
                if (state.news.isEmpty()) {
                    item { HintCard(Strings.NO_NEWS) }
                } else {
                    items(state.news) { news -> NewsCard(news) }
                }

                item { SectionTitle(Strings.QUICK_ENTRIES, CartoonColors.Brand) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        QUICK_ENTRIES.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                row.forEach { entry ->
                                    QuickEntryCard(
                                        entry = entry,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onOpenWeb(AppContainer.webUrl(entry.path)) }
                                    )
                                }
                                if (row.size == 1) Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Info, null, tint = CartoonColors.InkSub, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            Strings.WEB_FALLBACK_TIP,
                            style = MaterialTheme.typography.labelSmall,
                            color = CartoonColors.InkSub
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeCard(state: DashboardUiState) {
    GlassCard(modifier = Modifier.fillMaxWidth(), alpha = 0.82f) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(CartoonKit.CardShape)
                    .background(CartoonColors.BrandSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Sparkle, null, tint = CartoonColors.Brand, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(Strings.WELCOME, style = MaterialTheme.typography.labelMedium, color = CartoonColors.InkSub)
                Text(
                    state.account?.username ?: state.account?.email ?: Strings.APP_NAME,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = CartoonColors.Ink
                )
                state.notice?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String, value: String,
    icon: ImageVector, accent: Color, soft: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier, alpha = 0.82f) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CartoonKit.CardShape)
                    .background(soft),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = CartoonColors.Ink,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HintCard(text: String) {
    GlassCard(modifier = Modifier.fillMaxWidth(), alpha = 0.7f) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = CartoonColors.InkSub,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun HostCard(host: HostSummary) {
    GlassCard(modifier = Modifier.fillMaxWidth(), alpha = 0.8f) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CartoonKit.CardShape)
                    .background(CartoonColors.MintSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Server, null, tint = CartoonColors.Mint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    host.productName ?: host.name ?: Strings.UNKNOWN,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = CartoonColors.Ink
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    host.status?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = CartoonColors.Mint)
                    }
                    host.dueTime?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsCard(news: NewsItem) {
    GlassCard(modifier = Modifier.fillMaxWidth(), alpha = 0.8f) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CartoonKit.CardShape)
                    .background(CartoonColors.PeachSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Bell, null, tint = CartoonColors.Peach, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(news.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
                if (news.type.isNotBlank()) {
                    Text(news.type, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                }
            }
        }
    }
}

@Composable
private fun QuickEntryCard(entry: QuickEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassCard(modifier = modifier, alpha = 0.8f) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CartoonKit.CardShape)
                .clickable { onClick() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CartoonKit.CardShape)
                    .background(entry.soft),
                contentAlignment = Alignment.Center
            ) {
                Icon(entry.icon, null, tint = entry.accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = CartoonColors.Ink,
                maxLines = 1
            )
        }
    }
}
