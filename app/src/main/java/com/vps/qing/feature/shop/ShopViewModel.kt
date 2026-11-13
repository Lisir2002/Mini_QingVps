package com.vps.qing.feature.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.vps.qing.core.AppContainer
import com.vps.qing.core.network.ApiResult
import com.vps.qing.data.repository.CartBodies
import com.vps.qing.data.repository.CartCalculator
import com.vps.qing.data.repository.CatalogParser
import com.vps.qing.data.repository.CatalogRepository
import com.vps.qing.data.repository.ConfigOption
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
    val quoting: Boolean = false,
    /** 实时算价失败提示；此时不展示任何「看起来像成交价」的数字。 */
    val quoteError: String? = null,
    val adding: Boolean = false,
    val error: String? = null
) {
    /** 选中周期应展示的应付总额；算价失败返回 null，由 UI 显示占位。 */
    val displayTotal: Double? get() = quote?.total
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

    companion object {
        /** 目录静默刷新的最小间隔，避免每次切 Tab 都打网络。 */
        private const val CATALOG_TTL_MS = 60_000L
        /** 配置变化后的算价防抖，避免连续点选产生请求风暴。 */
        private const val QUOTE_DEBOUNCE_MS = 120L
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
        _ui.update { it.copy(configSheet = ConfigSheetState(product = product)) }
        viewModelScope.launch {
            // 配置定义与商品指标并发拉取；配置强制刷新，避免站点改价后仍用旧缓存
            val cfgCall = async { catalog.productConfig(product.id, forceRefresh = true) }
            val infoCall = async { catalog.productIndicators(product.id) }
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

    /**
     * 组装算价/加购 body。
     * v1.4.3：遍历完整 options（含隐藏项）并用默认值补齐，避免提交的配置少于站点要求
     * 导致「配置对不上」；cycleId 可覆盖，用于逐周期算价。
     */
    private fun buildBody(sheet: ConfigSheetState, cycleId: String?, qty: Int): JsonObject {
        val cfg = sheet.config
        if (cfg?.mf != null && sheet.mf != null) {
            // v1.4.7 修复：加购必须用 cartBody（product_id + activeName/login_way 等），
            // 之前误用算价用的 calcBody（字段是 id + promo_code），站点读不到
            // product_id 直接报 [400]「请选择商品」。
            return MfCloudCart.cartBody(sheet.mf, sheet.product.id, qty)
        }
        val selections = cfg?.options.orEmpty().mapNotNull { opt ->
            val value = sheet.selections[opt.id] ?: defaultValue(opt) ?: return@mapNotNull null
            CartBodies.Selection(opt.id, value, OptionType.needsArray(opt.type))
        }
        return CartBodies.build(sheet.product.id, selections, cycleId, qty)
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
                            val cycles = dr.data
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
                cycleIds.associateWith { cid -> buildBody(current, cid, 1) }
            } else emptyMap()
            val mainBody = buildBody(current, current.cycleId, current.qty)

            val cycleQuotes = if (recalcCycles) {
                catalog.calcCycleQuotes(current.product.id, unitBodies)
            } else current.cycleQuotes
            val main = catalog.calcPrice(current.product.id, mainBody)

            // 期间配置又变了 / 已有更新请求：丢弃本次结果
            if (seq != quoteSeq) return@launch
            val live = _ui.value.configSheet ?: return@launch
            if (configKey(live) != key) return@launch

            val quote = (main as? ApiResult.Success)?.data
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
            val body = buildBody(sheet, sheet.cycleId, sheet.qty)
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
        viewModelScope.launch {
            _ui.update { it.copy(cartLoading = true) }
            when (val r = catalog.cloudCart()) {
                is ApiResult.Success -> _ui.update {
                    it.copy(cartLoading = false, cartSynced = true, cart = r.data)
                }
                is ApiResult.Failure -> _ui.update {
                    it.copy(cartLoading = false, cartSynced = true, error = "[${r.code}] ${r.msg}")
                }
                is ApiResult.Error -> _ui.update {
                    it.copy(cartLoading = false, cartSynced = true)
                }
            }
        }
    }

    fun changeCartQty(item: CartItem, delta: Int) {
        if (item.position < 0) return
        val next = item.quantity + delta
        viewModelScope.launch {
            if (next <= 0) {
                when (catalog.deleteCartLine(item.position)) {
                    is ApiResult.Success -> syncCart()
                    else -> _ui.update { it.copy(toast = "操作失败，请重试") }
                }
            } else {
                when (catalog.updateCartQty(item.position, next)) {
                    is ApiResult.Success -> syncCart()
                    else -> _ui.update { it.copy(toast = "操作失败，请重试") }
                }
            }
        }
    }

    fun removeCartItem(item: CartItem) {
        if (item.position < 0) return
        viewModelScope.launch {
            when (catalog.deleteCartLine(item.position)) {
                is ApiResult.Success -> syncCart()
                else -> _ui.update { it.copy(toast = "删除失败，请重试") }
            }
        }
    }

    fun consumeToast() = _ui.update { it.copy(toast = null) }
}
