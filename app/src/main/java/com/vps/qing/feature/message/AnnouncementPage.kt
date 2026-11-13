package com.vps.qing.feature.message

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vps.qing.core.AppContainer
import com.vps.qing.core.simpleViewModelFactory
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonIconChip
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.EmptyHint
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.Strings
import com.vps.qing.data.repository.CatalogRepository
import com.vps.qing.data.repository.NewsItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnnouncementsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val news: List<NewsItem> = emptyList(),
    val error: String? = null
)

/** 公告列表数据源：GET /announcement（匿名可用，走统一缓存层）。 */
class AnnouncementsViewModel(
    private val catalog: CatalogRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(AnnouncementsUiState())
    val ui: StateFlow<AnnouncementsUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            if (force) AppContainer.invalidateCache()
            _ui.update { it.copy(loading = it.news.isEmpty(), refreshing = it.news.isNotEmpty(), error = null) }
            when (val r = catalog.news()) {
                is com.vps.qing.core.network.ApiResult.Success -> _ui.update {
                    it.copy(loading = false, refreshing = false, news = r.data, error = null)
                }
                is com.vps.qing.core.network.ApiResult.Failure -> _ui.update {
                    it.copy(loading = false, refreshing = false, error = "[${r.code}] ${r.msg}")
                }
                is com.vps.qing.core.network.ApiResult.Error -> _ui.update {
                    it.copy(loading = false, refreshing = false, error = r.throwable.message ?: "公告加载失败")
                }
            }
        }
    }
}

/**
 * 网站公告独立页面（v1.4.7）：原生列表替代原先的 WebView 列表。
 * 点击公告卡打开站点公告详情页（announcement.htm?id=，登录态已同步）。
 */
@Composable
fun AnnouncementPage(
    onBack: () -> Unit,
    onOpenWeb: (String) -> Unit
) {
    val vm: AnnouncementsViewModel = viewModel(
        factory = simpleViewModelFactory { AnnouncementsViewModel(AppContainer.catalogRepository) }
    )
    val state by vm.ui.collectAsState()

    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = Strings.ANNOUNCEMENTS,
                subtitle = "共 ${state.news.size} 条",
                navIcon = AppIcons.Back,
                onNavClick = onBack,
                actions = {
                    if (!state.loading && !state.refreshing) {
                    CartoonIconChip(
                        icon = AppIcons.Refresh,
                            onClick = { vm.load(force = true) },
                            tint = CartoonColors.Mint,
                            bg = CartoonColors.MintSoft
                        )
                    }
                }
            )
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CartoonColors.Brand)
                }
                state.error != null && state.news.isEmpty() -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    EmptyHint(
                        icon = AppIcons.EmptyBox,
                        text = state.error ?: "公告加载失败",
                        action = {
                            CartoonButton(
                                text = Strings.RETRY,
                                onClick = { vm.load(force = true) },
                                icon = AppIcons.Refresh
                            )
                        }
                    )
                }
                state.news.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyHint(icon = AppIcons.EmptyBox, text = Strings.NO_NEWS)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.news) { news -> AnnouncementCard(news) { onOpenWeb(it) } }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementCard(news: NewsItem, onOpenWeb: (String) -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CartoonKit.CardShape)
            .clickable { onOpenWeb(AppContainer.webUrl("announcement.htm?id=${news.id}")) },
        alpha = 0.82f
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CartoonKit.CardShape)
                    .background(CartoonColors.PeachSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Bell, null, tint = CartoonColors.Peach, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    news.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = CartoonColors.Ink
                )
                if (news.createTime > 0) {
                    Text(
                        formatAnnouncementTime(news.createTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = CartoonColors.InkSub
                    )
                }
            }
            Icon(AppIcons.Forward, null, tint = CartoonColors.InkSub, modifier = Modifier.size(16.dp))
        }
    }
}

private fun formatAnnouncementTime(seconds: Long): String = runCatching {
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
        .format(java.util.Date(seconds * 1000L))
}.getOrDefault("")
