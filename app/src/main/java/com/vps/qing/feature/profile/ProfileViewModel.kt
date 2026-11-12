package com.vps.qing.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vps.qing.core.network.ApiResult
import com.vps.qing.core.network.HtmlFallbackException
import com.vps.qing.data.repository.AccountInfo
import com.vps.qing.data.repository.HostLine
import com.vps.qing.data.repository.OrderLine
import com.vps.qing.data.repository.ProfileRepository
import com.vps.qing.data.repository.ServiceParser
import com.vps.qing.data.repository.SubAccountLine
import com.vps.qing.data.repository.TicketLine
import com.vps.qing.data.repository.TxnLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** 账号与服务子页面板块。 */
enum class ServicePanel { HOSTS, ORDERS, TRANSACTIONS, TICKETS, SUB_ACCOUNTS, SECURITY }

data class ServicePanelData(
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
    val hosts: List<HostLine> = emptyList(),
    val orders: List<OrderLine> = emptyList(),
    val txns: List<TxnLine> = emptyList(),
    val tickets: List<TicketLine> = emptyList(),
    val subAccounts: List<SubAccountLine> = emptyList(),
    val security: List<Pair<String, String>> = emptyList()
)

data class ProfileUiState(
    val loading: Boolean = true,
    val account: AccountInfo? = null,
    val error: String? = null,
    val loggedOut: Boolean = false,
    /** 各服务子页面的数据缓存（进入页面懒加载）。 */
    val panels: Map<ServicePanel, ServicePanelData> = emptyMap()
)

/**
 * v1.3.1：账号与服务从手风琴面板改为独立子页面。
 * 进入子页面时懒加载（ensurePanel），页面内刷新按钮强制重拉（refreshPanel）。
 */
class ProfileViewModel(private val repo: ProfileRepository) : ViewModel() {

    private val _ui = MutableStateFlow(ProfileUiState())
    val ui: StateFlow<ProfileUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            when (val result = repo.account()) {
                is ApiResult.Success -> _ui.update {
                    it.copy(loading = false, account = result.data, error = null)
                }
                is ApiResult.Failure -> _ui.update {
                    it.copy(loading = false, error = "[${result.code}] ${result.msg}")
                }
                is ApiResult.Error -> _ui.update {
                    it.copy(loading = false, error = friendly(result.throwable))
                }
            }
        }
    }

    /** 进入子页面：未加载过则拉取，已加载直接复用缓存。 */
    fun ensurePanel(panel: ServicePanel) {
        val existing = _ui.value.panels[panel]
        if (existing != null && existing.loaded) return
        fetchPanel(panel)
    }

    /** 子页面手动刷新：强制重新拉站点数据。 */
    fun refreshPanel(panel: ServicePanel) {
        fetchPanel(panel)
    }

    private fun fetchPanel(panel: ServicePanel) {
        viewModelScope.launch {
            setPanel(panel) { it.copy(loading = true, error = null) }
            when (panel) {
                ServicePanel.HOSTS -> settle(panel, repo.hosts()) { s, d -> s.copy(hosts = d) }
                ServicePanel.ORDERS -> settle(panel, repo.orders()) { s, d -> s.copy(orders = d) }
                ServicePanel.TRANSACTIONS -> settle(panel, repo.transactions()) { s, d -> s.copy(txns = d) }
                ServicePanel.TICKETS -> settle(panel, repo.tickets()) { s, d -> s.copy(tickets = d) }
                ServicePanel.SUB_ACCOUNTS -> settle(panel, repo.subAccounts()) { s, d -> s.copy(subAccounts = d) }
                ServicePanel.SECURITY -> {
                    val rows = _ui.value.account?.let { ServiceParser.security(it) } ?: emptyList()
                    setPanel(panel) { it.copy(loading = false, loaded = true, security = rows, error = null) }
                }
            }
        }
    }

    /** 统一落盘：Success 写数据，Failure/Error 写错误。 */
    private suspend fun <T> settle(
        panel: ServicePanel,
        result: ApiResult<T>,
        write: (ServicePanelData, T) -> ServicePanelData
    ) {
        when (result) {
            is ApiResult.Success -> setPanel(panel) {
                write(it, result.data).copy(loading = false, loaded = true, error = null)
            }
            is ApiResult.Failure -> setPanel(panel) {
                it.copy(loading = false, loaded = true, error = "[${result.code}] ${result.msg}")
            }
            is ApiResult.Error -> setPanel(panel) {
                it.copy(loading = false, loaded = true, error = friendly(result.throwable))
            }
        }
    }

    private fun setPanel(panel: ServicePanel, transform: (ServicePanelData) -> ServicePanelData) {
        _ui.update {
            val data = transform(it.panels[panel] ?: ServicePanelData())
            it.copy(panels = it.panels + (panel to data))
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            _ui.update { it.copy(loggedOut = true) }
        }
    }

    private fun friendly(t: Throwable): String = when (t) {
        is HtmlFallbackException -> "接口返回了网页而不是 JSON，请确认登录态或站点版本"
        is IOException -> "网络连接失败，请检查网络"
        else -> t.message ?: "未知错误"
    }
}
