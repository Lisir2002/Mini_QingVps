package com.vps.qing.feature.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vps.qing.core.AppContainer
import com.vps.qing.core.network.ApiResult
import com.vps.qing.data.repository.ArticleDetail
import com.vps.qing.data.repository.ArticleItem
import com.vps.qing.data.repository.CatalogRepository
import com.vps.qing.data.repository.MailItem
import com.vps.qing.data.repository.MessageKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 消息中心选中的分类。 */
enum class MessageTab(val labelRes: String) {
    NOTICE("通知"),
    ANNOUNCEMENT("公告"),
    NEWS("新闻"),
    RESOURCE("资源")
}

data class MessageCenterUiState(
    val tab: MessageTab = MessageTab.NOTICE,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** 站内信（通知）。 */
    val mails: List<MailItem> = emptyList(),
    val unread: Int = 0,
    val loggedIn: Boolean = true,
    /** 文章列表按类型缓存，切换 Tab 不必每次重拉。 */
    val articles: Map<MessageKind, List<ArticleItem>> = emptyMap(),
    /** 当前打开的文章详情；null 表示列表态。 */
    val detail: ArticleDetail? = null,
    val detailKind: MessageKind = MessageKind.ANNOUNCEMENT,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    /** 一次性提示。 */
    val toast: String? = null
)

/**
 * v1.4.20 消息中心状态机。
 *
 * 设计：一个 Tab = 一类内容，列表与详情分离；站内信承载"已读/未读"（站点真实邮件接口），
 * 文章类（公告/新闻/资源）为站点全局内容、无已读语义。所有数据来自站点真实接口。
 */
class MessageCenterViewModel(
    private val catalog: CatalogRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(MessageCenterUiState())
    val ui: StateFlow<MessageCenterUiState> = _ui.asStateFlow()

    init {
        _ui.update { it.copy(loggedIn = AppContainer.isLoggedIn()) }
        loadTab(MessageTab.NOTICE)
    }

    fun select(tab: MessageTab) {
        _ui.update { it.copy(tab = tab, error = null, detail = null) }
        loadTab(tab)
    }

    fun loadTab(tab: MessageTab, force: Boolean = false) {
        if (force) AppContainer.invalidateCache("news-menu")
        when (tab) {
            MessageTab.NOTICE -> loadMails()
            MessageTab.ANNOUNCEMENT -> loadArticles(MessageKind.ANNOUNCEMENT)
            MessageTab.NEWS -> loadArticles(MessageKind.NEWS)
            MessageTab.RESOURCE -> loadArticles(MessageKind.RESOURCE)
        }
    }

    fun refresh() = loadTab(_ui.value.tab, force = true)

    private fun loadMails() {
        val loggedIn = AppContainer.isLoggedIn()
        _ui.update {
            it.copy(
                loggedIn = loggedIn,
                loading = it.mails.isEmpty(),
                refreshing = it.mails.isNotEmpty(),
                error = null
            )
        }
        if (!loggedIn) {
            _ui.update { it.copy(loading = false, refreshing = false, mails = emptyList(), unread = 0) }
            return
        }
        viewModelScope.launch {
            when (val r = catalog.mailList()) {
                is ApiResult.Success -> {
                    val mails = r.data
                    _ui.update {
                        it.copy(
                            loading = false, refreshing = false, mails = mails,
                            unread = mails.count { m -> !m.read }, error = null
                        )
                    }
                    // 未读数以概要接口为准（若可用），失败则保留列表统计
                    when (val s = catalog.mailSummary()) {
                        is ApiResult.Success -> _ui.update { st ->
                            val summary = com.vps.qing.data.repository.MessageParser.mailSummary(s.data, mails)
                            st.copy(unread = summary.unread)
                        }
                        else -> Unit
                    }
                }
                is ApiResult.Failure -> _ui.update {
                    it.copy(loading = false, refreshing = false, error = "[${r.code}] ${r.msg}")
                }
                is ApiResult.Error -> _ui.update {
                    it.copy(loading = false, refreshing = false, error = r.throwable.message ?: "通知加载失败")
                }
            }
        }
    }

    private fun loadArticles(kind: MessageKind) {
        _ui.update {
            it.copy(
                loading = it.articles[kind].isNullOrEmpty(),
                refreshing = !it.articles[kind].isNullOrEmpty(),
                error = null
            )
        }
        viewModelScope.launch {
            val result: ApiResult<List<ArticleItem>> = when (kind) {
                MessageKind.ANNOUNCEMENT -> catalog.announcements()
                MessageKind.NEWS -> when (val nr = catalog.news()) {
                    is ApiResult.Success -> ApiResult.Success(nr.data.map {
                        ArticleItem(it.id, it.title, MessageKind.NEWS, it.type, it.createTime)
                    })
                    is ApiResult.Failure -> ApiResult.Failure(nr.code, nr.msg)
                    is ApiResult.Error -> ApiResult.Error(nr.throwable)
                }
                MessageKind.RESOURCE -> catalog.resources()
                MessageKind.MAIL -> ApiResult.Success(emptyList())
            }
            when (result) {
                is ApiResult.Success -> _ui.update {
                    it.copy(
                        loading = false, refreshing = false, error = null,
                        articles = it.articles + (kind to result.data)
                    )
                }
                is ApiResult.Failure -> _ui.update {
                    it.copy(loading = false, refreshing = false, error = "[${result.code}] ${result.msg}")
                }
                is ApiResult.Error -> _ui.update {
                    it.copy(loading = false, refreshing = false, error = result.throwable.message ?: "内容加载失败")
                }
            }
        }
    }

    /** 打开文章详情：公告/资源用各自详情接口；新闻复用 newsDetail。 */
    fun openArticle(item: ArticleItem) {
        _ui.update {
            it.copy(
                detail = null, detailKind = item.kind,
                detailLoading = true, detailError = null
            )
        }
        viewModelScope.launch {
            val r = when (item.kind) {
                MessageKind.NEWS -> catalog.newsDetail(item.id).let { res ->
                    when (res) {
                        is ApiResult.Success -> if (res.data != null) ApiResult.Success(
                            ArticleDetail(res.data.id, res.data.title, res.data.type, res.data.createTime, res.data.paragraphs)
                        ) else ApiResult.Error(NullPointerException("empty"))
                        is ApiResult.Failure -> ApiResult.Failure(res.code, res.msg)
                        is ApiResult.Error -> ApiResult.Error(res.throwable)
                    }
                }
                else -> catalog.articleDetail(item.id, item.kind)
            }
            when (r) {
                is ApiResult.Success -> _ui.update {
                    it.copy(detail = r.data, detailLoading = false, detailError = null)
                }
                is ApiResult.Failure -> _ui.update {
                    it.copy(detailLoading = false, detailError = "[${r.code}] ${r.msg}")
                }
                is ApiResult.Error -> _ui.update {
                    it.copy(detailLoading = false, detailError = r.throwable.message ?: "详情加载失败")
                }
            }
        }
    }

    fun closeDetail() = _ui.update { it.copy(detail = null, detailError = null) }

    /** 打开站内信详情：本地先把该条标为已读，同时调用站点已读接口。 */
    fun openMail(mail: MailItem) {
        markRead(mail.id)
    }

    fun markRead(id: String) {
        val before = _ui.value.mails
        _ui.update { st ->
            val next = st.mails.map { if (it.id == id && !it.read) it.copy(read = true) else it }
            st.copy(mails = next, unread = next.count { !it.read })
        }
        viewModelScope.launch {
            when (catalog.mailRead(id)) {
                is ApiResult.Success -> Unit
                else -> _ui.update { it.copy(mails = before, unread = before.count { !it.read }, toast = "标记已读失败") }
            }
        }
    }

    fun markAllRead() {
        val unreadIds = _ui.value.mails.filter { !it.read }.map { it.id }
        if (unreadIds.isEmpty()) return
        val before = _ui.value.mails
        _ui.update { st ->
            val next = st.mails.map { it.copy(read = true) }
            st.copy(mails = next, unread = 0, toast = "已全部标为已读")
        }
        viewModelScope.launch {
            var failed = false
            unreadIds.forEach { id ->
                if (catalog.mailRead(id) !is ApiResult.Success) failed = true
            }
            if (failed) _ui.update {
                it.copy(mails = before, unread = before.count { !it.read }, toast = "部分标记失败，请重试")
            }
        }
    }

    fun deleteMail(mail: MailItem) {
        val before = _ui.value.mails
        _ui.update { st ->
            val next = st.mails.filterNot { it.id == mail.id }
            st.copy(mails = next, unread = next.count { !it.read })
        }
        viewModelScope.launch {
            when (catalog.mailDelete(mail.id)) {
                is ApiResult.Success -> Unit
                else -> _ui.update { it.copy(mails = before, unread = before.count { !it.read }, toast = "删除失败") }
            }
        }
    }

    fun consumeToast() = _ui.update { it.copy(toast = null) }
}
