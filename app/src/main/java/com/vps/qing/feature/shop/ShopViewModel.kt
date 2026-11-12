package com.vps.qing.feature.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vps.qing.core.network.ApiResult
import com.vps.qing.data.repository.CartCalculator
import com.vps.qing.data.repository.CartItem
import com.vps.qing.data.repository.CatalogRepository
import com.vps.qing.data.repository.ProductGroup
import com.vps.qing.data.repository.ProductInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShopUiState(
    /** 0 = 采购页，1 = 购物车。 */
    val tab: Int = 0,
    val groupsLoading: Boolean = true,
    val firstGroups: List<ProductGroup> = emptyList(),
    /** 一级分组 id → 该分组下的二级分类，按需加载并缓存。 */
    val secondGroups: Map<String, List<ProductGroup>> = emptyMap(),
    val selectedFirstId: String? = null,
    val selectedSecondId: String? = null,
    val productsLoading: Boolean = false,
    val products: List<ProductInfo> = emptyList(),
    val error: String? = null,
    val cart: List<CartItem> = emptyList()
) {
    val cartCount: Int get() = CartCalculator.count(cart)
    val cartTotal: Double get() = CartCalculator.total(cart)
    val currentSeconds: List<ProductGroup>
        get() = selectedFirstId?.let { secondGroups[it] } ?: emptyList()
}

/**
 * 选购页状态。分类与商品来自站点真实接口，购物车是应用内本地状态。
 * ViewModel 挂在 MAIN 这个 NavBackStackEntry 上，切底栏 Tab 不会重建，购物车不会丢。
 */
class ShopViewModel(private val catalog: CatalogRepository) : ViewModel() {

    private val _ui = MutableStateFlow(ShopUiState())
    val ui: StateFlow<ShopUiState> = _ui.asStateFlow()

    init {
        loadGroups()
    }

    fun loadGroups() {
        viewModelScope.launch {
            _ui.update { it.copy(groupsLoading = true, error = null) }
            when (val r = catalog.firstGroups()) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(groupsLoading = false, firstGroups = r.data) }
                    r.data.firstOrNull()?.let { selectFirst(it.id) }
                }
                is ApiResult.Failure -> _ui.update {
                    it.copy(groupsLoading = false, error = "[${r.code}] ${r.msg}")
                }
                is ApiResult.Error -> _ui.update {
                    it.copy(groupsLoading = false, error = r.throwable.message ?: "分类加载失败")
                }
            }
        }
    }

    /** 点左侧一级分类：加载其二级分类，并自动选中第一个展示商品。 */
    fun selectFirst(id: String) {
        _ui.update {
            it.copy(selectedFirstId = id, selectedSecondId = null, products = emptyList(), error = null)
        }
        val cached = _ui.value.secondGroups[id]
        if (cached != null) {
            cached.firstOrNull()?.let { selectSecond(it.id) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(productsLoading = true) }
            when (val r = catalog.secondGroups(id)) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(secondGroups = it.secondGroups + (id to r.data), productsLoading = false) }
                    r.data.firstOrNull()?.let { selectSecond(it.id) }
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

    /** 点左侧二级分类：右侧刷新为该分类的商品卡片。 */
    fun selectSecond(id: String) {
        val first = _ui.value.selectedFirstId ?: return
        _ui.update { it.copy(selectedSecondId = id, error = null) }
        loadProducts(first, id)
    }

    fun refreshProducts() {
        val first = _ui.value.selectedFirstId
        val second = _ui.value.selectedSecondId
        if (first != null && second != null) loadProducts(first, second)
        else loadGroups()
    }

    private fun loadProducts(fpgId: String, spgId: String) {
        viewModelScope.launch {
            _ui.update { it.copy(productsLoading = true, error = null) }
            when (val r = catalog.products(fpgId, spgId)) {
                is ApiResult.Success -> _ui.update {
                    it.copy(productsLoading = false, products = r.data)
                }
                is ApiResult.Failure -> _ui.update {
                    it.copy(productsLoading = false, error = "[${r.code}] ${r.msg}")
                }
                is ApiResult.Error -> _ui.update {
                    it.copy(productsLoading = false, error = r.throwable.message ?: "商品加载失败")
                }
            }
        }
    }

    fun setTab(tab: Int) = _ui.update { it.copy(tab = tab) }

    fun addToCart(product: ProductInfo, quantity: Int) {
        val item = CartItem(
            productId = product.id,
            productName = product.name,
            specs = product.specs,
            cycle = product.cycle,
            unitPrice = product.unitPrice,
            quantity = quantity,
            groupName = product.groupSecondName ?: product.groupFirstName.orEmpty()
        )
        _ui.update { it.copy(cart = CartCalculator.add(it.cart, item)) }
    }

    fun changeQty(key: String, delta: Int) =
        _ui.update { it.copy(cart = CartCalculator.changeQty(it.cart, key, delta)) }

    fun removeFromCart(key: String) =
        _ui.update { it.copy(cart = CartCalculator.remove(it.cart, key)) }

    fun clearCart() = _ui.update { it.copy(cart = emptyList()) }
}
