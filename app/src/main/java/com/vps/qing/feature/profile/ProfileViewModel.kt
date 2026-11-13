package com.vps.qing.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.vps.qing.core.network.ApiResult
import com.vps.qing.core.network.HtmlFallbackException
import com.vps.qing.data.repository.AccountInfo
import com.vps.qing.data.repository.AuthNode
import com.vps.qing.data.repository.CertInfo
import com.vps.qing.data.repository.CertPlugin
import com.vps.qing.data.repository.CountryOption
import com.vps.qing.data.repository.HostLine
import com.vps.qing.data.repository.HostOption
import com.vps.qing.data.repository.ModuleOption
import com.vps.qing.data.repository.OrderDetail
import com.vps.qing.data.repository.OrderLine
import com.vps.qing.data.repository.ProfileRepository
import com.vps.qing.data.repository.PurchasedDetail
import com.vps.qing.data.repository.ServiceParser
import com.vps.qing.data.repository.SubAccountBodies
import com.vps.qing.data.repository.SubAccountDetail
import com.vps.qing.data.repository.SubAccountForm
import com.vps.qing.data.repository.SubAccountLine
import com.vps.qing.data.repository.TicketBodies
import com.vps.qing.data.repository.TicketConfigInfo
import com.vps.qing.data.repository.TicketDetail
import com.vps.qing.data.repository.TicketLine
import com.vps.qing.data.repository.TicketOption
import com.vps.qing.data.repository.TicketStatistic
import com.vps.qing.data.repository.TxnLine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val certPlugins: List<CertPlugin> = emptyList(),
    /** 工单元数据（v1.4.3，随工单列表一起拉取）。 */
    val ticketStatistic: TicketStatistic? = null,
    val ticketTypes: List<TicketOption> = emptyList(),
    val ticketStatuses: List<TicketOption> = emptyList(),
    val ticketDepartments: List<TicketOption> = emptyList(),
    val ticketConfig: TicketConfigInfo? = null
)

/** 工单详情子页面状态。 */
data class TicketDetailUi(
    val loading: Boolean = false,
    val error: String? = null,
    val detail: TicketDetail? = null
)

/** 子账户详情 / 编辑回填状态。 */
data class SubAccountDetailUi(
    val loading: Boolean = false,
    val error: String? = null,
    val detail: SubAccountDetail? = null
)

/** 子账户新建/编辑表单所需的元数据（权限树、模块、产品、国家区号）。 */
data class SubAccountFormMeta(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val auth: List<AuthNode> = emptyList(),
    val modules: List<ModuleOption> = emptyList(),
    val hosts: List<HostOption> = emptyList(),
    val countries: List<CountryOption> = emptyList()
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
    val productDetails: Map<String, ProductDetailUi> = emptyMap(),
    /** 工单详情页缓存（ticketId -> 状态，v1.4.3）。 */
    val ticketDetails: Map<String, TicketDetailUi> = emptyMap(),
    /** 子账户详情/编辑回填缓存（id -> 状态）。 */
    val subAccountDetails: Map<String, SubAccountDetailUi> = emptyMap(),
    /** 工单状态筛选（null = 全部）。 */
    val ticketFilterStatusId: String? = null,
    /** 子账户表单元数据。 */
    val subFormMeta: SubAccountFormMeta = SubAccountFormMeta(),
    /** 写操作反馈（一次性）。 */
    val actionBusy: Boolean = false,
    val actionMessage: String? = null,
    val actionError: String? = null
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
 * 账号与服务：v1.3.1 起每项独立子页面 + 懒加载；
 * v1.4.3 补全工单中心（统计/类型/状态/部门/详情/回复/创建/关闭/催单）与
 * 子账户管理（权限树/模块/产品/区号/详情/创建/编辑/启停/删除）的完整交互。
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
        if (existing != null && existing.loaded) {
            if (panel == ServicePanel.SUB_ACCOUNTS) ensureSubFormMeta()
            return
        }
        fetchPanel(panel)
    }

    /** 子页面手动刷新：强制重新拉站点数据。 */
    fun refreshPanel(panel: ServicePanel) {
        fetchPanel(panel)
        if (panel == ServicePanel.SUB_ACCOUNTS) ensureSubFormMeta(force = true)
    }

    fun setTicketFilter(statusId: String?) = _ui.update { it.copy(ticketFilterStatusId = statusId) }

    /** 进入工单创建 / 子账户编辑页时确保表单元数据（关联产品、权限树、模块、区号）已加载。 */
    fun ensureFormMeta() = ensureSubFormMeta()

    /** 订单详情子页面：进入即拉取（已在缓存且成功过则复用）。 */
    fun ensureOrderDetail(orderId: String) {
        val existing = _ui.value.orderDetails[orderId]
        if (existing != null && existing.detail != null) return
        fetchOrderDetail(orderId)
    }

    fun refreshOrderDetail(orderId: String) = fetchOrderDetail(orderId)

    // ---- v1.4.2：已购产品详情（已订购配置 + 支付信息，只读）----

    fun ensureProductDetail(hostId: String) {
        val existing = _ui.value.productDetails[hostId]
        if (existing != null && existing.detail != null) return
        fetchProductDetail(hostId)
    }

    fun refreshProductDetail(hostId: String) = fetchProductDetail(hostId)

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

    // ---- v1.4.3：工单详情 ----

    fun ensureTicketDetail(id: String) {
        val existing = _ui.value.ticketDetails[id]
        if (existing != null && existing.detail != null) return
        fetchTicketDetail(id)
    }

    fun refreshTicketDetail(id: String) = fetchTicketDetail(id)

    private fun fetchTicketDetail(id: String) {
        viewModelScope.launch {
            setTicketDetail(id) { it.copy(loading = true, error = null) }
            when (val r = repo.ticketDetail(id)) {
                is ApiResult.Success -> setTicketDetail(id) {
                    it.copy(loading = false, error = null, detail = r.data)
                }
                is ApiResult.Failure -> setTicketDetail(id) {
                    it.copy(loading = false, error = "[${r.code}] ${r.msg}")
                }
                is ApiResult.Error -> setTicketDetail(id) {
                    it.copy(loading = false, error = friendly(r.throwable))
                }
            }
        }
    }

    private fun setTicketDetail(id: String, transform: (TicketDetailUi) -> TicketDetailUi) {
        _ui.update {
            val cur = transform(it.ticketDetails[id] ?: TicketDetailUi())
            it.copy(ticketDetails = it.ticketDetails + (id to cur))
        }
    }

    /** 创建工单：成功后刷新列表并给出反馈。 */
    fun submitTicket(
        title: String,
        typeId: String,
        content: String,
        hostIds: List<String> = emptyList()
    ) {
        if (title.isBlank() || typeId.isBlank() || content.isBlank()) {
            fail("请填写标题、类型与内容")
            return
        }
        viewModelScope.launch {
            busy(true)
            when (val r = repo.createTicket(TicketBodies.create(title, typeId, content, hostIds))) {
                is ApiResult.Success -> {
                    ok("工单已提交")
                    refreshPanel(ServicePanel.TICKETS)
                }
                else -> fail(r)
            }
        }
    }

    fun replyTicket(id: String, content: String, quoteReplyId: String = "0") {
        if (content.isBlank()) {
            fail("请输入回复内容")
            return
        }
        viewModelScope.launch {
            busy(true)
            when (val r = repo.replyTicket(id, TicketBodies.reply(content, quoteReplyId))) {
                is ApiResult.Success -> {
                    ok("回复已发送")
                    fetchTicketDetail(id)
                    refreshPanel(ServicePanel.TICKETS)
                }
                else -> fail(r)
            }
        }
    }

    fun closeTicket(id: String) {
        viewModelScope.launch {
            busy(true)
            when (val r = repo.closeTicket(id)) {
                is ApiResult.Success -> {
                    ok("工单已关闭")
                    fetchTicketDetail(id)
                    refreshPanel(ServicePanel.TICKETS)
                }
                else -> fail(r)
            }
        }
    }

    fun urgeTicket(id: String) {
        viewModelScope.launch {
            busy(true)
            when (val r = repo.urgeTicket(id)) {
                is ApiResult.Success -> ok("已催单，请等待客服回复")
                else -> fail(r)
            }
        }
    }

    // ---- v1.4.3：子账户元数据 / 详情 / 增删改 ----

    private fun ensureSubFormMeta(force: Boolean = false) {
        val meta = _ui.value.subFormMeta
        if (!force && (meta.loaded || meta.loading)) return
        fetchSubFormMeta()
    }

    private fun fetchSubFormMeta() {
        viewModelScope.launch {
            _ui.update { it.copy(subFormMeta = it.subFormMeta.copy(loading = true)) }
            val authCall = async { repo.authTree() }
            val modulesCall = async { repo.modules() }
            val hostsCall = async { repo.allHosts() }
            val countriesCall = async { repo.countryCodes() }
            _ui.update {
                it.copy(subFormMeta = SubAccountFormMeta(
                    loading = false, loaded = true,
                    auth = (authCall.await() as? ApiResult.Success)?.data.orEmpty(),
                    modules = (modulesCall.await() as? ApiResult.Success)?.data.orEmpty(),
                    hosts = (hostsCall.await() as? ApiResult.Success)?.data.orEmpty(),
                    countries = (countriesCall.await() as? ApiResult.Success)?.data.orEmpty()
                ))
            }
        }
    }

    fun ensureSubAccountDetail(id: String) {
        val existing = _ui.value.subAccountDetails[id]
        if (existing != null && existing.detail != null) return
        fetchSubAccountDetail(id)
    }

    fun refreshSubAccountDetail(id: String) = fetchSubAccountDetail(id)

    private fun fetchSubAccountDetail(id: String) {
        viewModelScope.launch {
            setSubAccountDetail(id) { it.copy(loading = true, error = null) }
            when (val r = repo.subAccountDetail(id)) {
                is ApiResult.Success -> setSubAccountDetail(id) {
                    it.copy(loading = false, error = null, detail = r.data)
                }
                is ApiResult.Failure -> setSubAccountDetail(id) {
                    it.copy(loading = false, error = "[${r.code}] ${r.msg}")
                }
                is ApiResult.Error -> setSubAccountDetail(id) {
                    it.copy(loading = false, error = friendly(r.throwable))
                }
            }
        }
    }

    private fun setSubAccountDetail(id: String, transform: (SubAccountDetailUi) -> SubAccountDetailUi) {
        _ui.update {
            val cur = transform(it.subAccountDetails[id] ?: SubAccountDetailUi())
            it.copy(subAccountDetails = it.subAccountDetails + (id to cur))
        }
    }

    /** 新建 / 编辑子账户。id 为 null 表示新建。 */
    fun saveSubAccount(form: SubAccountForm, id: String? = null) {
        val invalid = form.validate(id != null)
        if (invalid != null) {
            fail(invalid)
            return
        }
        viewModelScope.launch {
            busy(true)
            val body: JsonObject = SubAccountBodies.build(form, id)
            val r = if (id == null) repo.createSubAccount(body) else repo.updateSubAccount(id, body)
            when (r) {
                is ApiResult.Success -> {
                    ok(if (id == null) "子账户已创建" else "已保存")
                    refreshPanel(ServicePanel.SUB_ACCOUNTS)
                }
                else -> fail(r)
            }
        }
    }

    fun toggleSubAccountStatus(id: String, enable: Boolean) {
        viewModelScope.launch {
            busy(true)
            when (val r = repo.updateSubAccountStatus(id, enable)) {
                is ApiResult.Success -> {
                    ok(if (enable) "已启用" else "已停用")
                    refreshPanel(ServicePanel.SUB_ACCOUNTS)
                }
                else -> fail(r)
            }
        }
    }

    fun deleteSubAccount(id: String) {
        viewModelScope.launch {
            busy(true)
            when (val r = repo.deleteSubAccount(id)) {
                is ApiResult.Success -> {
                    ok("子账户已删除")
                    refreshPanel(ServicePanel.SUB_ACCOUNTS)
                }
                else -> fail(r)
            }
        }
    }

    // ---- 写操作反馈 ----

    private fun busy(value: Boolean) = _ui.update {
        it.copy(actionBusy = value, actionMessage = null, actionError = null)
    }

    private fun ok(message: String) = _ui.update {
        it.copy(actionBusy = false, actionMessage = message, actionError = null)
    }

    private fun fail(message: String) = _ui.update {
        it.copy(actionBusy = false, actionMessage = null, actionError = message)
    }

    private fun fail(r: ApiResult<*>) = when (r) {
        is ApiResult.Failure -> fail("[${r.code}] ${r.msg}")
        is ApiResult.Error -> fail(friendly(r.throwable))
        is ApiResult.Success -> fail("操作未成功，请重试")
    }

    fun consumeAction() = _ui.update {
        it.copy(actionMessage = null, actionError = null)
    }

    // ---- 订单详情 / 面板加载 ----

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

    private fun <T> ApiResult<List<T>>.dataIfSuccess(): List<T> =
        (this as? ApiResult.Success)?.data ?: emptyList()

    private fun fetchPanel(panel: ServicePanel) {
        viewModelScope.launch {
            setPanel(panel) { it.copy(loading = true, error = null) }
            when (panel) {
                ServicePanel.HOSTS -> settle(panel, repo.hosts()) { s, d -> s.copy(hosts = d) }
                ServicePanel.ORDERS -> settle(panel, repo.orders()) { s, d -> s.copy(orders = d) }
                ServicePanel.TRANSACTIONS -> settle(panel, repo.transactions()) { s, d -> s.copy(txns = d) }
                ServicePanel.TICKETS -> loadTicketsPanel(panel)
                ServicePanel.SUB_ACCOUNTS -> loadSubAccountsPanel(panel)
                ServicePanel.SECURITY -> {
                    val rows = _ui.value.account?.let { ServiceParser.security(it) } ?: emptyList()
                    setPanel(panel) { it.copy(loading = false, loaded = true, security = rows, error = null) }
                }
                ServicePanel.CERTIFICATION -> {
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

    /** 工单列表 + 统计/类型/状态/部门/通知配置一次性并行拉取。 */
    private suspend fun loadTicketsPanel(panel: ServicePanel) = coroutineScope {
        val listCall = async { repo.tickets() }
        val statCall = async { repo.ticketStatistic() }
        val typesCall = async { repo.ticketTypes() }
        val statusesCall = async { repo.ticketStatuses() }
        val deptCall = async { repo.ticketDepartments() }
        val configCall = async { repo.ticketConfig() }
        val list = listCall.await()
        val meta = ServicePanelData(
            ticketStatistic = (statCall.await() as? ApiResult.Success)?.data,
            ticketTypes = (typesCall.await() as? ApiResult.Success)?.data.orEmpty(),
            ticketStatuses = (statusesCall.await() as? ApiResult.Success)?.data.orEmpty(),
            ticketDepartments = (deptCall.await() as? ApiResult.Success)?.data.orEmpty(),
            ticketConfig = (configCall.await() as? ApiResult.Success)?.data
        )
        when (list) {
            is ApiResult.Success -> setPanel(panel) {
                it.copy(
                    loading = false, loaded = true, error = null, tickets = list.data,
                    ticketStatistic = meta.ticketStatistic,
                    ticketTypes = meta.ticketTypes,
                    ticketStatuses = meta.ticketStatuses,
                    ticketDepartments = meta.ticketDepartments,
                    ticketConfig = meta.ticketConfig
                )
            }
            is ApiResult.Failure -> setPanel(panel) {
                it.copy(loading = false, loaded = true, error = "[${list.code}] ${list.msg}")
            }
            is ApiResult.Error -> setPanel(panel) {
                it.copy(loading = false, loaded = true, error = friendly(list.throwable))
            }
        }
    }

    /** 子账户列表 + 表单元数据一次性并行拉取。 */
    private suspend fun loadSubAccountsPanel(panel: ServicePanel) = coroutineScope {
        val listCall = async { repo.subAccounts() }
        val authCall = async { repo.authTree() }
        val modulesCall = async { repo.modules() }
        val hostsCall = async { repo.allHosts() }
        val countriesCall = async { repo.countryCodes() }
        val list = listCall.await()
        val formMeta = SubAccountFormMeta(
            loading = false, loaded = true,
            auth = (authCall.await() as? ApiResult.Success)?.data.orEmpty(),
            modules = (modulesCall.await() as? ApiResult.Success)?.data.orEmpty(),
            hosts = (hostsCall.await() as? ApiResult.Success)?.data.orEmpty(),
            countries = (countriesCall.await() as? ApiResult.Success)?.data.orEmpty()
        )
        _ui.update { it.copy(subFormMeta = formMeta) }
        when (list) {
            is ApiResult.Success -> setPanel(panel) {
                it.copy(loading = false, loaded = true, error = null, subAccounts = list.data)
            }
            is ApiResult.Failure -> setPanel(panel) {
                it.copy(loading = false, loaded = true, error = "[${list.code}] ${list.msg}")
            }
            is ApiResult.Error -> setPanel(panel) {
                it.copy(loading = false, loaded = true, error = friendly(list.throwable))
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
