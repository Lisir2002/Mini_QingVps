package com.vps.qing.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vps.qing.core.AppContainer
import com.vps.qing.core.network.ApiResult
import com.vps.qing.data.repository.AccountInfo
import com.vps.qing.data.repository.CatalogRepository
import com.vps.qing.data.repository.HostSummary
import com.vps.qing.data.repository.MenuEntry
import com.vps.qing.data.repository.NewsItem
import com.vps.qing.data.repository.ProfileRepository
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
    val hosts: List<HostSummary> = emptyList(),
    val news: List<NewsItem> = emptyList(),
    val menu: List<MenuEntry> = emptyList(),
    val notice: String? = null
)

/**
 * 仪表盘数据。公告 / 菜单可匿名拉取；已购主机 / 账号需登录态，
 * 匿名时站点返回 body.status=401，这里降级成空列表 + 提示，不弹错误。
 *
 * v1.4.4：四路接口改为并发（站点 TTFB 约 1.6s，串行会让首屏等到约 4 倍延迟），
 * 未登录时跳过两个注定 401 的私有请求；刷新时保留已有数据不白屏。
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
                // 四个 Deferred 同时启动，共用 HTTP/2 连接多路复用
                val newsCall = async { catalog.news().orEmpty() }
                val menuCall = async { catalog.menu().orEmpty() }
                val hostsCall = async {
                    if (loggedIn) catalog.hosts().orEmpty() else emptyList()
                }
                val accountCall = async {
                    if (loggedIn) profile.account().orNull() else null
                }

                val newsList = newsCall.await()
                val menuList = menuCall.await()
                val hostsList = hostsCall.await()
                val accountInfo = accountCall.await()

                _ui.update {
                    it.copy(
                        loading = false,
                        loggedIn = loggedIn,
                        account = accountInfo,
                        hosts = hostsList,
                        news = newsList,
                        menu = menuList,
                        notice = if (loggedIn) null else "登录后查看你的服务与账单"
                    )
                }
            }
        }
    }
}
