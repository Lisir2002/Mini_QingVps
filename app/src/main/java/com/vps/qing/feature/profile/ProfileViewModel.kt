package com.vps.qing.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vps.qing.core.network.ApiResult
import com.vps.qing.core.network.HtmlFallbackException
import com.vps.qing.data.repository.AccountInfo
import com.vps.qing.data.repository.CertInfo
import com.vps.qing.data.repository.CertPlugin
import com.vps.qing.data.repository.CertRecord
import com.vps.qing.data.repository.HostLine
import com.vps.qing.data.repository.OrderLine
import com.vps.qing.data.repository.ProfileRepository
import com.vps.qing.data.repository.ServiceParser
import com.vps.qing.data.repository.SubAccountLine
import com.vps.qing.data.repository.TicketLine
import com.vps.qing.data.repository.OrderDetail
import com.vps.qing.data.repository.PurchasedDetail
import com.vps.qing.data.repository.TxnLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** 账号与服务子页面板块。 */
enum class ServicePanel { HOSTS, ORDERS, TRANSACTIONS, TICKETS, SUB_ACCOUNTS, SECURITY, CERTIFICATION }

data class ServicePanelData(
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
    val hosts: List<HostLine> = emptyList(),
    val orders: List<OrderLine> = emptyList(),
    val txns: List<TxnLine> = emptyList(),
    val tickets: List<TicketLine> = emptyList(),
    val subAccounts: List<SubAccountLine> = emptyList(),
    val security: List<Pair<String, String>> = emptyList(),
    /** 实名认证（v1.4.0 深度适配站点认证接口）。 */
    val cert: CertInfo? = null,
    val certPlugins: List<CertPlugin> = emptyList()
)

data class ProfileUiState(
    val loading: Boolean = true,
    val account: AccountInfo? = null,
    val error: String? = null,
    val loggedOut: Boolean = false,
    /** 各服务子页面的数据缓存（进入页面懒加载）。 */
    val panels: Map<ServicePanel, ServicePanelData> = emptyMap(),
    /** 订单详情页缓存（orderId -> 状态）。 */
    val orderDetails: Map<String, OrderDetailUi> = emptyMap(),
    /** 已购产品详情页缓存（hostId -> 状态，v1.4.2）。 */
    val productDetails: Map<String, ProductDetailUi> = emptyMap()
)

/** 订单详情子页面状态。 */
data class OrderDetailUi(
    val loading: Boolean = false,
    val error: String? = null,
    val detail: OrderDetail? = null
)

/** 已购产品详情子页面状态（v1.4.2）。 */
data class ProductDetailUi(
    val loading: Boolean = false,
    val error: String? = null,
    val detail: PurchasedDetail? = null
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

    /** 订单详情子页面：进入即拉取（已在缓存且成功过则复用）。 */
    fun ensureOrderDetail(orderId: String) {
        val existing = _ui.value.orderDetails[orderId]
        if (existing != null && existing.detail != null) return
        fetchOrderDetail(orderId)
    }

    fun refreshOrderDetail(orderId: String) {
        fetchOrderDetail(orderId)
    }

    // ---- v1.4.2：已购产品详情（已订购配置 + 支付信息，只读）----

    /** 进入详情页：未成功加载过则拉取，已加载直接复用缓存。 */
    fun ensureProductDetail(hostId: String) {
        val existing = _ui.value.productDetails[hostId]
        if (existing != null && existing.detail != null) return
        fetchProductDetail(hostId)
    }

    fun refreshProductDetail(hostId: String) {
        fetchProductDetail(hostId)
    }

    private fun fetchProductDetail(hostId: String) {
        viewModelScope.launch {
            setProductDetail(hostId) { it.copy(loading = true, error = null) }
            when (val r = repo.productDetail(hostId)) {
                is ApiResult.Success -> setProductDetail(hostId) {
                    it.copy(loading = false, error = null, detail = r.data)
                }
                is ApiResult.Failure -> setProductDetail(hostId) {
                    it.copy(loading = false, error = "[${r.code}] ${r.msg}")
                }
                is ApiResult.Error -> setProductDetail(hostId) {
                    it.copy(loading = false, error = friendly(r.throwable))
                }
            }
        }
    }

    private fun setProductDetail(hostId: String, transform: (ProductDetailUi) -> ProductDetailUi) {
        _ui.update {
            val cur = transform(it.productDetails[hostId] ?: ProductDetailUi())
            it.copy(productDetails = it.productDetails + (hostId to cur))
        }
    }

    private fun fetchOrderDetail(orderId: String) {
        viewModelScope.launch {
            setOrderDetail(orderId) { it.copy(loading = true, error = null) }
            when (val r = repo.orderDetail(orderId)) {
                is ApiResult.Success -> setOrderDetail(orderId) {
                    it.copy(loading = false, error = null, detail = r.data)
                }
                is ApiResult.Failure -> setOrderDetail(orderId) {
                    it.copy(loading = false, error = "[${r.code}] ${r.msg}")
                }
                is ApiResult.Error -> setOrderDetail(orderId) {
                    it.copy(loading = false, error = friendly(r.throwable))
                }
            }
        }
    }

    private fun setOrderDetail(orderId: String, transform: (OrderDetailUi) -> OrderDetailUi) {
        _ui.update {
            val cur = transform(it.orderDetails[orderId] ?: OrderDetailUi())
            it.copy(orderDetails = it.orderDetails + (orderId to cur))
        }
    }

    private fun <T> ApiResult<List<T>>.dataIfSuccess(): List<T> = (this as? ApiResult.Success)?.data ?: emptyList()

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
                ServicePanel.CERTIFICATION -> {
                    // 认证信息（核心）+ 认证方式（辅助，失败不影响主体展示）
                    val infoResult = repo.certInfo()
                    val pluginResult = repo.certPlugins()
                    when (infoResult) {
                        is ApiResult.Success -> setPanel(panel) {
                            it.copy(
                                loading = false, loaded = true, error = null,
                                cert = infoResult.data, certPlugins = pluginResult.dataIfSuccess()
                            )
                        }
                        is ApiResult.Failure -> setPanel(panel) {
                            it.copy(loading = false, loaded = true, error = "[${infoResult.code}] ${infoResult.msg}")
                        }
                        is ApiResult.Error -> setPanel(panel) {
                            it.copy(loading = false, loaded = true, error = friendly(infoResult.throwable))
                        }
                    }
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
