package com.vps.qing.data.repository

import java.util.Locale

/**
 * 应用内购物车条目。第一版用本地状态保存（站点 POST /cart 需要登录态且加购协议未实测），
 * 结算统一跳站点 /cart/shoppingCar.htm 的 WebView 兜底页。
 *
 * [specs] 是商品 description 里解析出来的真实配置项，加入后原样带进购物车，
 * 因此「添加的服务器及其配置」可以在购物车 Tab 直接看到。
 */
data class CartItem(
    val productId: String,
    val productName: String,
    val specs: List<String> = emptyList(),
    val cycle: String = "",
    val unitPrice: Double = 0.0,
    val quantity: Int = 1,
    val groupName: String = ""
) {
    /** 同商品 + 同周期视为同一条目，配置项不参与 key（同一商品配置固定）。 */
    val key: String get() = "$productId|$cycle"
    val subtotal: Double get() = unitPrice * quantity
}

/** 纯 JVM 的购物车运算，便于本地单元测试（ViewModel 只持有 List<CartItem>）。 */
object CartCalculator {
    fun add(items: List<CartItem>, item: CartItem): List<CartItem> {
        if (item.quantity <= 0) return items
        val index = items.indexOfFirst { it.key == item.key }
        if (index < 0) return items + item
        return items.toMutableList().also { mutable ->
            val old = mutable[index]
            mutable[index] = old.copy(quantity = old.quantity + item.quantity)
        }
    }

    /** 数量减到 0 时自动移除该条目。 */
    fun changeQty(items: List<CartItem>, key: String, delta: Int): List<CartItem> =
        items.mapNotNull { item ->
            if (item.key != key) item
            else item.copy(quantity = item.quantity + delta).takeIf { it.quantity > 0 }
        }

    fun remove(items: List<CartItem>, key: String): List<CartItem> =
        items.filterNot { it.key == key }

    fun count(items: List<CartItem>): Int = items.sumOf { it.quantity }

    fun total(items: List<CartItem>): Double = items.sumOf { it.subtotal }

    fun money(amount: Double): String = String.format(Locale.CHINA, "￥%.2f", amount)
}
