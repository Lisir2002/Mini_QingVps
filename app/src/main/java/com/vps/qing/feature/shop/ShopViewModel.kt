package com.vps.qing.feature.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.vps.qing.core.network.ApiResult
import com.vps.qing.data.repository.CartBodies
import com.vps.qing.data.repository.CartCalculator
import com.vps.qing.data.repository.CatalogParser
import com.vps.qing.data.repository.CatalogRepository
import com.vps.qing.data.repository.ConfigOption
import com.vps.qing.data.repository.OptionType
import com.vps.qing.data.repository.PriceQuote
import com.vps.qing.data.repository.ProductConfig
import com.vps.qing.data.repository.ProductGroup
import com.vps.qing.data.repository.ProductInfo
import com.vps.qing.data.repository.CartItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 自定义配置弹窗的状态（独立于主列表，便于整体弹出/关闭）。 */
data class ConfigSheetState(
    val product: ProductInfo,
    val loading: Boolean = true,
    val config: ProductConfig? = null,
    /** optionId -> 选中的 subId（选择型）或数量字符串（数量型 / 开关）。 */
    val selections: Map<String, String> = emptyMap(),
    val cycleId: String? = null,
    val qty: Int = 1,
    val quote: PriceQuote? = null,
    val quoting: Boolean = false,
    val adding: Boolean = false,
    val error: String? = null
)

data class ShopUiState(
    /** 顶栏：0 = 采购页，1 = 购物车。 */
    val tab: Int = 0,
    /** 侧边栏是否展开（展开显示完整分类名，收起显示缩略）。 */
    val sidebarExpanded: Boolean = true,
    val groupsLoading: Boolean = true,
    val firstGroups: List<ProductGroup> = emptyList(),
    /** 一级分组 id → 该分组下的二级分类，按需加载并缓存。 */
    val secondGroups: Map<String, List<ProductGroup>> = emptyMap(),
    val selectedFirstId: String? = null,
    val selectedSecondId: String? = null,
    val productsLoading: Boolean = false,
    /** 站点全量商品（分类在本地过滤，因为 /product 忽略 fpg_id/spg_id）。 */
    val allProducts: List<ProductInfo> = emptyList(),
    val error: String? = null,
    /** 站点云端购物车（真实入云）。 */
    val cart: List<CartItem> = emptyList(),
    val cartLoading: Boolean = false,
    val cartSynced: Boolean = false,
    /** 自定义配置弹窗；null = 不显示。 */
    val configSheet: ConfigSheetState? = null,
    /** 一次性提示（如「已加入云端购物车」）。 */
    val toast: String? = null
) {
    val cartCount: Int get() = CartCalculator.count(cart)
    val cartTotal: Double get() = CartCalculator.total(cart)
    val currentSeconds: List<ProductGroup>
        get() = selectedFirstId?.let { secondGroups[it] } ?: emptyList()

    /** 当前分类应展示的商品：按商品自带的分组 id 本地过滤。 */
    val visibleProducts: List<ProductInfo>
        get() = CatalogParser.byGroup(allProducts, selectedFirstId, selectedSecondId)
}

/**
 * 选购页状态：
 *  - 商品一次性拉全量，切换分类走本地过滤（站点 /product 忽略分类参数，实测确认）；
 *  - 加购前弹自定义配置，选完配置/周期/数量后真实 POST /cart 到站点云端；
 *  - 购物车以 GET /cart 的云端数据为准，改数量 / 删除也走站点接口。
 * ViewModel 挂在 MAIN 导航节点，切底栏 Tab 不重建，购物车与侧边栏状态不丢。
 */
class ShopViewModel(private val catalog: CatalogRepository) : ViewModel() {

    private val _ui = MutableStateFlow(ShopUiState())
    val ui: StateFlow<ShopUiState> = _ui.asStateFlow()

    private var quoteJob: Job? = null

    init {
        loadGroups()
        syncCart()
    }

    fun loadGroups() {
        viewModelScope.launch {
            _ui.update { it.copy(groupsLoading = true, error = null, productsLoading = true) }
            // 分组与全量商品并行拉取
            val groupsResult = catalog.firstGroups()
            val productsResult = catalog.allProducts()
            val groups = when (groupsResult) {
                is ApiResult.Success -> groupsResult.data
                is ApiResult.Failure -> {
                    _ui.update { it.copy(error = "[${groupsResult.code}] ${groupsResult.msg}") }
                    emptyList()
                }
                is ApiResult.Error -> {
                    _ui.update { it.copy(error = groupsResult.throwable.message ?: "分类加载失败") }
                    emptyList()
                }
            }
            val all = when (productsResult) {
                is ApiResult.Success -> productsResult.data
                is ApiResult.Failure -> {
                    _ui.update { it.copy(error = "[${productsResult.code}] ${productsResult.msg}") }
                    emptyList()
                }
                is ApiResult.Error -> {
                    _ui.update { it.copy(error = productsResult.throwable.message ?: "商品加载失败") }
                    emptyList()
                }
            }
            _ui.update { it.copy(groupsLoading = false, productsLoading = false, firstGroups = groups, allProducts = all) }
            groups.firstOrNull()?.let { selectFirst(it.id) }
        }
    }

    fun selectFirst(id: String) {
        _ui.update { it.copy(selectedFirstId = id, selectedSecondId = null, error = null) }
        val cached = _ui.value.secondGroups[id]
        if (cached != null) {
            pickDefaultSecond(id, cached)
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(productsLoading = true) }
            when (val r = catalog.secondGroups(id)) {
                is ApiResult.Success -> {
                    _ui.update {
                        it.copy(
                            secondGroups = it.secondGroups + (id to r.data),
                            productsLoading = false
                        )
                    }
                    pickDefaultSecond(id, r.data)
                }
                is ApiResult.Failure -> _ui.update {
                    it.copy(productsLoading = false, error = "[${r.code}] ${r.msg}")
                }
                is ApiResult.Error -> _ui.update {
                    it.copy(productsLoading = false, error = r.throwable.message ?: "分类加载失败")
                }
            }
        }
    }

    /** 优先选中有商品的二级分类，避免默认落在空分类上。 */
    private fun pickDefaultSecond(firstId: String, seconds: List<ProductGroup>) {
        val all = _ui.value.allProducts
        val withProducts = seconds.firstOrNull { s -> all.any { it.groupSecondId == s.id } }
        val target = withProducts ?: seconds.firstOrNull() ?: return
        _ui.update { it.copy(selectedFirstId = firstId, selectedSecondId = target.id) }
    }

    fun selectSecond(id: String) {
        _ui.update { it.copy(selectedSecondId = id, error = null) }
    }

    fun refresh() {
        loadGroups()
        syncCart()
    }

    fun setTab(tab: Int) {
        _ui.update { it.copy(tab = tab) }
        if (tab == 1) syncCart()
    }

    fun toggleSidebar() = _ui.update { it.copy(sidebarExpanded = !it.sidebarExpanded) }

    // ---- 自定义配置弹窗 ----

    fun openConfig(product: ProductInfo) {
        _ui.update {
            it.copy(configSheet = ConfigSheetState(product = product))
        }
        viewModelScope.launch {
            when (val r = catalog.productConfig(product.id)) {
                is ApiResult.Success -> {
                    val cfg = r.data
                    val defaults = buildMap {
                        cfg.options.forEach { opt ->
                            val v = defaultValue(opt)
                            if (v != null) put(opt.id, v)
                        }
                    }
                    _ui.update {
                        it.copy(
                            configSheet = it.configSheet?.copy(
                                loading = false,
                                config = cfg,
                                selections = defaults,
                                cycleId = cfg.defaultCycleId
                            )
                        )
                    }
                    recalcQuote()
                }
                is ApiResult.Failure -> _ui.update {
                    it.copy(configSheet = it.configSheet?.copy(loading = false, error = "[${r.code}] ${r.msg}"))
                }
                is ApiResult.Error -> _ui.update {
                    it.copy(
                        configSheet = it.configSheet?.copy(
                            loading = false,
                            error = r.throwable.message ?: "配置加载失败"
                        )
                    )
                }
            }
        }
    }

    private fun defaultValue(opt: ConfigOption): String? = when {
        OptionType.isChoice(opt.type) -> opt.defaultSubId
        opt.type == OptionType.YES_NO -> "0"
        opt.type == OptionType.QUANTITY || opt.type == OptionType.QUANTITY_RANGE ->
            (opt.qtyMin.takeIf { it > 0 } ?: 1).toString()
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

    fun selectCycle(cycleId: String) {
        _ui.update { it.copy(configSheet = it.configSheet?.copy(cycleId = cycleId)) }
        recalcQuote()
    }

    fun changeConfigQty(delta: Int) {
        _ui.update {
            it.copy(configSheet = it.configSheet?.copy(qty = (it.configSheet.qty + delta).coerceIn(1, 99)))
        }
        recalcQuote()
    }

    fun dismissConfig() {
        quoteJob?.cancel()
        _ui.update { it.copy(configSheet = null) }
    }

    /** 组装算价/加购 body（纯数据，复用 CartBodies）。 */
    private fun buildBody(sheet: ConfigSheetState): JsonObject {
        val cfg = sheet.config
        val selections = sheet.selections.mapNotNull { (optionId, value) ->
            val opt = cfg?.options?.firstOrNull { it.id == optionId }
            val array = opt?.let { OptionType.needsArray(it.type) } ?: false
            CartBodies.Selection(optionId, value, array)
        }
        return CartBodies.build(sheet.product.id, selections, sheet.cycleId, sheet.qty)
    }

    /** 实时算价（配置变化时）。失败不打断加购，仅不显示价格。 */
    private fun recalcQuote() {
        val sheet = _ui.value.configSheet ?: return
        if (sheet.config == null || sheet.config.requiresWebOrder) return
        quoteJob?.cancel()
        quoteJob = viewModelScope.launch {
            _ui.update { it.copy(configSheet = it.configSheet?.copy(quoting = true)) }
            val body = buildBody(sheet)
            when (val r = catalog.calcPrice(sheet.product.id, body)) {
                is ApiResult.Success -> _ui.update {
                    it.copy(configSheet = it.configSheet?.copy(quoting = false, quote = r.data))
                }
                else -> _ui.update {
                    it.copy(configSheet = it.configSheet?.copy(quoting = false))
                }
            }
        }
    }

    /** 真实加入站点购物车：POST /cart → 成功后重新拉云端购物车覆盖本地。 */
    fun confirmAddToCart() {
        val sheet = _ui.value.configSheet ?: return
        val cfg = sheet.config
        // 套餐型商品（mf_cloud）无法原生自定义，由 UI 引导网页订购，这里不提交
        if (cfg == null || cfg.requiresWebOrder) return
        viewModelScope.launch {
            _ui.update { it.copy(configSheet = it.configSheet?.copy(adding = true, error = null)) }
            val body = buildBody(sheet)
            when (val r = catalog.addToCart(body)) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(configSheet = null, toast = "已加入站点购物车") }
                    syncCart()
                }
                is ApiResult.Failure -> _ui.update {
                    it.copy(configSheet = it.configSheet?.copy(adding = false, error = "[${r.code}] ${r.msg}"))
                }
                is ApiResult.Error -> _ui.update {
                    it.copy(
                        configSheet = it.configSheet?.copy(
                            adding = false,
                            error = r.throwable.message ?: "加入购物车失败"
                        )
                    )
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
