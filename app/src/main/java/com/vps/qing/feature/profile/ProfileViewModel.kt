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

/** 服务面板板块。 */
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
    /** 当前展开的服务板块；null = 全部折叠。 */
    val openPanel: ServicePanel? = null,
    val panels: Map<ServicePanel, ServicePanelData> = emptyMap()
)

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

    /** 展开服务板块（再点收起）。板块数据懒加载：展开才拉取，已有数据直接复用。 */
    fun togglePanel(panel: ServicePanel) {
        val current = _ui.value
        if (current.openPanel == panel) {
            _ui.update { it.copy(openPanel = null) }
            return
        }
        _ui.update { it.copy(openPanel = panel) }
        val existing = current.panels[panel]
        if (existing == null || !existing.loaded) fetchPanel(panel)
    }

    /** 手动刷新当前展开板块（重新拉站点数据）。 */
    fun refreshOpenPanel() {
        _ui.value.openPanel?.let { fetchPanel(it) }
    }

    private fun fetchPanel(panel: ServicePanel) {
        viewModelScope.launch {
            setPanel(panel) { it.copy(loading = true, error = null) }
            val r: ApiResult<(ServicePanelData) -> ServicePanelData> = when (panel) {
                ServicePanel.HOSTS -> repo.hosts().lift { s, d -> s.copy(hosts = d) }
                ServicePanel.ORDERS -> repo.orders().lift { s, d -> s.copy(orders = d) }
                ServicePanel.TRANSACTIONS -> repo.transactions().lift { s, d -> s.copy(txns = d) }
                ServicePanel.TICKETS -> repo.tickets().lift { s, d -> s.copy(tickets = d) }
                ServicePanel.SUB_ACCOUNTS -> repo.subAccounts().lift { s, d -> s.copy(subAccounts = d) }
                ServicePanel.SECURITY -> {
                    val rows = _ui.value.account?.let { ServiceParser.security(it) } ?: emptyList()
                    setPanel(panel) { it.copy(loading = false, loaded = true, security = rows, error = null) }
                    return@launch
                }
            }
            when (r) {
                is ApiResult.Success -> setPanel(panel) {
                    r.data(it).copy(loading = false, loaded = true, error = null)
                }
                is ApiResult.Failure -> setPanel(panel) {
                    it.copy(loading = false, loaded = true, error = "[${r.code}] ${r.msg}")
                }
                is ApiResult.Error -> setPanel(panel) {
                    it.copy(loading = false, loaded = true, error = friendly(r.throwable))
                }
            }
        }
    }

    private fun setPanel(panel: ServicePanel, transform: (ServicePanelData) -> ServicePanelData) {
        _ui.update {
            val data = transform(it.panels[panel] ?: ServicePanelData())
            it.copy(panels = it.panels + (panel to data))
        }
    }

    /** 把 ApiResult<T> 映射成「把 T 写入面板」的变换，统一错误处理。 */
    private fun <T> ApiResult<T>.lift(
        f: (ServicePanelData, T) -> ServicePanelData
    ): ApiResult<(ServicePanelData) -> ServicePanelData> = when (this) {
        is ApiResult.Success -> ApiResult.Success { s -> f(s, data) }
        is ApiResult.Failure -> this
        is ApiResult.Error -> this
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
