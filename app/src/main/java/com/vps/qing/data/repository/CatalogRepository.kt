package com.vps.qing.data.repository

import com.google.gson.JsonObject
import com.vps.qing.core.network.ApiResult
import com.vps.qing.core.network.arrayAt
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

    /** 全量商品（分类在本地过滤）。 */
    suspend fun allProducts(): ApiResult<List<ProductInfo>> =
        safeApiCall({ api.allProducts() }) { CatalogParser.products(it) }

    /** 兼容旧签名：仍请求站点，但返回的是全量，由调用方决定如何过滤。 */
    suspend fun products(fpgId: String, spgId: String): ApiResult<List<ProductInfo>> =
        safeApiCall({ api.products(fpgId, spgId) }) { CatalogParser.products(it) }

    /**
     * 商品配置定义。站点对 mf_cloud 套餐型商品返回 status=400，
     * 这里转成 requiresWebOrder=true 的配置，让 UI 降级为「站点选购」。
     */
    suspend fun productConfig(productId: String): ApiResult<ProductConfig> {
        return safeApiCall({ api.productConfig(productId) }) { data ->
            ConfigParser.parse(productId, data)
        }.let { result ->
            when (result) {
                is ApiResult.Success -> {
                    configCache[productId] = result.data
                    result
                }
                // 站点对 mf_cloud 套餐型商品返回 400「商品未关联通用产品模块接口」，
                // 降级成「站点选购」配置，让 UI 引导到网页订购页。
                is ApiResult.Failure -> {
                    val fallback = ProductConfig(
                        productId = productId,
                        requiresWebOrder = true,
                        webOrderPath = "cart/goods.htm?id=$productId"
                    )
                    configCache.putIfAbsent(productId, fallback)
                    ApiResult.Success(fallback)
                }
                is ApiResult.Error -> cachedConfig(productId)?.let { ApiResult.Success(it) } ?: result
            }
        }
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
            ids.filter { configCache[it] == null }.forEach { productConfig(it) }
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
