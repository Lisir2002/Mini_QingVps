package com.vps.qing.feature.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.vps.qing.core.AppContainer
import com.vps.qing.core.network.ApiResult
import com.vps.qing.core.notify.QingNotifier
import com.vps.qing.data.repository.CartBodies
import com.vps.qing.data.repository.CartCalculator
import com.vps.qing.data.repository.CatalogParser
import com.vps.qing.data.repository.CatalogRepository
import com.vps.qing.data.repository.CheckoutParser
import com.vps.qing.data.repository.ConfigOption
import com.vps.qing.data.repository.GatewayOption
import com.vps.qing.data.repository.CouponItem
import com.vps.qing.data.repository.NoticeParagraph
import com.vps.qing.data.repository.PayDeadline
import com.vps.qing.data.repository.MfCloudCart
import com.vps.qing.data.repository.MfSelection
import com.vps.qing.data.repository.OptionType
import com.vps.qing.data.repository.PriceQuote
import com.vps.qing.data.repository.ProductConfig
import com.vps.qing.data.repository.ProductGroup
import com.vps.qing.data.repository.ProductInfo
import com.vps.qing.data.repository.CartItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 自定义配置页的状态（独立于主列表，便于整体打开/关闭）。 */
data class ConfigSheetState(
    val product: ProductInfo,
    val loading: Boolean = true,
    val config: ProductConfig? = null,
    /** optionId -> 选中的 subId（选择型）或数量字符串（数量型 / 开关）——通用商品。 */
    val selections: Map<String, String> = emptyMap(),
    /** mf_cloud（大带宽等套餐型）商品的区域/线路/CPU/内存/磁盘/带宽/镜像选择。 */
    val mf: MfSelection? = null,
    val cycleId: String? = null,
    val qty: Int = 1,
    /** 选中周期的完整实时算价（已含数量与折扣，UI 直接展示，不再乘 qty）。 */
    val quote: PriceQuote? = null,
    /** 每个周期的实时算价（单份口径），切换选配后同步刷新。 */
    val cycleQuotes: Map<String, PriceQuote> = emptyMap(),
    /** 商品详情指标（计费方式 / 起价 / 模块），来自 GET /product/{id}。 */
    val indicators: ProductInfo? = null,
    /** v1.4.27 购前须知：SSR 专区页「公告·购买前必读」结构化段落（替代错误的全站公告）。 */
    val prePurchaseNotices: List<NoticeParagraph> = emptyList(),
    /** 区域机器须知（商品 description 提取）。 */
    val regionNotices: List<String> = emptyList(),
    val coupons: List<CouponItem> = emptyList(),
    val couponsLoading: Boolean = false,
    val quoting: Boolean = false,
    /** 实时算价失败提示；此时不展示任何「看起来像成交价」的数字。 */
    val quoteError: String? = null,
    val adding: Boolean = false,
    val error: String? = null
) {
    /** 选中周期应展示的应付总额；算价失败返回 null，由 UI 显示占位。 */
    val displayTotal: Double? get() = quote?.total
}

/**
 * 原生结算支付状态机（v1.4.8）：
 * GATEWAY（选支付方式）→ PAYING（已拉起支付页，轮询中）→ SUCCESS / 失败回到 GATEWAY。
 */
data class CheckoutState(
    val orderId: String? = null,
    val amount: String? = null,
    val hostIds: List<String> = emptyList(),
    /** 订单关联商品名（绑定显示用，来自购物车或订单明细）。 */
    val productNames: List<String> = emptyList(),
    /** 已选中但未支付的行下标（结算来源）。 */
    val positions: List<Int> = emptyList(),
    val gateways: List<GatewayOption> = emptyList(),
    val gatewaysLoading: Boolean = false,
    /** v1.4.17：实时拉取 GET /gateway 失败时的提示，可手动重试。 */
    val gatewayError: String? = null,
    /** 当前选中的网关（站点原始对象）：等待支付页文案与图标全部由它驱动，禁止硬编码。 */
    val selectedGateway: GatewayOption? = null,
    val paying: Boolean = false,
    /** 「我已完成支付」查询中：按钮进入禁用+加载态，给出回应式反馈。 */
    val checking: Boolean = false,
    val payUrl: String? = null,
    /** v1.4.18：站点返回的原始支付 HTML（网页端 `#payBox` 同款内容）。 */
    val payHtml: String = "",
    /** v1.4.18：站点支付按钮文案（如「立即支付」），与网页端保持一致。 */
    val payButtonLabel: String = "立即支付",
    /** v1.4.18：站点 HTML 里真实存在的二维码内容；null 表示站点没给二维码，App 绝不伪造。 */
    val qrContent: String? = null,
    /** v1.4.18：站点 HTML 里真实存在的二维码图片（data:image / http）。 */
    val qrImageUrl: String? = null,
    /** v1.4.18：订单真实支付有效期，来自 GET /order/{id}。 */
    val deadline: PayDeadline? = null,
    /** v1.4.18：「我已完成支付」校验后的明确反馈（成功 / 未支付 / 查询失败）。 */
    val checkMessage: String? = null,
    /** 反馈是否为成功态，决定提示条颜色与图标。 */
    val checkSuccess: Boolean = false,
    /** 支付有效期已耗尽。 */
    val expired: Boolean = false,
    val paid: Boolean = false,
    val error: String? = null
) {
    val stage: String
        get() = when {
            paid -> "SUCCESS"
            payUrl != null || payHtml.isNotBlank() -> "PAYING"
            else -> "GATEWAY"
        }
}

/** 「我已完成支付」单次校验结果：三态，未支付与查询失败必须区分提示。 */
private sealed interface PayCheck {
    object Paid : PayCheck
    object Unpaid : PayCheck
    data class Failed(val msg: String) : PayCheck
}

data class ShopUiState(
    /** 顶栏：0 = 采购页，1 = 购物车。 */
    val tab: Int = 0,
    /** 侧边栏是否展开（展开显示完整分类名，收起显示缩略）。 */
    val sidebarExpanded: Boolean = true,
    val groupsLoading: Boolean = true,
    val firstGroups: List<ProductGroup> = emptyList(),
    /** 一级分组 id → 该分组下的二级分类，一次并发拉全量并缓存。 */
    val secondGroups: Map<String, List<ProductGroup>> = emptyMap(),
    val selectedFirstId: String? = null,
    val selectedSecondId: String? = null,
    /** 手风琴：允许多个一级分类同时展开（v1.4.3 由单选改多选）。 */
    val expandedFirstIds: Set<String> = emptySet(),
    /** 「全部商品」快捷入口：忽略分类过滤。 */
    val showAll: Boolean = false,
    /** 商品/分类搜索关键词（匹配名称与配置指标）。 */
    val searchQuery: String = "",
    /** 静默刷新进行中（下拉刷新指示器）。 */
    val refreshing: Boolean = false,
    val productsLoading: Boolean = false,
    /** 站点全量商品（分类在本地过滤，因为 /product 忽略 fpg_id/spg_id）。 */
    val allProducts: List<ProductInfo> = emptyList(),
    val error: String? = null,
    /** 站点云端购物车（真实入云）。 */
    val cart: List<CartItem> = emptyList(),
    val cartLoading: Boolean = false,
    val cartSynced: Boolean = false,
    /** 自定义配置页；null = 不显示。 */
    val configSheet: ConfigSheetState? = null,
    /** 原生结算支付面板（v1.4.8）；null = 不显示。 */
    val checkout: CheckoutState? = null,
    /** 一次性提示（如「已加入云端购物车」）。 */
    val toast: String? = null
) {
    val cartCount: Int get() = CartCalculator.count(cart)
    val cartTotal: Double get() = CartCalculator.total(cart)
    val currentSeconds: List<ProductGroup>
        get() = selectedFirstId?.let { secondGroups[it] } ?: emptyList()

    /**
     * 当前分类应展示的商品：按商品自带的分组 id 本地过滤 + 关键词搜索。
     * 「全部商品」或搜索态忽略分类限制，避免用户在某分类下搜不到其它分类的商品。
     */
    val visibleProducts: List<ProductInfo>
        get() {
            val base = if (showAll || searchQuery.isNotBlank()) allProducts
            else CatalogParser.byGroup(allProducts, selectedFirstId, selectedSecondId)
            if (searchQuery.isBlank()) return base
            val q = searchQuery.trim()
            return base.filter { p ->
                p.name.contains(q, true) ||
                    p.groupFirstName.orEmpty().contains(q, true) ||
                    p.groupSecondName.orEmpty().contains(q, true) ||
                    p.specs.any { it.contains(q, true) }
            }
        }

    /** 二级分类下的商品数（侧栏角标，避免用户点进空分类）。 */
    fun countForSecond(id: String): Int =
        allProducts.count { it.groupSecondId == id }

    /** 一级分类下的商品数。 */
    fun countForFirst(id: String): Int =
        allProducts.count { it.groupFirstId == id }
}

/**
 * 选购页状态：
 *  - 商品/分类并发拉取并带 TTL 节流，手动刷新与下拉刷新强制绕过缓存（v1.4.3）；
 *  - 加购前弹自定义配置，选完配置/周期/数量后真实 POST /cart 到站点云端；
 *  - 配置变化时对所有周期并发实时算价，用请求序号 + 配置签名双重防竞态；
 *  - 购物车以 GET /cart 的云端数据为准，改数量 / 删除也走站点接口。
 * ViewModel 挂在 MAIN 导航节点，切底栏 Tab 不重建，购物车与侧边栏状态不丢。
 */
class ShopViewModel(private val catalog: CatalogRepository) : ViewModel() {

    private val _ui = MutableStateFlow(ShopUiState())
    val ui: StateFlow<ShopUiState> = _ui.asStateFlow()

    private var quoteJob: Job? = null
    /** 每次算价请求的自增序号：只有最新一次请求可以写回价格。 */
    private var quoteSeq = 0L
    private var lastCatalogSyncMs = 0L
    /** 支付状态轮询任务（v1.4.8）。 */
    private var payPollJob: Job? = null
    /** v1.4.20：购物车同步任务与序号——连续删除/改数量时，只让最新一次回源结果写回，避免旧响应覆盖。 */
    private var cartSyncJob: Job? = null
    private var cartSyncSeq = 0L

    companion object {
        /** 目录静默刷新的最小间隔，避免每次切 Tab 都打网络。 */
        private const val CATALOG_TTL_MS = 60_000L
        /** 配置变化后的算价防抖，避免连续点选产生请求风暴。 */
        private const val QUOTE_DEBOUNCE_MS = 120L
        /**
         * 官方 payDialog.js：轮询间隔 2000ms、`time = 300000`（5 分钟）后停止。
         * 这是**轮询上限**，不是订单有效期；订单真实有效期来自
         * GET /order/{id} 的 unpaid_timeout / remain_pay_time。
         * v1.4.18 修正旧版写死的「最长等待 10 分钟」。
         */
        private const val PAY_POLL_INTERVAL_MS = 2_000L
        private const val PAY_POLL_MAX_MS = 5 * 60_000L
    }

    init {
        loadGroups()
        syncCart()
    }

    /**
     * 拉取分类与全量商品。
     * v1.4.3：
     *  - silent=true 为静默刷新（保留旧数据展示，成功后原地替换，不打断浏览）；
     *  - 带 TTL 节流，force=true（手动/下拉刷新）强制绕过；
     *  - 一次性并发拉取全部一级分类的二级分类，侧栏展开任意分类都无需再等待网络；
     *  - 站点侧删除分类导致旧选中失效时自动回退到可用的分类。
     */
    fun loadGroups(silent: Boolean = false, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (silent && !force && now - lastCatalogSyncMs < CATALOG_TTL_MS &&
            _ui.value.allProducts.isNotEmpty()
        ) return
        viewModelScope.launch {
            if (!silent) {
                _ui.update { it.copy(groupsLoading = true, error = null, productsLoading = true) }
            } else if (force) {
                _ui.update { it.copy(refreshing = true, error = null) }
            }
            val groupsCall = async { catalog.firstGroups() }
            val productsCall = async { catalog.allProducts() }

            val groups = when (val r = groupsCall.await()) {
                is ApiResult.Success -> r.data
                is ApiResult.Failure -> { reportError(silent, "[${r.code}] ${r.msg}"); null }
                is ApiResult.Error -> { reportError(silent, r.throwable.message ?: "分类加载失败"); null }
            }
            val all = when (val r = productsCall.await()) {
                is ApiResult.Success -> r.data
                is ApiResult.Failure -> { reportError(silent, "[${r.code}] ${r.msg}"); null }
                is ApiResult.Error -> { reportError(silent, r.throwable.message ?: "商品加载失败"); null }
            }

            // 并发补齐所有一级分类的二级分类（站点二级接口按一级 id 查询）
            val firstIds = (groups ?: _ui.value.firstGroups).map { it.id }
            val secondCalls = firstIds.associateWith { id -> async { catalog.secondGroups(id) } }
            val seconds = secondCalls.mapNotNull { (id, deferred) ->
                (deferred.await() as? ApiResult.Success)?.data?.let { id to it }
            }.toMap()

            // 静默且全部失败：保持旧数据，不闪错误
            if (silent && groups == null && all == null && seconds.isEmpty()) {
                _ui.update { it.copy(refreshing = false) }
                return@launch
            }
            if (groups != null || all != null) lastCatalogSyncMs = System.currentTimeMillis()

            _ui.update { st ->
                st.copy(
                    groupsLoading = false,
                    productsLoading = false,
                    refreshing = false,
                    firstGroups = groups ?: st.firstGroups,
                    allProducts = all ?: st.allProducts,
                    secondGroups = if (seconds.isNotEmpty()) st.secondGroups + seconds else st.secondGroups,
                    expandedFirstIds = if (st.expandedFirstIds.isEmpty() && st.selectedFirstId != null)
                        setOf(st.selectedFirstId!!) else st.expandedFirstIds
                )
            }
            if (!silent && groups == null && all == null) return@launch

            // 校正选中项：站点删除/调整分类后旧选中可能失效
            val st = _ui.value
            val selectedFirstValid = st.selectedFirstId?.let { f ->
                st.firstGroups.any { it.id == f }
            } ?: false
            val selectedSecondValid = st.selectedSecondId?.let { s ->
                (st.selectedFirstId?.let { st.secondGroups[it] } ?: emptyList())
                    .any { it.id == s }
            } ?: true
            when {
                st.showAll || st.searchQuery.isNotBlank() -> Unit
                st.selectedFirstId == null || !selectedFirstValid ->
                    st.firstGroups.firstOrNull()?.let { selectFirst(it.id) }
                !selectedSecondValid -> {
                    val secs = st.secondGroups[st.selectedFirstId].orEmpty()
                    val fallback = secs.firstOrNull { s -> st.allProducts.any { it.groupSecondId == s.id } }
                        ?: secs.firstOrNull()
                    _ui.update { it.copy(selectedSecondId = fallback?.id) }
                }
            }
        }
    }

    private fun reportError(silent: Boolean, msg: String) {
        if (!silent) _ui.update { it.copy(error = msg) }
    }

    /** 点一级分类：选中该产品类型并展开它（不影响其它已展开的分类）。 */
    fun selectFirst(id: String) {
        _ui.update {
            it.copy(
                selectedFirstId = id, selectedSecondId = null, showAll = false,
                searchQuery = "", error = null,
                expandedFirstIds = it.expandedFirstIds + id
            )
        }
        pickDefaultSecond(id, _ui.value.secondGroups[id].orEmpty())
    }

    /** 手风琴开关：点一级分类右侧的 chevron 只折叠/展开，不改变选中项。 */
    fun toggleFirstExpanded(id: String) {
        _ui.update {
            val next = if (id in it.expandedFirstIds) it.expandedFirstIds - id
            else it.expandedFirstIds + id
            it.copy(expandedFirstIds = next)
        }
    }

    /** 「全部商品」：忽略分类过滤。 */
    fun showAllProducts() {
        _ui.update {
            it.copy(showAll = true, selectedFirstId = null, selectedSecondId = null, searchQuery = "", error = null)
        }
    }

    fun setSearchQuery(query: String) {
        _ui.update { it.copy(searchQuery = query, error = null) }
    }

    /** 优先选中有商品的二级分类，避免默认落在空分类上。 */
    private fun pickDefaultSecond(firstId: String, seconds: List<ProductGroup>) {
        if (seconds.isEmpty()) return
        val all = _ui.value.allProducts
        val withProducts = seconds.firstOrNull { s -> all.any { it.groupSecondId == s.id } }
        val target = withProducts ?: seconds.firstOrNull() ?: return
        _ui.update { it.copy(selectedFirstId = firstId, selectedSecondId = target.id, showAll = false) }
    }

    fun selectSecond(id: String) {
        _ui.update {
            it.copy(selectedSecondId = id, showAll = false, searchQuery = "", error = null)
        }
    }

    /** 手动刷新：强制静默重拉分类/商品/全部二级分类 + 购物车。 */
    fun refresh() {
        // v1.4.4：下拉刷新要真正回源，先清掉统一缓存层的 L1/L2，
        // 否则 TTL 未过期时会直接返回缓存，用户感觉「刷新没反应」。
        AppContainer.invalidateCache()
        loadGroups(silent = true, force = true)
        syncCart()
    }

    fun setTab(tab: Int) {
        _ui.update { it.copy(tab = tab) }
        if (tab == 1) syncCart()
        // 回到选购页时按 TTL 静默同步站点最新分类与商品（App 存活期间上新也能出现）
        if (tab == 0 && !_ui.value.groupsLoading) loadGroups(silent = true)
    }

    fun toggleSidebar() = _ui.update { it.copy(sidebarExpanded = !it.sidebarExpanded) }

    // ---- 自定义配置页 ----

    fun openConfig(product: ProductInfo) {
        quoteJob?.cancel()
        quoteSeq++
        _ui.update {
            it.copy(configSheet = ConfigSheetState(
                product = product,
                // 区域机器须知来自商品自身 description（列表已带回），无需额外请求
                regionNotices = CatalogParser.regionNoticesFromHtml(product.descriptionHtml),
                couponsLoading = true
            ))
        }
        viewModelScope.launch {
            // 配置定义与商品指标并发拉取；配置强制刷新，避免站点改价后仍用旧缓存
            val cfgCall = async { catalog.productConfig(product.id, forceRefresh = true) }
            val infoCall = async { catalog.productIndicators(product.id) }
            // v1.4.27：购前须知改为抓取专区页 SSR「公告·购买前必读」；优惠券并发拉取，失败静默降级
            val noticeCall = async {
                catalog.prePurchaseNotices(
                    product.groupFirstId.orEmpty(), product.groupSecondId.orEmpty()
                )
            }
            val couponCall = async { catalog.coupons() }
            when (val r = cfgCall.await()) {
                is ApiResult.Success -> {
                    val cfg = r.data
                    val indicators = (infoCall.await() as? ApiResult.Success)?.data
                    if (cfg.mf != null) {
                        val sel = MfSelection.default(cfg.mf)
                        _ui.update {
                            it.copy(configSheet = it.configSheet?.copy(
                                loading = false, config = cfg, mf = sel,
                                cycleId = sel?.durationId, indicators = indicators
                            ))
                        }
                    } else {
                        val defaults = buildMap {
                            cfg.options.forEach { opt ->
                                val v = defaultValue(opt)
                                if (v != null) put(opt.id, v)
                            }
                        }
                        _ui.update {
                            it.copy(configSheet = it.configSheet?.copy(
                                loading = false, config = cfg, selections = defaults,
                                cycleId = cfg.defaultCycleId, indicators = indicators
                            ))
                        }
                    }
                    recalcQuote()
                }
                is ApiResult.Failure -> _ui.update {
                    it.copy(configSheet = it.configSheet?.copy(loading = false, error = "[${r.code}] ${r.msg}"))
                }
                is ApiResult.Error -> _ui.update {
                    it.copy(configSheet = it.configSheet?.copy(
                        loading = false, error = r.throwable.message ?: "配置加载失败"
                    ))
                }
            }
            // v1.4.27：辅助信息回写。用商品 id 守卫，防止用户快速切换商品时旧响应覆盖新页面。
            val notices = when (val nr = noticeCall.await()) {
                is ApiResult.Success -> nr.data
                else -> emptyList()
            }
            val coupons = when (val cr = couponCall.await()) {
                is ApiResult.Success -> cr.data
                else -> emptyList()
            }
            _ui.update { st ->
                val sheet = st.configSheet
                if (sheet == null || sheet.product.id != product.id) st
                else st.copy(configSheet = sheet.copy(
                    prePurchaseNotices = notices, coupons = coupons, couponsLoading = false
                ))
            }
        }
    }

    /** mf_cloud 配置面板选择更新：周期价随档位变化，由 recalcQuote 统一重拉。 */
    fun updateMfSelection(next: MfSelection) {
        _ui.update {
            val sheet = it.configSheet ?: return@update it
            it.copy(configSheet = sheet.copy(mf = next, error = null))
        }
        recalcQuote()
    }

    fun selectMfImage(imageId: Int) {
        val current = _ui.value.configSheet?.mf ?: return
        updateMfSelection(current.copy(imageId = imageId))
    }

    private fun defaultValue(opt: ConfigOption): String? = when {
        OptionType.isChoice(opt.type) -> opt.defaultSubId
        opt.type == OptionType.YES_NO -> "0"
        opt.type == OptionType.QUANTITY || opt.type == OptionType.QUANTITY_RANGE ->
            opt.minValue.toString()
        else -> opt.defaultSubId
    }

    fun selectOption(optionId: String, value: String) {
        _ui.update {
            it.copy(configSheet = it.configSheet?.copy(
                selections = it.configSheet.selections + (optionId to value),
                error = null
            ))
        }
        recalcQuote()
    }

    /** 切换周期：周期价格已全量算好，只需按实际数量重算主价格栏。 */
    fun selectCycle(cycleId: String) {
        _ui.update { it.copy(configSheet = it.configSheet?.copy(cycleId = cycleId)) }
        recalcQuote(recalcCycles = false)
    }

    fun changeConfigQty(delta: Int) {
        _ui.update {
            it.copy(configSheet = it.configSheet?.copy(
                qty = (it.configSheet.qty + delta).coerceIn(1, 99)
            ))
        }
        recalcQuote()
    }

    fun dismissConfig() {
        quoteJob?.cancel()
        quoteSeq++
        _ui.update { it.copy(configSheet = null) }
    }

    /** 配置签名：保证写回的价格与当前界面上的配置严格对应。 */
    private fun configKey(sheet: ConfigSheetState): String = buildList {
        add(sheet.product.id)
        add(sheet.cycleId.orEmpty())
        add(sheet.qty.toString())
        sheet.selections.toSortedMap().forEach { (k, v) -> add("$k=$v") }
        sheet.mf?.let { add(it.toString()) }
    }.joinToString("|")

    /** 通用商品：遍历完整 options（含隐藏项）并用默认值补齐，避免提交配置少于站点要求。 */
    private fun genericSelections(sheet: ConfigSheetState): List<CartBodies.Selection> =
        sheet.config?.options.orEmpty().mapNotNull { opt ->
            val value = sheet.selections[opt.id] ?: defaultValue(opt) ?: return@mapNotNull null
            CartBodies.Selection(opt.id, value, OptionType.needsArray(opt.type))
        }

    /**
     * v1.4.15 算价专用 body（与加购 body 分离，这是「价格待确认」的根因修复）。
     * 站点官方 mf_cloud.js 的 calcPrice 用 POST /product/{id}/config_option，body 为
     * {id, config_options:{...完整平铺配置, promo_code, event_promotion}, qty}，
     * 即 [MfCloudCart.calcBody]；此前 App 误把加购用的 cartBody（product_id +
     * activeName/login_way）拿去算价，站点读不到 id 直接 400「商品不存在」。
     * 通用商品算价与加购共用 [CartBodies.build]（实测站点接受同构 body）。
     */
    private fun buildCalcBody(sheet: ConfigSheetState, cycleId: String?, qty: Int): JsonObject {
        val cfg = sheet.config
        if (cfg?.mf != null && sheet.mf != null) {
            return MfCloudCart.calcBody(
                sheet.mf.copy(durationId = cycleId), sheet.product.id, qty, cfg.mf.networkType
            )
        }
        return CartBodies.build(sheet.product.id, genericSelections(sheet), cycleId, qty)
    }

    /** 加购专用 body：mf_cloud 用 cartBody（product_id + activeName/login_way），周期显式取当前选中。 */
    private fun buildCartBody(sheet: ConfigSheetState, cycleId: String?, qty: Int): JsonObject {
        val cfg = sheet.config
        if (cfg?.mf != null && sheet.mf != null) {
            return MfCloudCart.cartBody(
                sheet.mf.copy(durationId = cycleId), sheet.product.id, qty, cfg.mf.networkType
            )
        }
        return CartBodies.build(sheet.product.id, genericSelections(sheet), cycleId, qty)
    }

    /**
     * 站点实时标价兜底（v1.4.15）：mf_cloud 套餐型商品的算价接口实测需登录态 + 完整推荐配置，
     * App 现有配置模型无法稳定拿到成交价。此时用 /product 列表返回的官方标价（每次刷新
     * 实拉、真实且实时）构造估算价，UI 标注「站点标价 · 以结算价为准」，杜绝空白「价格待确认」。
     */
    private fun estimatedQuote(sheet: ConfigSheetState): PriceQuote? {
        val listPrice = sheet.product.price.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
        val cycleName = sheet.config?.cycles?.firstOrNull { it.id == sheet.cycleId }?.name
            ?.takeIf { it.isNotBlank() } ?: sheet.product.cycle
        return PriceQuote(
            price = listPrice * sheet.qty,
            unitPrice = listPrice,
            basePrice = listPrice,
            billingCycle = cycleName,
            priceTotal = listPrice * sheet.qty,
            isEstimated = true
        )
    }

    /**
     * 实时算价（v1.4.3 重写）。
     *
     * - 切换任意配置 / 数量：对所有付费周期按单份口径并发算价（周期胶囊同步刷新），
     *   并对选中周期按实际数量再算一次作为应付总额；
     * - 切换周期：只重算主价格，周期胶囊沿用已算好的价格；
     * - 请求序号 + 配置签名双重校验：快速切换时旧响应一律丢弃，杜绝价格与配置错配；
     * - 算价失败不拿 custom_cycles 原价冒充实时价，只给出明确提示。
     */
    private fun recalcQuote(recalcCycles: Boolean = true) {
        val initial = _ui.value.configSheet ?: return
        val cfg = initial.config ?: return
        if (cfg.requiresWebOrder) return
        if (cfg.mf != null && initial.mf == null) return
        val seq = ++quoteSeq
        quoteJob?.cancel()
        quoteJob = viewModelScope.launch {
            _ui.update {
                it.copy(configSheet = it.configSheet?.copy(quoting = true, quoteError = null))
            }
            delay(QUOTE_DEBOUNCE_MS)
            if (seq != quoteSeq) return@launch

            // mf_cloud 的周期/价格随 CPU/内存/带宽档位变化，配置变化时重拉周期列表
            if (cfg.mf != null && recalcCycles) {
                val sel = _ui.value.configSheet?.mf
                if (sel != null) {
                    when (val dr = catalog.mfDuration(initial.product.id, sel)) {
                        is ApiResult.Success -> {
                            if (seq != quoteSeq) return@launch
                            // v1.4.15：匿名/配置不全时 duration 会返回全 0 的周期价，
                            // 用 /product 的官方实时标价给默认周期兜底，周期胶囊不再显示 ￥0.00。
                            val listPrice = initial.product.price.toDoubleOrNull() ?: 0.0
                            val cycles = if (dr.data.isNotEmpty() && dr.data.all { it.amount <= 0.0 } && listPrice > 0.0) {
                                val defaultIdx = dr.data.indexOfFirst { it.isDefault }.coerceAtLeast(0)
                                dr.data.mapIndexed { idx, c ->
                                    if (idx == defaultIdx) c.copy(amount = listPrice) else c
                                }
                            } else dr.data
                            val keep = _ui.value.configSheet?.cycleId
                                ?.takeIf { cid -> cycles.any { it.id == cid } }
                            val chosen = keep
                                ?: (cycles.firstOrNull { it.isDefault } ?: cycles.firstOrNull())?.id
                            _ui.update { st ->
                                st.copy(configSheet = st.configSheet?.copy(
                                    config = st.configSheet.config?.copy(cycles = cycles),
                                    cycleId = chosen,
                                    mf = st.configSheet.mf?.copy(durationId = chosen)
                                ))
                            }
                        }
                        else -> Unit
                    }
                }
            }

            val current = _ui.value.configSheet ?: return@launch
            val currentCfg = current.config ?: return@launch
            val key = configKey(current)
            val cycleIds = currentCfg.cycles.map { it.id }
                .ifEmpty { listOfNotNull(current.cycleId) }
            val unitBodies = if (recalcCycles) {
                cycleIds.associateWith { cid -> buildCalcBody(current, cid, 1) }
            } else emptyMap()
            val mainBody = buildCalcBody(current, current.cycleId, current.qty)

            val cycleQuotes = if (recalcCycles) {
                catalog.calcCycleQuotes(current.product.id, unitBodies)
            } else current.cycleQuotes
            // v1.4.15：算价失败重试一次（短退避），提升弱网下的稳定性
            var main = catalog.calcPrice(current.product.id, mainBody)
            if (main !is ApiResult.Success) {
                if (seq != quoteSeq) return@launch
                delay(180L)
                main = catalog.calcPrice(current.product.id, mainBody)
            }

            // 期间配置又变了 / 已有更新请求：丢弃本次结果
            if (seq != quoteSeq) return@launch
            val live = _ui.value.configSheet ?: return@launch
            if (configKey(live) != key) return@launch

            // 算价成功用站点成交价；失败回退到 /product 的官方实时标价（真实、实时），
            // UI 会标注「站点标价 · 以结算价为准」，不再出现空白「价格待确认」。
            val successQuote = (main as? ApiResult.Success)?.data
            val quote = successQuote ?: estimatedQuote(current)
            _ui.update { st ->
                val sheet = st.configSheet ?: return@update st
                st.copy(configSheet = sheet.copy(
                    quoting = false,
                    quote = quote,
                    cycleQuotes = if (recalcCycles) cycleQuotes else sheet.cycleQuotes,
                    quoteError = if (quote == null) "实时价格获取失败，以站点结算价为准" else null
                ))
            }
        }
    }

    /** 真实加入站点购物车：POST /cart → 成功后重新拉云端购物车覆盖本地。 */
    fun confirmAddToCart() {
        val sheet = _ui.value.configSheet ?: return
        val cfg = sheet.config
        if (cfg == null || cfg.requiresWebOrder) return
        if (cfg.mf != null && sheet.mf == null) return
        viewModelScope.launch {
            _ui.update { it.copy(configSheet = it.configSheet?.copy(adding = true, error = null)) }
            // v1.4.15：加购用 cartBody（product_id + activeName/login_way），与算价 body 分离
            val body = buildCartBody(sheet, sheet.cycleId, sheet.qty)
            when (val r = catalog.addToCart(body)) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(configSheet = null, toast = "已加入站点购物车") }
                    syncCart()
                }
                is ApiResult.Failure -> _ui.update {
                    it.copy(configSheet = it.configSheet?.copy(adding = false, error = "[${r.code}] ${r.msg}"))
                }
                is ApiResult.Error -> _ui.update {
                    it.copy(configSheet = it.configSheet?.copy(
                        adding = false, error = r.throwable.message ?: "加入购物车失败"
                    ))
                }
            }
        }
    }

    // ---- 云端购物车 ----

    fun syncCart() {
        cartSyncJob?.cancel()
        val seq = ++cartSyncSeq
        cartSyncJob = viewModelScope.launch {
            _ui.update { it.copy(cartLoading = true) }
            when (val r = catalog.cloudCart()) {
                is ApiResult.Success -> _ui.update {
                    if (seq != cartSyncSeq) it else it.copy(cartLoading = false, cartSynced = true, cart = r.data)
                }
                is ApiResult.Failure -> _ui.update {
                    if (seq != cartSyncSeq) it else it.copy(cartLoading = false, cartSynced = true, error = "[${r.code}] ${r.msg}")
                }
                is ApiResult.Error -> _ui.update {
                    if (seq != cartSyncSeq) it else it.copy(cartLoading = false, cartSynced = true)
                }
            }
        }
    }

    /**
     * v1.4.20：移除/改数量的即时反馈。
     * 先按 key 乐观更新本地列表（数量归零即移除），让角标、金额、列表在同一帧刷新；
     * 随后以站点为准回源 [syncCart]。网络失败时回滚到操作前快照并给出提示。
     */
    private fun mutateCartOptimistically(
        key: String,
        transform: (CartItem) -> CartItem?
    ): List<CartItem> {
        val before = _ui.value.cart
        val after = before.mapNotNull { if (it.key == key) transform(it) else it }
        _ui.update { it.copy(cart = after) }
        return before
    }

    // ---- 原生结算支付（v1.4.8，协议对齐 route/home.php）----

    /**
     * 发起结算：结算购物车里所有云端行（position>=0），
     * 成功后拉支付方式列表进入选方式阶段。
     * 结算动作会让服务端删除对应购物车行，故成功即失效购物车缓存并重新同步，
     * 避免「已去支付但购物车内仍显示已删商品」的数据时效性问题（v1.4.9）。
     */
    fun startCheckout() {
        val positions = _ui.value.cart.filter { it.position >= 0 }.map { it.position }
        if (positions.isEmpty()) {
            _ui.update { it.copy(toast = "购物车没有可结算的商品") }
            return
        }
        val names = _ui.value.cart.filter { it.position >= 0 }.map { it.productName }
        viewModelScope.launch {
            _ui.update {
                it.copy(checkout = CheckoutState(
                    positions = positions, productNames = names, gatewaysLoading = true
                ))
            }
            when (val r = catalog.settleCart(positions)) {
                is ApiResult.Success -> {
                    // 服务端已删除购物车行：失效缓存并回源同步，保证 UI 与云端一致
                    AppContainer.invalidateCache("cart")
                    syncCart()
                    // v1.4.17：结算完成后实时回源拉站点可用网关（清 gateway 缓存），
                    // 列表、名称、图标、提交参数全部来自站点，App 不写死任何一项。
                    val (gws, gwError) = loadGateways()
                    _ui.update {
                        it.copy(checkout = it.checkout?.copy(
                            orderId = r.data.orderId,
                            amount = r.data.amount,
                            hostIds = r.data.hostIds,
                            gateways = gws,
                            gatewaysLoading = false,
                            gatewayError = gwError
                        ))
                    }
                }
                is ApiResult.Failure -> _ui.update {
                    it.copy(checkout = null, toast = "[${r.code}] ${r.msg}")
                }
                is ApiResult.Error -> _ui.update {
                    it.copy(checkout = null, toast = r.throwable.message ?: "结算失败")
                }
            }
        }
    }

    /**
     * 针对「已存在但未支付」的订单继续支付（v1.4.9，订单详情页「继续支付」入口）。
     * 不复用购物车结算，而是直接进入选支付方式阶段（orderId/amount 已知），
     * 后续 payWith 直接 POST /pay（id=订单号, gateway），原生完成支付闭环。
     */
    fun startPayOrder(orderId: String, amount: String, productNames: List<String> = emptyList()) {
        if (orderId.isBlank()) return
        viewModelScope.launch {
            _ui.update {
                it.copy(checkout = CheckoutState(
                    orderId = orderId, amount = amount, productNames = productNames,
                    gatewaysLoading = true
                ))
            }
            val (gws, gwError) = loadGateways()
            _ui.update {
                it.copy(checkout = it.checkout?.copy(
                    gateways = gws, gatewaysLoading = false, gatewayError = gwError
                ))
            }
        }
    }

    /**
     * v1.4.17：实时拉取站点支付方式。先失效 gateway 的 L1/L2 缓存，
     * 站点新增 / 下线网关时，每次进入支付或点「重新获取」都看到最新选项。
     */
    private suspend fun loadGateways(): Pair<List<GatewayOption>, String?> {
        AppContainer.invalidateCache("gateway")
        return when (val r = catalog.gateways()) {
            is ApiResult.Success -> r.data to null
            is ApiResult.Failure -> emptyList<GatewayOption>() to "[${r.code}] ${r.msg}"
            is ApiResult.Error -> emptyList<GatewayOption>() to
                (r.throwable.message ?: "支付方式加载失败")
        }
    }

    /** 用户主动刷新支付方式（站点刚上线新通道时无需退出支付页）。 */
    fun refreshGateways() {
        val checkout = _ui.value.checkout ?: return
        if (checkout.gatewaysLoading || checkout.paying) return
        viewModelScope.launch {
            _ui.update {
                it.copy(checkout = it.checkout?.copy(gatewaysLoading = true, gatewayError = null))
            }
            val (gws, err) = loadGateways()
            _ui.update {
                it.copy(checkout = it.checkout?.copy(
                    gateways = gws, gatewaysLoading = false, gatewayError = err
                ))
            }
        }
    }

    /**
     * 选择支付方式并拉起支付（v1.4.18 全链路真实性修复）：
     *  - 余额全额抵扣：响应体顶层 code=Paid，直接成功；
     *  - 网关支付：保留站点原始 data.html、真实二维码内容/图片、收银台链接；
     *  - 有效期：立即回源拉 GET /order/{id}，不再写死「10 分钟」。
     * gateway.name 原样来自站点 GET /gateway，不做任何映射或白名单。
     */
    fun payWith(gateway: GatewayOption) {
        val checkout = _ui.value.checkout ?: return
        val orderId = checkout.orderId ?: return
        if (checkout.paying) return
        viewModelScope.launch {
            _ui.update {
                it.copy(checkout = it.checkout?.copy(
                    paying = true, error = null, selectedGateway = gateway,
                    checkMessage = null, expired = false
                ))
            }
            when (val r = catalog.payOrder(orderId, gateway.name)) {
                is ApiResult.Success -> {
                    val outcome = r.data
                    when {
                        outcome.paid -> markPaid(orderId)
                        outcome.payUrl != null || outcome.payHtml.isNotBlank() -> {
                            // order/{id} 默认有 5 分钟详情缓存，有效期必须强制回源
                            AppContainer.invalidateCache("order")
                            val deadline = (catalog.payDeadline(orderId) as? ApiResult.Success)?.data
                            _ui.update {
                                it.copy(checkout = it.checkout?.copy(
                                    paying = false,
                                    payUrl = outcome.payUrl,
                                    payHtml = outcome.payHtml,
                                    payButtonLabel = outcome.payButtonLabel,
                                    qrContent = outcome.qrContent,
                                    qrImageUrl = outcome.qrImageUrl,
                                    deadline = deadline,
                                    checkMessage = null
                                ))
                            }
                            startPayPolling(orderId, deadline ?: PayDeadline())
                        }
                        else -> _ui.update {
                            it.copy(checkout = it.checkout?.copy(
                                paying = false,
                                error = "站点未返回支付入口（data.html 为空），请改用其他支付方式或联系客服"
                            ))
                        }
                    }
                }
                is ApiResult.Failure -> _ui.update {
                    it.copy(checkout = it.checkout?.copy(paying = false, error = "[${r.code}] ${r.msg}"))
                }
                is ApiResult.Error -> _ui.update {
                    it.copy(checkout = it.checkout?.copy(paying = false, error = r.throwable.message ?: "发起支付失败"))
                }
            }
        }
    }

    /**
     * 「我已完成支付」：立即回源查一次顶层 code，并给出明确反馈。
     * 旧版只静默 checkOnce 后解除 loading，成功读不到 Paid、失败也没有任何提示，
     * 用户点了完全没反应（v1.4.18 用户实测反馈）。
     */
    fun checkPayNow() {
        val checkout = _ui.value.checkout ?: return
        val orderId = checkout.orderId ?: return
        if (checkout.checking) return
        viewModelScope.launch {
            _ui.update {
                it.copy(checkout = it.checkout?.copy(checking = true, checkMessage = null))
            }
            // 状态查询不能被缓存命中，保证时效
            AppContainer.invalidateCache("pay")
            when (val result = checkOnce(orderId)) {
                is PayCheck.Paid -> Unit // 已切到 SUCCESS 态，无需额外提示
                is PayCheck.Unpaid -> _ui.update {
                    it.copy(checkout = it.checkout?.copy(
                        checking = false, checkSuccess = false,
                        checkMessage = "暂未检测到支付成功，请确认已在收银台完成付款后再试"
                    ))
                }
                is PayCheck.Failed -> _ui.update {
                    it.copy(checkout = it.checkout?.copy(
                        checking = false, checkSuccess = false,
                        checkMessage = "支付状态查询失败：${result.msg}，请稍后重试"
                    ))
                }
            }
        }
    }

    /** 关闭结算面板：轮询停止（订单保留，可在订单列表继续支付）。 */
    fun closeCheckout() {
        payPollJob?.cancel()
        payPollJob = null
        _ui.update { it.copy(checkout = null) }
    }

    /**
     * 轮询上限由**订单真实有效期**决定：站点启用 unpaid_timeout 时按剩余秒数，
     * 否则用官方 payDialog 的 5 分钟轮询上限。到期仍未支付则明确标记超时。
     */
    private fun startPayPolling(orderId: String, deadline: PayDeadline) {
        payPollJob?.cancel()
        val startedAt = System.currentTimeMillis()
        val maxMs = if (deadline.hasTimeout && deadline.remainSeconds > 0) {
            deadline.remainSeconds * 1000L
        } else PAY_POLL_MAX_MS
        payPollJob = viewModelScope.launch {
            while (isActive && System.currentTimeMillis() - startedAt < maxMs) {
                delay(PAY_POLL_INTERVAL_MS)
                // 轮询期间的单次失败静默重试，不打扰用户；手动点「我已完成支付」才提示
                if (checkOnce(orderId) is PayCheck.Paid) return@launch
            }
            if (isActive && _ui.value.checkout?.paid != true) {
                _ui.update { it.copy(checkout = it.checkout?.copy(expired = true)) }
            }
        }
    }

    /**
     * 查一次支付状态（顶层 code）：Paid 写回成功态并刷新购物车；
     * Unpaid 与查询失败分别返回，供「我已完成支付」给出不同提示。
     */
    private suspend fun checkOnce(orderId: String): PayCheck = when (val r = catalog.payStatus(orderId)) {
        is ApiResult.Success -> if (r.data == "PAID") {
            markPaid(orderId)
            PayCheck.Paid
        } else PayCheck.Unpaid
        is ApiResult.Failure -> PayCheck.Failed("[${r.code}] ${r.msg}")
        is ApiResult.Error -> PayCheck.Failed(r.throwable.message ?: "网络异常")
    }

    private suspend fun markPaid(orderId: String) {
        payPollJob?.cancel()
        payPollJob = null
        _ui.update {
            it.copy(checkout = it.checkout?.copy(paid = true, payUrl = null, paying = false, error = null))
        }
        // v1.4.17：支付成功即时通知（是否真正发出由权限 / 开关 / 免打扰四道闸决定）
        runCatching {
            QingNotifier.orderPaid(
                AppContainer.appContext(), orderId, _ui.value.checkout?.amount.orEmpty()
            )
        }
        syncCart()
        AppContainer.invalidateCache("host")
        AppContainer.invalidateCache("order")
    }

    fun changeCartQty(item: CartItem, delta: Int) {
        if (item.position < 0) return
        val next = item.quantity + delta
        val before = mutateCartOptimistically(item.key) { cur ->
            val q = cur.quantity + delta
            cur.copy(quantity = q).takeIf { q > 0 }
        }
        viewModelScope.launch {
            val ok = if (next <= 0) {
                catalog.deleteCartLine(item.position) is ApiResult.Success
            } else {
                catalog.updateCartQty(item.position, next) is ApiResult.Success
            }
            if (ok) {
                // 站点为准回源（位置索引在删除后会重排，必须以 GET /cart 结果覆盖）
                syncCart()
            } else {
                _ui.update { it.copy(cart = before, toast = "操作失败，请重试") }
            }
        }
    }

    fun removeCartItem(item: CartItem) {
        if (item.position < 0) return
        val before = mutateCartOptimistically(item.key) { null }
        viewModelScope.launch {
            when (catalog.deleteCartLine(item.position)) {
                is ApiResult.Success -> syncCart()
                else -> _ui.update { it.copy(cart = before, toast = "删除失败，请重试") }
            }
        }
    }

    fun consumeToast() = _ui.update { it.copy(toast = null) }
}
