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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vps.qing.core.AppContainer
import com.vps.qing.core.simpleViewModelFactory
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.accountDisplay
import com.vps.qing.core.ui.CertBadge
import com.vps.qing.core.ui.CertBadgeChip
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonIconChip
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.SectionTitle
import com.vps.qing.core.ui.ScrollGlassEffect
import com.vps.qing.core.ui.Strings
import com.vps.qing.core.ui.UiT
import com.vps.qing.data.repository.HostSummary
import com.vps.qing.data.repository.NewsItem

/**
 * 快捷入口（v1.4.6 原生化）：点击直达 App 内原生页面，不再跳 WebView；
 * navToken 由主容器路由到「我的」Tab 的对应服务面板。
 */
private data class QuickEntry(val title: String, val navToken: String, val icon: ImageVector, val accent: Color, val soft: Color)

private val QUICK_ENTRIES = listOf(
    QuickEntry(Strings.MY_ORDERS, "orders", AppIcons.Order, CartoonColors.Brand, CartoonColors.BrandSoft),
    QuickEntry(Strings.MY_HOSTS, "hosts", AppIcons.Server, CartoonColors.Mint, CartoonColors.MintSoft),
    QuickEntry(Strings.FINANCE, "finance", AppIcons.Wallet, CartoonColors.Lemon, CartoonColors.LemonSoft),
    QuickEntry(Strings.TICKETS, "tickets", AppIcons.Ticket, CartoonColors.Sky, CartoonColors.SkySoft),
    // v1.4.14：实名认证已并入「安全中心」，此格改为直达子账户管理（原生面板）
    QuickEntry(Strings.SUB_ACCOUNTS, "subaccounts", AppIcons.Users, CartoonColors.Peach, CartoonColors.PeachSoft),
    QuickEntry(Strings.SECURITY, "security", AppIcons.Lock, CartoonColors.Lilac, CartoonColors.LilacSoft)
)

/**
 * 首页仪表盘：欢迎卡 + 概览数字 + 已购服务 + 公告 + 快捷入口。
 *
 * v1.4.6 可用性重构：
 *  - 快捷入口原生化直达（订单/主机/财务/工单/实名/安全），不再经 WebView；
 *  - 已购服务卡点击进产品详情，公告卡点击进公告详情页；
 *  - 统计卡可点（服务数→主机面板，余额→财务面板）；
 *  - 未登录给「去登录」引导，无服务给「去选购」引导；
 *  - 「查看全部 / 更多」入口；下拉刷新替代顶栏单一刷新按钮；
 *  - 移除从未展示的 menu 请求，首屏少一条 1.6s 往返。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenWeb: (String) -> Unit,
    /** 原生面板/详情直达：token 语义见 MainScreen.profileNavRequest。 */
    onOpenPanel: (String) -> Unit = {},
    onNeedLogin: () -> Unit = {},
    onGoShop: () -> Unit = {},
    /** 公告独立页面（v1.4.7）。 */
    onOpenAnnouncements: () -> Unit = {},
    /** 公告原生内容页直达（v1.4.10）：传入公告 id，主容器打开公告页并定位到详情。 */
    onOpenAnnouncement: (String) -> Unit = {}
) {
    val vm: DashboardViewModel = viewModel(
        factory = simpleViewModelFactory {
            DashboardViewModel(AppContainer.catalogRepository, AppContainer.profileRepository)
        }
    )
    val state by vm.ui.collectAsState()

    CartoonBackground {
        // v1.4.10 透明画布：列表整幅铺满（顶部从顶栏玻璃卡下穿过，底部沉入底栏玻璃之下），
        // 顶栏悬浮在列表之上，磨砂玻璃实时透出滚动内容。
        Box(modifier = Modifier.fillMaxSize()) {
            // 顶栏实际高度（含状态栏让位与内边距），供列表顶部让位
            var headerPx by remember { mutableStateOf(0) }
            val density = LocalDensity.current
            val headerSpace = with(density) { headerPx.toDp() } + 4.dp

            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = { vm.load(force = true) },
                modifier = Modifier.fillMaxSize()
            ) {
                // v1.4.11：滚动时玻璃顶/底栏磨砂感消失只留透明度
                val listState = rememberLazyListState()
                ScrollGlassEffect(listState)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 14.dp, end = 14.dp,
                        top = headerSpace, bottom = 132.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // v1.4.14：未实名徽标引导到「安全中心」（实名认证已合入其中）
                    item { WelcomeCard(state) { onOpenPanel("security") } }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatCard(
                                Strings.SERVICE_COUNT,
                                if (state.loggedIn) state.hosts.size.toString() else Strings.UNKNOWN,
                                AppIcons.Server, CartoonColors.Mint, CartoonColors.MintSoft,
                                Modifier.weight(1f),
                                onClick = if (state.loggedIn && state.hosts.isNotEmpty()) {
                                    { onOpenPanel("hosts") }
                                } else null
                            )
                            StatCard(
                                Strings.NEWS_COUNT, state.news.size.toString(),
                                AppIcons.Bell, CartoonColors.Sky, CartoonColors.SkySoft,
                                Modifier.weight(1f)
                            )
                            StatCard(
                                Strings.BALANCE, state.account?.balance ?: Strings.UNKNOWN,
                                AppIcons.Wallet, CartoonColors.Lemon, CartoonColors.LemonSoft,
                                Modifier.weight(1f),
                                onClick = if (state.loggedIn) {
                                    { onOpenPanel("finance") }
                                } else null
                            )
                        }
                    }

                    item {
                        SectionHeader(Strings.MY_SERVICES, CartoonColors.Mint) {
                            if (state.loggedIn && state.hosts.isNotEmpty()) {
                                TrailingAction(Strings.VIEW_ALL) { onOpenPanel("hosts") }
                            }
                        }
                    }
                    if (state.hosts.isEmpty()) {
                        item {
                            if (state.loggedIn) {
                                ActionHintCard(Strings.NO_SERVICE, Strings.GO_SHOP_BROWSE, onGoShop)
                            } else {
                                ActionHintCard(Strings.LOGIN_PROMPT, Strings.GO_LOGIN_NOW, onNeedLogin)
                            }
                        }
                    } else {
                        items(state.hosts.take(5)) { host -> HostCard(host) { onOpenPanel(it) } }
                    }

                    item {
                        SectionHeader(Strings.ANNOUNCEMENTS, CartoonColors.Peach) {
                            if (state.news.isNotEmpty()) {
                                TrailingAction(Strings.MORE) { onOpenAnnouncements() }
                            }
                        }
                    }
                    if (state.news.isEmpty()) {
                        item { HintCard(Strings.NO_NEWS) }
                    } else {
                        items(state.news) { news -> NewsCard(news) { onOpenAnnouncement(news.id) } }
                    }

                    item { SectionHeader(Strings.QUICK_ENTRIES, CartoonColors.Brand) {} }
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
                                            onClick = { onOpenPanel(entry.navToken) }
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

            // 顶栏悬浮在列表之上（v1.4.10）：磨砂玻璃实时透出下方滚动内容
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { headerPx = it.size.height }
            ) {
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
            }
        }
    }
}

/** 区块标题 + 可选尾部动作（查看全部 / 更多）。 */
@Composable
private fun SectionHeader(
    text: String,
    accent: Color,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionTitle(text, accent, Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun TrailingAction(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CartoonKit.PillShape)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = CartoonColors.InkSub
        )
        Spacer(Modifier.width(2.dp))
        Icon(AppIcons.Forward, null, tint = CartoonColors.InkSub, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun WelcomeCard(state: DashboardUiState, onGoSecurity: () -> Unit) {
    // v1.4.14：名字区只显示脱敏邮箱（绝不显示实名姓名），实名状态仅用徽标表达；
    // 未实名徽标点击进入「安全中心」，再从其中的实名卡进入认证整页。
    val display = accountDisplay(state.account, state.cert, state.loggedIn)
    GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
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
            Column(Modifier.weight(1f)) {
                Text(Strings.WELCOME, style = MaterialTheme.typography.labelMedium, color = CartoonColors.InkSub)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        display.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = CartoonColors.Ink,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (display.badge != CertBadge.NONE) {
                        Spacer(Modifier.width(8.dp))
                        CertBadgeChip(
                            badge = display.badge,
                            onClick = if (display.badge == CertBadge.UNCERTIFIED) onGoSecurity else null
                        )
                    }
                }
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
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val clickable = onClick != null
    GlassCard(
        modifier = modifier.then(
            if (clickable) Modifier.clip(CartoonKit.CardShape).clickable { onClick?.invoke() } else Modifier
        ),
        alpha = UiT.glassCard
    ) {
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
    GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = CartoonColors.InkSub,
            modifier = Modifier.padding(16.dp)
        )
    }
}

/** 带动作按钮的引导卡：未登录「去登录」、无服务「去选购」。 */
@Composable
private fun ActionHintCard(text: String, actionText: String, onAction: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = CartoonColors.InkSub
            )
            CartoonButton(
                text = actionText,
                onClick = onAction,
                icon = AppIcons.Forward,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 已购服务卡：点击直达产品详情（有 id 时），否则进主机面板。 */
@Composable
private fun HostCard(host: HostSummary, onOpenPanel: (String) -> Unit) {
    val navToken = host.id?.takeIf { it.isNotBlank() }?.let { "product:$it" } ?: "hosts"
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CartoonKit.CardShape)
            .clickable { onOpenPanel(navToken) },
        alpha = UiT.glassCard
    ) {
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
            Icon(AppIcons.Forward, null, tint = CartoonColors.InkSub, modifier = Modifier.size(16.dp))
        }
    }
}

/** 公告卡：点击打开站点公告详情页（有 id 时）。 */
@Composable
private fun NewsCard(news: NewsItem, onOpenId: (String) -> Unit) {
    val hasDetail = news.id.isNotBlank()
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (hasDetail) {
                    Modifier.clip(CartoonKit.CardShape).clickable {
                        onOpenId(news.id)
                    }
                } else Modifier
            ),
        alpha = UiT.glassCard
    ) {
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
            if (hasDetail) {
                Icon(AppIcons.Forward, null, tint = CartoonColors.InkSub, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun QuickEntryCard(entry: QuickEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassCard(modifier = modifier, alpha = UiT.glassCard) {
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
