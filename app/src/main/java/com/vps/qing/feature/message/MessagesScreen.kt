package com.vps.qing.feature.message

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vps.qing.core.AppContainer
import com.vps.qing.core.simpleViewModelFactory
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.EmptyHint
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassPullRefreshBox
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.LoadingSkeleton
import com.vps.qing.core.ui.ScrollGlassEffect
import com.vps.qing.core.ui.Strings
import com.vps.qing.core.ui.topInsetPadding
import com.vps.qing.core.ui.UiT
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.data.repository.ArticleItem
import com.vps.qing.data.repository.MailItem
import com.vps.qing.data.repository.MessageKind

/**
 * 消息中心（v1.4.20 重构）。
 *
 * 把站点四类内容统一到一个页面：通知（站内信，带已读/未读）、公告、新闻、资源；
 * 顶部四个分段切换，未读角标驱动；文章点击进原生详情，站内信点击即标记已读。
 */
@Composable
fun MessagesScreen(
    onOpenWeb: (String) -> Unit = {},
    onOpenAnnouncements: () -> Unit = {}
) {
    val vm: MessageCenterViewModel = viewModel(
        factory = simpleViewModelFactory { MessageCenterViewModel(AppContainer.catalogRepository) }
    )
    val state by vm.ui.collectAsState()

    // 详情态：文章详情接管整个消息 Tab，返回键回到列表
    if (state.detail != null || state.detailLoading || state.detailError != null) {
        BackHandler { vm.closeDetail() }
        ArticleDetailView(
            title = state.detail?.title ?: Strings.MSG_DETAIL,
            type = state.detail?.type.orEmpty(),
            paragraphs = state.detail?.paragraphs ?: emptyList(),
            loading = state.detailLoading,
            error = state.detailError,
            onBack = { vm.closeDetail() },
            onRetry = { state.detail?.let { vm.openArticle(ArticleItem(it.id, it.title, state.detailKind)) } }
        )
        return
    }

    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize().topInsetPadding()) {
            GlassTopBar(
                title = Strings.MESSAGES_PLACEHOLDER,
                subtitle = if (state.unread > 0) "${Strings.MSG_UNREAD} ${state.unread}" else null,
                actions = {
                    if (state.tab == MessageTab.NOTICE && state.unread > 0) {
                        TextButton(onClick = { vm.markAllRead() }) {
                            Text(Strings.MSG_MARK_ALL_READ, color = CartoonColors.Brand)
                        }
                    }
                }
            )
            // 四段分类切换
            CategoryTabs(current = state.tab, unread = state.unread, onSelect = { vm.select(it) })

            GlassPullRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize(),
                showStrip = false,
                error = state.error
            ) {
                if (state.loading) {
                    LoadingSkeleton(modifier = Modifier.fillMaxWidth(), rows = 4)
                } else when (state.tab) {
                    MessageTab.NOTICE -> NoticeList(state, vm)
                    MessageTab.ANNOUNCEMENT -> ArticleList(
                        state.articles[MessageKind.ANNOUNCEMENT] ?: emptyList(),
                        Strings.MSG_EMPTY_ANNOUNCEMENT, vm
                    )
                    MessageTab.NEWS -> ArticleList(
                        state.articles[MessageKind.NEWS] ?: emptyList(),
                        Strings.MSG_EMPTY_NEWS, vm
                    )
                    MessageTab.RESOURCE -> ArticleList(
                        state.articles[MessageKind.RESOURCE] ?: emptyList(),
                        Strings.MSG_EMPTY_RESOURCE, vm
                    )
                }
            }
        }
    }
}


@Composable
private fun CategoryTabs(current: MessageTab, unread: Int, onSelect: (MessageTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = UiT.pagePad, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MessageTab.entries.forEach { tab ->
            val active = tab == current
            val label = when (tab) {
                MessageTab.NOTICE -> Strings.MSG_TAB_NOTICE
                MessageTab.ANNOUNCEMENT -> Strings.MSG_TAB_ANNOUNCEMENT
                MessageTab.NEWS -> Strings.MSG_TAB_NEWS
                MessageTab.RESOURCE -> Strings.MSG_TAB_RESOURCE
            }
            Box(
                Modifier
                    .clip(CartoonKit.PillShape)
                    .background(if (active) CartoonColors.Brand else CartoonColors.White.copy(alpha = 0.55f))
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) androidx.compose.ui.graphics.Color.White else CartoonColors.Ink
                    )
                    if (tab == MessageTab.NOTICE && unread > 0) {
                        Spacer(Modifier.width(5.dp))
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(CartoonColors.Coral),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (unread > 99) "99+" else "$unread",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeList(state: MessageCenterUiState, vm: MessageCenterViewModel) {
    if (!state.loggedIn) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyHint(icon = AppIcons.Mail, text = Strings.MSG_LOGIN_REQUIRED)
        }
        return
    }
    if (state.mails.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyHint(icon = AppIcons.Bell, text = Strings.MSG_EMPTY_NOTICE)
        }
        return
    }
    val listState = rememberLazyListState()
    ScrollGlassEffect(listState)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = UiT.pagePad, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(state.mails, key = { it.id }) { mail ->
            MailCard(mail, onClick = { vm.openMail(mail) }, onDelete = { vm.deleteMail(mail) })
        }
    }
}

@Composable
private fun ArticleList(
    items: List<ArticleItem>,
    emptyText: String,
    vm: MessageCenterViewModel
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyHint(icon = AppIcons.Message, text = emptyText)
        }
        return
    }
    val listState = rememberLazyListState()
    ScrollGlassEffect(listState)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = UiT.pagePad, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items, key = { "${it.kind}-${it.id}" }) { item ->
            ArticleCard(item, onClick = { vm.openArticle(item) })
        }
    }
}

@Composable
private fun MailCard(mail: MailItem, onClick: () -> Unit, onDelete: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CartoonKit.ItemShape)
                .clickable(onClick = onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CartoonKit.PillShape)
                    .background(if (mail.read) CartoonColors.Line.copy(alpha = 0.3f) else CartoonColors.Coral.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    AppIcons.Mail, null,
                    tint = if (mail.read) CartoonColors.InkSub else CartoonColors.Coral,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!mail.read) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(CartoonColors.Coral))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        mail.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (mail.read) FontWeight.Medium else FontWeight.Bold,
                        color = CartoonColors.Ink,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (mail.summary.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        mail.summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = CartoonColors.InkSub,
                        maxLines = 2
                    )
                }
            }
            TextButton(onClick = onDelete) {
                Text(Strings.MSG_DELETE, color = CartoonColors.InkSub, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ArticleCard(item: ArticleItem, onClick: () -> Unit) {
    val (icon, tint, soft) = when (item.kind) {
        MessageKind.ANNOUNCEMENT -> Triple(AppIcons.Bell, CartoonColors.Coral, CartoonColors.LemonSoft)
        MessageKind.NEWS -> Triple(AppIcons.Message, CartoonColors.Mint, CartoonColors.MintSoft)
        MessageKind.RESOURCE -> Triple(AppIcons.Info, CartoonColors.Sky, CartoonColors.SkySoft)
        MessageKind.MAIL -> Triple(AppIcons.Mail, CartoonColors.Brand, CartoonColors.BrandSoft)
    }
    GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CartoonKit.ItemShape)
                .clickable(onClick = onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).clip(CartoonKit.PillShape).background(soft),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = CartoonColors.Ink,
                    maxLines = 2
                )
                val sub = listOfNotNull(
                    item.groupName.takeIf { it.isNotBlank() },
                    item.type.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(sub, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                }
            }
            Icon(AppIcons.Forward, null, tint = CartoonColors.InkSub, modifier = Modifier.size(16.dp))
        }
    }
}

/** 通用文章详情：标题卡 + 段落列表（公告/新闻/资源同款原生渲染）。 */
@Composable
private fun ArticleDetailView(
    title: String,
    type: String,
    paragraphs: List<String>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize().topInsetPadding()) {
            GlassTopBar(title = Strings.MSG_DETAIL, navIcon = AppIcons.Back, onNavClick = onBack)
            GlassPullRefreshBox(
                isRefreshing = loading,
                onRefresh = onRetry,
                modifier = Modifier.fillMaxSize(),
                showStrip = false,
                error = error
            ) {
                when {
                    loading -> LoadingSkeleton(modifier = Modifier.fillMaxWidth(), rows = 4)
                    error != null || paragraphs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyHint(
                            icon = AppIcons.EmptyBox,
                            text = error ?: "（正文为空）",
                            action = { CartoonButton(text = Strings.RETRY, onClick = onRetry, icon = AppIcons.Refresh) }
                        )
                    }
                    else -> {
                        val listState = rememberLazyListState()
                        ScrollGlassEffect(listState)
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = UiT.pagePad, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (type.isNotBlank()) {
                                            Text(type, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = CartoonColors.Peach)
                                        }
                                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = CartoonColors.Ink)
                                    }
                                }
                            }
                            items(paragraphs) { p ->
                                GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
                                    Text(
                                        p,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CartoonColors.Ink,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
