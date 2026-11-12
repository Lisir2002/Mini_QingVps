package com.vps.qing.data.repository

import com.vps.qing.core.network.ApiResult
import com.vps.qing.core.network.safeApiCall
import com.vps.qing.data.api.QingVpsApiService

/** 选购页 / 仪表盘的数据层。全部走 safeApiCall，业务 status!=200 统一转 ApiResult.Failure。 */
class CatalogRepository(private val api: QingVpsApiService) {

    suspend fun firstGroups(): ApiResult<List<ProductGroup>> =
        safeApiCall({ api.firstGroups() }) { CatalogParser.firstGroups(it) }

    suspend fun secondGroups(firstId: String): ApiResult<List<ProductGroup>> =
        safeApiCall({ api.secondGroups(firstId) }) { CatalogParser.secondGroups(it) }

    suspend fun products(fpgId: String, spgId: String): ApiResult<List<ProductInfo>> =
        safeApiCall({ api.products(fpgId, spgId) }) { CatalogParser.products(it) }

    suspend fun news(): ApiResult<List<NewsItem>> =
        safeApiCall({ api.news() }) { CatalogParser.news(it) }

    suspend fun menu(): ApiResult<List<MenuEntry>> =
        safeApiCall({ api.menu() }) { CatalogParser.menu(it) }

    /** 需登录态；匿名时站点返回 status=401，由 UI 降级为「登录后查看」。 */
    suspend fun hosts(): ApiResult<List<HostSummary>> =
        safeApiCall({ api.hosts() }) { CatalogParser.hosts(it) }
}
