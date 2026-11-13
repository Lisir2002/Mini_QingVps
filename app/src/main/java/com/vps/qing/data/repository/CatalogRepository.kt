package com.vps.qing.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.vps.qing.core.network.ApiResult
import com.vps.qing.core.network.arrayAt
import com.vps.qing.core.network.number
import com.vps.qing.core.network.safeApiCall
import com.vps.qing.core.network.string
import com.vps.qing.data.api.QingVpsApiService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * 选购页 / 仪表盘的数据层。
 *
 * v1.2.0：
 *  - 商品只拉一次全量（站点忽略分类参数），分类由 ViewModel 用 CatalogParser.byGroup 本地过滤；
 *  - 自定义配置 / 周期来自 /reidcsmart_common/product/{id}/configoption；
 *  - 加购真实提交 POST /cart，购物车以 GET /cart 的云端数据为准。
 * 全部走 safeApiCall，业务 status!=200 统一转 ApiResult.Failure。
 */
class CatalogRepository(private val api: QingVpsApiService) {

    /** 商品配置缓存：购物车解析要把 configoption 的 id 翻成中文名，按 productId 缓存。 */
    private val configCache = ConcurrentHashMap<String, ProductConfig>()

    fun cachedConfig(productId: String): ProductConfig? = configCache[productId]

    suspend fun firstGroups(): ApiResult<List<ProductGroup>> =
        safeApiCall({ api.firstGroups() }) { CatalogParser.firstGroups(it) }

    suspend fun secondGroups(firstId: String): ApiResult<List<ProductGroup>> =
        safeApiCall({ api.secondGroups(firstId) }) { CatalogParser.secondGroups(it) }

    /** 商品分页大小：站点 /product 默认 limit=20 会漏商品，必须显式传大值。 */
    private val productPageSize = 100
    private val productMaxPages = 20

    /**
     * 全量商品（分类在本地过滤）。
     *
     * v1.4.3 修正分页判定：实测 `data.count` 恒等于「默认每页条数 20」，
     * 并不是商品总数（limit=100 一次返回 44 条但 count 仍是 20）。
     * 旧版用 `collected.size >= count` 当终止条件：小 limit 时会提前停在 20 条、
     * 大 limit 时又会因第一页就超过 20 而立刻停止翻页，两种情况都可能漏商品。
     * 现在以「本页返回条数小于 limit」判定最后一页，并按 id 去重防止翻页重叠。
     */
    suspend fun allProducts(): ApiResult<List<ProductInfo>> {
        val collected = mutableListOf<JsonElement>()
        val seenIds = mutableSetOf<String>()
        var page = 1
        while (page <= productMaxPages) {
            val r = safeApiCall({ api.allProducts(page, productPageSize) }) { it }
            val arr = when (r) {
                is ApiResult.Success -> r.data.arrayAt("list")
                is ApiResult.Failure -> return ApiResult.Failure(r.code, r.msg, r.data)
                is ApiResult.Error -> return ApiResult.Error(r.throwable)
            } ?: break
            arr.forEach { el ->
                val id = el.string("id")
                if (id == null || seenIds.add(id)) collected.add(el)
            }
            // 本页没填满 = 已经是最后一页；空页同样终止
            if (arr.size() < productPageSize) break
            page++
        }
        if (collected.isEmpty()) return ApiResult.Success(emptyList())
        val wrapper = JsonObject().apply {
            add("list", com.google.gson.JsonArray().apply { collected.forEach { add(it) } })
        }
        return ApiResult.Success(CatalogParser.products(wrapper))
    }

    /** 兼容旧签名：仍请求站点，但返回的是全量，由调用方决定如何过滤。 */
    suspend fun products(fpgId: String, spgId: String): ApiResult<List<ProductInfo>> =
        safeApiCall({ api.products(fpgId, spgId) }) { CatalogParser.products(it) }

    /**
     * 商品配置定义（两级降级）：
     *  1. 通用商品：GET /reidcsmart_common/product/{id}/configoption 成功即用；
     *  2. mf_cloud 套餐型（如「美国大宽带」）：通用接口返回 400，改拉
     *     order_page + image + line 详情做深度适配（v1.2.2 起，不再丢给网页）；
     *  3. 两者都失败才降级 requiresWebOrder=true 引导网页选购。
     */
    suspend fun productConfig(productId: String, forceRefresh: Boolean = false): ApiResult<ProductConfig> {
        if (!forceRefresh) cachedConfig(productId)?.let { return ApiResult.Success(it) }
        if (forceRefresh) configCache.remove(productId)
        return safeApiCall({ api.productConfig(productId) }) { data ->
            ConfigParser.parse(productId, data)
        }.let { result ->
            when (result) {
                is ApiResult.Success -> {
                    configCache[productId] = result.data
                    result
                }
                // 通用接口 400：尝试 mf_cloud 深度适配
                is ApiResult.Failure -> when (val mf = loadMfConfig(productId)) {
                    is ApiResult.Success -> {
                        configCache[productId] = mf.data
                        ApiResult.Success(mf.data)
                    }
                    else -> {
                        val fallback = ProductConfig(
                            productId = productId,
                            requiresWebOrder = true,
                            webOrderPath = "cart/goods.htm?id=$productId"
                        )
                        configCache.putIfAbsent(productId, fallback)
                        ApiResult.Success(fallback)
                    }
                }
                is ApiResult.Error -> cachedConfig(productId)?.let { ApiResult.Success(it) } ?: result
            }
        }
    }

    /** 拉取并组装 mf_cloud 配置：order_page → image → 每条线路的 bw/flow 档位。 */
    private suspend fun loadMfConfig(productId: String): ApiResult<ProductConfig> {
        val page = safeApiCall({ api.packageOrderPage(productId) }) { data ->
            MfParser.parseOrderPage(productId, data)
        }
        val base = when (page) {
            is ApiResult.Success -> page.data
            else -> return ApiResult.Failure(-1, "mf_cloud order_page unavailable")
        }
        // order_page 能解析但没有任何可选维度，视为不支持深度适配
        if (base.areas.isEmpty()) {
            return ApiResult.Failure(-1, "no mf_cloud options")
        }
        val images = safeApiCall({ api.mfImages(productId) }) { MfParser.parseImages(it) }
        val withImages = when (images) {
            is ApiResult.Success -> base.copy(imageGroups = images.data)
            else -> base
        }
        // 逐条线路补 bw / flow 档位（站点前端同样先 order_page 再取线路详情）
        var cfg = withImages
        cfg.areas.map { it.lineId }.distinct().forEach { lineId ->
            val detail = safeApiCall({ api.mfLineDetail(productId, lineId) }) { it }
            if (detail is ApiResult.Success) {
                cfg = cfg.copy(areas = MfParser.mergeLineDetail(cfg.areas, lineId, detail.data))
            }
        }
        return ApiResult.Success(ProductConfig(productId = productId, mf = cfg))
    }

    /** 拉取 mf_cloud 周期（默认配置档位）并缓存进 ProductConfig.cycles，供购物车翻译价格。 */
    suspend fun ensureMfCycles(productId: String, sel: MfSelection) {
        val cached = configCache[productId] ?: return
        if (cached.mf == null || cached.cycles.isNotEmpty()) return
        when (val r = mfDuration(productId, sel)) {
            is ApiResult.Success -> configCache[productId] = cached.copy(cycles = r.data)
            else -> Unit
        }
    }

    /** mf_cloud 周期：POST /product/{id}/remf_cloud/duration。 */
    suspend fun mfDuration(productId: String, sel: MfSelection): ApiResult<List<CycleOption>> {
        val networkType = configCache[productId]?.mf?.networkType ?: "normal"
        return safeApiCall({ api.mfDuration(productId, MfCloudCart.durationBody(sel, productId, networkType)) }) { data ->
            data.takeIf { it?.isJsonArray == true }?.asJsonArray?.mapNotNull { el ->
                val id = el.string("id") ?: return@mapNotNull null
                CycleOption(
                    id = id,
                    name = el.string("name").orEmpty(),
                    amount = el.number("price") ?: 0.0,
                    isDefault = el.number("is_default")?.toInt() == 1
                )
            }.orEmpty()
        }
    }

    /** 实时算价（配置面板展示当前配置的价格）。 */
    suspend fun calcPrice(productId: String, body: JsonObject): ApiResult<PriceQuote> =
        safeApiCall({ api.calcPrice(productId, body) }) { ConfigParser.price(it) }

    /**
     * v1.4.3：按当前配置对所有付费周期并发算价。
     * custom_cycles[].cycle_amount 是站点标价（未含折扣 / 首购优惠），实测月付标价
     * 13.33 而算价返回 10.00、年付标价 133.32 而算价返回 100.00，
     * 所以只有逐周期调用算价接口才能拿到「配置对应」的真实价格。
     * 单个周期失败不影响其它周期（失败项由 UI 回退展示标价并明确标注）。
     */
    suspend fun calcCycleQuotes(
        productId: String,
        bodies: Map<String, JsonObject>
    ): Map<String, PriceQuote> = coroutineScope {
        val calls = bodies.mapValues { (_, body) -> async { calcPrice(productId, body) } }
        calls.mapNotNull { (cycleId, deferred) ->
            (deferred.await() as? ApiResult.Success)?.data?.let { cycleId to it }
        }.toMap()
    }

    /** 商品详情指标（GET /product/{id}），用于配置页展示计费方式与起价。 */
    suspend fun productIndicators(productId: String): ApiResult<ProductInfo?> =
        safeApiCall({ api.productDetail(productId) }) { CatalogParser.productDetail(it) }

    /** 真实加入站点购物车。 */
    suspend fun addToCart(body: JsonObject): ApiResult<Unit> =
        safeApiCall({ api.addToCart(body) }) { Unit }

    // ---- 原生结算支付（v1.4.8）----

    /** 支付方式列表。 */
    suspend fun gateways(): ApiResult<List<GatewayOption>> =
        safeApiCall({ api.gateways() }) { CheckoutParser.gateways(it) }

    /** 结算购物车：返回订单 id 与金额。 */
    suspend fun settleCart(positions: List<Int>): ApiResult<SettleResult> =
        safeApiCall({ api.settleCart(CheckoutBodies.settle(positions)) }) { data ->
            CheckoutParser.settle(data) ?: throw IllegalStateException("结算响应缺少 order_id")
        }

    /** 发起支付：余额支付 paid=true；网关支付返回跳转链接。 */
    suspend fun payOrder(orderId: String, gateway: String): ApiResult<PayOutcome> =
        safeApiCall({ api.payOrder(CheckoutBodies.pay(orderId, gateway)) }) {
            CheckoutParser.payOutcome(it)
        }

    /** 支付状态轮询："PAID" / "UNPAID"。 */
    suspend fun payStatus(orderId: String): ApiResult<String> =
        safeApiCall({ api.payStatus(orderId) }) { CheckoutParser.payStatus(it) }

    /**
     * 拉取站点云端购物车。先把列表里每个商品的配置定义补齐（用于把 configoption 的
     * id 翻成中文名），再解析。站点购物车无会话时返回空列表，属正常状态。
     * 补配置失败不影响展示（会退化成显示原始 id）。
     */
    suspend fun cloudCart(): ApiResult<List<CartItem>> =
        safeApiCall({ api.cart() }) { data ->
            val ids = data.arrayAt("list")
                ?.mapNotNull { it.string("product_id") }
                ?.distinct()
                .orEmpty()
            ids.forEach { id ->
                productConfig(id)
                // mf_cloud 商品补周期缓存（默认档位），让购物车能翻译周期名与单价
                configCache[id]?.mf?.let { mf ->
                    MfSelection.default(mf)?.let { ensureMfCycles(id, it) }
                }
            }
            CloudCartParser.parse(data) { configCache[it] }
        }

    suspend fun updateCartQty(position: Int, qty: Int): ApiResult<Unit> =
        safeApiCall({ api.updateCartQty(position, CartBodies.qty(qty)) }) { Unit }

    suspend fun deleteCartLine(position: Int): ApiResult<Unit> =
        safeApiCall({ api.deleteCartLine(position) }) { Unit }

    suspend fun news(): ApiResult<List<NewsItem>> =
        safeApiCall({ api.news() }) { CatalogParser.news(it) }

    /** 公告详情（匿名可用，v1.4.10）：原生公告内容页数据源。 */
    suspend fun newsDetail(id: String): ApiResult<NewsDetail?> =
        safeApiCall({ api.newsDetail(id) }) { CatalogParser.newsDetail(it) }

    suspend fun menu(): ApiResult<List<MenuEntry>> =
        safeApiCall({ api.menu() }) { CatalogParser.menu(it) }

    /** 需登录态；匿名时站点返回 status=401，由 UI 降级为「登录后查看」。 */
    suspend fun hosts(): ApiResult<List<HostSummary>> =
        safeApiCall({ api.hosts() }) { CatalogParser.hosts(it) }
}
