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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 * 仪表盘数据。公告 / 菜单可匿名拉取；已订购主机 / 账号需登录态，
 * 匿名时站点返回 body.status=401，这里降级成空列表 + 提示，不弹错误。
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

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            val loggedIn = AppContainer.isLoggedIn()

            val news = when (val r = catalog.news()) {
                is ApiResult.Success -> r.data
                else -> emptyList()
            }
            val menu = when (val r = catalog.menu()) {
                is ApiResult.Success -> r.data
                else -> emptyList()
            }
            val hosts = when (val r = catalog.hosts()) {
                is ApiResult.Success -> r.data
                else -> emptyList()
            }
            val account = when (val r = profile.account()) {
                is ApiResult.Success -> r.data
                else -> null
            }

            _ui.update {
                it.copy(
                    loading = false,
                    loggedIn = loggedIn,
                    account = account,
                    hosts = hosts,
                    news = news,
                    menu = menu,
                    notice = if (loggedIn) null else "登录后查看你的服务与账单"
                )
            }
        }
    }
}
