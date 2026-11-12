package com.vps.qing.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.vps.qing.core.network.ApiResult
import com.vps.qing.core.network.arrayAt
import com.vps.qing.core.network.number
import com.vps.qing.core.network.safeApiCall
import com.vps.qing.core.network.string
import com.vps.qing.data.api.QingVpsApiService
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

    /** 商品分页大小：站点 /product 默认 limit=20 会漏商品，必须显式传大值并按 count 兜底。 */
    private val productPageSize = 100
    private val productMaxPages = 10

    /**
     * 全量商品（分类在本地过滤）。
     * v1.2.4 修复根本性 bug：/product 是分页接口（默认 limit=20），站点已有 40+ 商品，
     * 不带 limit 只能拿到前 20 个 —— 上新商品和旧款全部"抓不到"。
     * 现在显式 limit=100 一次取回，并按 data.count 校验、必要时继续翻页。
     */
    suspend fun allProducts(): ApiResult<List<ProductInfo>> {
        val collected = mutableListOf<JsonElement>()
        var page = 1
        while (true) {
            val r = safeApiCall({ api.allProducts(page, productPageSize) }) { it }
            val arr = when (r) {
                is ApiResult.Success -> r.data.arrayAt("list")
                is ApiResult.Failure -> return ApiResult.Failure(r.code, r.msg, r.data)
                is ApiResult.Error -> return ApiResult.Error(r.throwable)
            } ?: break
            if (arr.size() == 0 && page > 1) break
            collected.addAll(arr)
            val total = r.data.number("count")?.toInt() ?: collected.size
            if (collected.size >= total || page >= productMaxPages) break
            page++
        }
        if (collected.isEmpty()) return ApiResult.Success(emptyList())
        val wrapper = JsonObject().apply { add("list", com.google.gson.JsonArray().apply { collected.forEach { add(it) } }) }
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
    suspend fun productConfig(productId: String): ApiResult<ProductConfig> {
        cachedConfig(productId)?.let { return ApiResult.Success(it) }
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
    suspend fun mfDuration(productId: String, sel: MfSelection): ApiResult<List<CycleOption>> =
        safeApiCall({ api.mfDuration(productId, MfCloudCart.durationBody(sel, productId)) }) { data ->
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

    /** 实时算价（配置面板展示当前配置的价格）。 */
    suspend fun calcPrice(productId: String, body: JsonObject): ApiResult<PriceQuote> =
        safeApiCall({ api.calcPrice(productId, body) }) { ConfigParser.price(it) }

    /** 真实加入站点购物车。 */
    suspend fun addToCart(body: JsonObject): ApiResult<Unit> =
        safeApiCall({ api.addToCart(body) }) { Unit }

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

    suspend fun menu(): ApiResult<List<MenuEntry>> =
        safeApiCall({ api.menu() }) { CatalogParser.menu(it) }

    /** 需登录态；匿名时站点返回 status=401，由 UI 降级为「登录后查看」。 */
    suspend fun hosts(): ApiResult<List<HostSummary>> =
        safeApiCall({ api.hosts() }) { CatalogParser.hosts(it) }
}
