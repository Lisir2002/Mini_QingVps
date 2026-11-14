package com.vps.qing.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vps.qing.core.AppContainer
import com.vps.qing.core.network.ApiResult
import com.vps.qing.data.repository.AccountInfo
import com.vps.qing.data.repository.CatalogRepository
import com.vps.qing.data.repository.CertInfo
import com.vps.qing.data.repository.HostSummary
import com.vps.qing.data.repository.NewsItem
import com.vps.qing.data.repository.ProfileRepository
import com.vps.qing.data.repository.isUnpaidHostStatus
import com.vps.qing.core.ui.Strings
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class DashboardUiState(
    val loading: Boolean = true,
    val loggedIn: Boolean = false,
    val account: AccountInfo? = null,
    /** 实名状态（v1.4.12）：决定首页欢迎卡显示实名姓名还是账号名 + 未实名徽标。 */
    val cert: CertInfo? = null,
    val hosts: List<HostSummary> = emptyList(),
    val news: List<NewsItem> = emptyList(),
    val notice: String? = null
)

/**
 * 仪表盘数据。公告可匿名拉取；已购主机 / 账号需登录态，
 * 匿名时站点返回 body.status=401，这里降级成空列表 + 提示，不弹错误。
 *
 * v1.4.4：接口并发（站点 TTFB 约 1.6s），未登录时跳过注定 401 的私有请求。
 * v1.4.6：移除从未展示的 menu 请求（此前每次进入首页白耗一条 1.6s 往返），
 * 公告/主机数据交由分层缓存，非 force 刷新时命中 L1 几乎零延迟。
 */
class DashboardViewModel(
    private val catalog: CatalogRepository,
    private val profile: ProfileRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(DashboardUiState())
    val ui: StateFlow<DashboardUiState> = _ui.asStateFlow()

    init {
        load()
    }

    private fun <T> ApiResult<List<T>>.orEmpty(): List<T> =
        if (this is ApiResult.Success) data else emptyList()

    private fun <T> ApiResult<T>.orNull(): T? =
        if (this is ApiResult.Success) data else null

    /** force=true 由下拉刷新触发：清缓存后真正回源。 */
    fun load(force: Boolean = false) {
        viewModelScope.launch {
            if (force) AppContainer.invalidateCache()
            // 只改 loading，保留上一次数据，刷新过程不闪白屏
            _ui.update { it.copy(loading = true) }
            val loggedIn = AppContainer.isLoggedIn()

            supervisorScope {
                // 三个 Deferred 同时启动，共用 HTTP/2 连接多路复用
                val newsCall = async { catalog.news().orEmpty() }
                val hostsCall = async {
                    if (loggedIn) catalog.hosts().orEmpty() else emptyList()
                }
                val accountCall = async {
                    if (loggedIn) profile.account().orNull() else null
                }
                // v1.4.12：实名状态与账号信息并发拉取，401/插件未开启时降级为 null
                val certCall = async {
                    if (loggedIn) (profile.certInfo() as? ApiResult.Success)?.data else null
                }

                val newsList = newsCall.await()
                val hostsList = hostsCall.await()
                val accountInfo = accountCall.await()
                val certInfo = certCall.await()

                _ui.update {
                    it.copy(
                        loading = false,
                        loggedIn = loggedIn,
                        account = accountInfo,
                        cert = certInfo,
                        // v1.4.11：已购服务只展示已支付的机器，待支付的不进首页列表
                        hosts = hostsList.filter { host -> !isUnpaidHostStatus(host.status) },
                        news = newsList,
                        notice = if (loggedIn) null else Strings.LOGIN_PROMPT
                    )
                }
            }
        }
    }
}
