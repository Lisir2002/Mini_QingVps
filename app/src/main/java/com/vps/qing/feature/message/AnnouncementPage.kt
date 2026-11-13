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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.vps.qing.data.repository.NewsDetail
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
 * 网站公告独立页面（v1.4.7 列表 / v1.4.10 内容页原生化）。
 * 点击公告卡不再打开 announcement.htm 网页，改为渲染原生详情 overlay。
 *
 * @param openDetailId 外部（如首页公告卡）请求直接打开的公告 id；消费后回调置空。
 */
@Composable
fun AnnouncementPage(
    onBack: () -> Unit,
    onOpenWeb: (String) -> Unit,
    openDetailId: String? = null,
    onDetailConsumed: () -> Unit = {}
) {
    val vm: AnnouncementsViewModel = viewModel(
        factory = simpleViewModelFactory { AnnouncementsViewModel(AppContainer.catalogRepository) }
    )
    val state by vm.ui.collectAsState()

    // 原生详情 overlay（v1.4.10）：列表之上整页覆盖
    var detailId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(openDetailId) {
        openDetailId?.let { detailId = it; onDetailConsumed() }
    }
    BackHandler(enabled = detailId != null) { detailId = null }

    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = Strings.ANNOUNCEMENTS,
                subtitle = "共 ${state.news.size} 条",
                navIcon = AppIcons.Back,
                onNavClick = if (detailId != null) ({ detailId = null }) else onBack,
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
                    items(state.news) { news -> AnnouncementCard(news) { detailId = news.id } }
                }
            }
        }
    }

    // 公告内容详情（原生，v1.4.10）：覆盖在列表页之上
    detailId?.let { id ->
        AnnouncementDetailPage(newsId = id, onBack = { detailId = null })
    }
}

// ---------------------------------------------------------------------------
// 公告内容详情（v1.4.10 原生化，替代 announcement.htm 网页）
// ---------------------------------------------------------------------------

data class AnnouncementDetailUiState(
    val loading: Boolean = true,
    val detail: NewsDetail? = null,
    val error: String? = null
)

/** 公告详情数据源：GET /news/{id}（匿名可用，走统一缓存层，写前缀 news/）。 */
class AnnouncementDetailViewModel(
    private val catalog: CatalogRepository,
    private val newsId: String
) : ViewModel() {

    private val _ui = MutableStateFlow(AnnouncementDetailUiState())
    val ui: StateFlow<AnnouncementDetailUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            if (force) AppContainer.invalidateCache("news/")
            _ui.update { it.copy(loading = true, error = null) }
            when (val r = catalog.newsDetail(newsId)) {
                is com.vps.qing.core.network.ApiResult.Success -> _ui.update {
                    it.copy(loading = false, detail = r.data, error = null)
                }
                is com.vps.qing.core.network.ApiResult.Failure -> _ui.update {
                    it.copy(loading = false, error = "[${r.code}] ${r.msg}")
                }
                is com.vps.qing.core.network.ApiResult.Error -> _ui.update {
                    it.copy(loading = false, error = r.throwable.message ?: "公告加载失败")
                }
            }
        }
    }
}

/** 公告内容详情页：标题卡（类型/时间）+ 正文段落直排；返回/刷新/加载/错误态齐全。 */
@Composable
fun AnnouncementDetailPage(
    newsId: String,
    onBack: () -> Unit
) {
    val vm: AnnouncementDetailViewModel = viewModel(
        key = "announcement-$newsId",
        factory = simpleViewModelFactory {
            AnnouncementDetailViewModel(AppContainer.catalogRepository, newsId)
        }
    )
    val state by vm.ui.collectAsState()
    val detail = state.detail

    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = "公告详情",
                navIcon = AppIcons.Back,
                onNavClick = onBack,
                actions = {
                    if (!state.loading) {
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
                detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 标题卡：类型徽标 + 标题 + 时间
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), alpha = 0.72f) {
                            Column(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (detail.type.isNotBlank()) {
                                    Text(
                                        detail.type,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = CartoonColors.Peach
                                    )
                                }
                                Text(
                                    detail.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CartoonColors.Ink
                                )
                                if (detail.createTime > 0) {
                                    Text(
                                        formatAnnouncementTime(detail.createTime),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CartoonColors.InkSub
                                    )
                                }
                            }
                        }
                    }
                    // 正文段落
                    if (detail.paragraphs.isEmpty()) {
                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth(), alpha = 0.72f) {
                                Text(
                                    "（正文为空）",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CartoonColors.InkSub,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(detail.paragraphs.size) { idx ->
                            val paragraph = detail.paragraphs[idx]
                            GlassCard(modifier = Modifier.fillMaxWidth(), alpha = 0.72f) {
                                Text(
                                    paragraph,
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
