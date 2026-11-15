package com.vps.qing.feature.promo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vps.qing.core.network.ApiResult
import com.vps.qing.data.repository.CatalogRepository
import com.vps.qing.data.repository.PromoApplyResult
import com.vps.qing.data.repository.PromoCodeItem
import com.vps.qing.data.repository.PromoDiscovered
import com.vps.qing.data.repository.ProductInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 中心页状态。 */
data class PromoCenterState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** 发现的码（按发现顺序）。 */
    val codes: List<PromoDiscovered> = emptyList(),
    /** 手动输入的码。 */
    val input: String = "",
    /** 手动校验结果。 */
    val manualResult: PromoApplyResult? = null,
    val manualApplying: Boolean = false,
    /** spgId → 该专区代表商品（用于 apply 试算的 product_id / amount）。 */
    val zoneProducts: Map<String, ProductInfo> = emptyMap()
) {
    val count: Int get() = codes.size
}

/**
 * 独立优惠码中心 ViewModel。
 * 加载 = 发现全站专区公告里的码 + 全量商品（构建专区代表商品用于校验试算）。
 */
class PromoCenterViewModel(private val catalog: CatalogRepository) : ViewModel() {

    private val _ui = MutableStateFlow(PromoCenterState())
    val ui: StateFlow<PromoCenterState> = _ui

    private var loadJob: Job? = null

    fun load(refresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update { it.copy(loading = !refresh, refreshing = refresh, error = null) }
            val codesDeferred = async { catalog.discoverPromoCodes() }
            val productsDeferred = async { catalog.allProducts() }
            val codesRes = codesDeferred.await()
            val products = when (val pr = productsDeferred.await()) {
                is ApiResult.Success -> pr.data
                else -> emptyList()
            }
            // 每个二级专区取第一个商品作为校验代表
            val zoneProducts = products.filter { !it.groupSecondId.isNullOrBlank() }
                .groupBy { it.groupSecondId!! }
                .mapValues { (_, list) -> list.first() }
            when (codesRes) {
                is ApiResult.Success -> _ui.update {
                    it.copy(
                        loading = false, refreshing = false,
                        codes = codesRes.data.map { PromoDiscovered(it) },
                        zoneProducts = zoneProducts
                    )
                }
                is ApiResult.Failure -> _ui.update {
                    it.copy(loading = false, refreshing = false, error = "[${codesRes.code}] ${codesRes.msg}")
                }
                is ApiResult.Error -> _ui.update {
                    it.copy(loading = false, refreshing = false, error = codesRes.throwable.message ?: "加载失败")
                }
            }
        }
    }

    fun onInput(value: String) = _ui.update { it.copy(input = value, manualResult = null) }

    /** 对发现的某个码做校验试算。 */
    fun verify(item: PromoCodeItem) {
        val product = _ui.value.zoneProducts[item.spgId]
        if (product == null) {
            _ui.update { st -> st.copy(codes = markResult(st.codes, item.code, PromoApplyResult(msg = "未找到该专区商品，无法试算"))) }
            return
        }
        viewModelScope.launch {
            _ui.update { st -> st.copy(codes = st.codes.map { if (it.item.code == item.code) it.copy(applying = true, applyResult = null) else it }) }
            val res = catalog.applyPromoCode(
                scene = "new",
                promoCode = item.code,
                productId = product.id,
                qty = 1,
                amount = product.price.toDoubleOrNull() ?: 0.0,
                billingCycleTime = cycleSecondsOf(product.cycle)
            )
            val result = when (res) {
                is ApiResult.Success -> res.data
                is ApiResult.Failure -> PromoApplyResult(msg = "[${res.code}] ${res.msg}")
                is ApiResult.Error -> PromoApplyResult(msg = res.throwable.message ?: "校验失败")
            }
            _ui.update { st -> st.copy(codes = markResult(st.codes, item.code, result)) }
        }
    }

    /** 对手动输入的码做校验试算（用第一个有商品的专区代表）。 */
    fun verifyManual() {
        val code = _ui.value.input.trim()
        if (code.isBlank()) return
        val (spgId, product) = _ui.value.zoneProducts.entries.firstOrNull { it.value.price.toDoubleOrNull() ?: 0.0 > 0 }
            ?.let { it.key to it.value } ?: ("" to null)
        if (product == null) {
            _ui.update { it.copy(manualResult = PromoApplyResult(msg = "暂无可校验的商品")) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(manualApplying = true, manualResult = null) }
            val res = catalog.applyPromoCode(
                scene = "new", promoCode = code, productId = product.id, qty = 1,
                amount = product.price.toDoubleOrNull() ?: 0.0, billingCycleTime = cycleSecondsOf(product.cycle)
            )
            val result = when (res) {
                is ApiResult.Success -> res.data
                is ApiResult.Failure -> PromoApplyResult(msg = "[${res.code}] ${res.msg}")
                is ApiResult.Error -> PromoApplyResult(msg = res.throwable.message ?: "校验失败")
            }
            _ui.update { it.copy(manualApplying = false, manualResult = result) }
        }
    }

    private fun markResult(list: List<PromoDiscovered>, code: String, result: PromoApplyResult) =
        list.map { if (it.item.code == code) it.copy(applying = false, applyResult = result) else it }

    /** 商品周期文本 → 秒数（供 promo_code/apply 的 billing_cycle_time 用）。 */
    private fun cycleSecondsOf(cycle: String): Int = when {
        cycle.contains("年") -> 31536000
        cycle.contains("季") -> 7776000
        else -> 2592000 // 月付兜底
    }

    init {
        load()
    }
}
