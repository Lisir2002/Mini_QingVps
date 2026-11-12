package com.vps.qing.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CartCalculatorTest {

    private fun item(id: String = "45", cycle: String = "月付", qty: Int = 1) = CartItem(
        productId = id,
        productName = "APAC | 亚太-体验版",
        specs = listOf("月流量：50GiB", "带宽：15Mbps"),
        cycle = cycle,
        unitPrice = 1.0,
        quantity = qty
    )

    @Test
    fun `add appends new item and keeps specs`() {
        val cart = CartCalculator.add(emptyList(), item())
        assertEquals(1, cart.size)
        assertEquals(2, cart[0].specs.size)
        assertEquals("月流量：50GiB", cart[0].specs[0])
    }

    @Test
    fun `add merges same product and cycle`() {
        var cart = CartCalculator.add(emptyList(), item(qty = 2))
        cart = CartCalculator.add(cart, item(qty = 3))
        assertEquals(1, cart.size)
        assertEquals(5, cart[0].quantity)
    }

    @Test
    fun `add keeps different cycle as separate line`() {
        var cart = CartCalculator.add(emptyList(), item(cycle = "月付"))
        cart = CartCalculator.add(cart, item(cycle = "年付"))
        assertEquals(2, cart.size)
    }

    @Test
    fun `add ignores non positive quantity`() {
        val cart = CartCalculator.add(emptyList(), item(qty = 0))
        assertTrue(cart.isEmpty())
    }

    @Test
    fun `changeQty removes line when drops to zero`() {
        var cart = CartCalculator.add(emptyList(), item(qty = 1))
        cart = CartCalculator.changeQty(cart, cart[0].key, -1)
        assertTrue(cart.isEmpty())
    }

    @Test
    fun `remove and clear work`() {
        var cart = CartCalculator.add(emptyList(), item())
        cart = CartCalculator.add(cart, item(id = "46"))
        assertEquals(2, cart.size)
        cart = CartCalculator.remove(cart, item().key)
        assertEquals(1, cart.size)
        assertEquals("46", cart[0].productId)
    }

    @Test
    fun `count total subtotal and money formatting`() {
        var cart = CartCalculator.add(emptyList(), item(qty = 2))
        cart = CartCalculator.add(cart, item(id = "46", qty = 1).copy(unitPrice = 12.5))
        assertEquals(3, CartCalculator.count(cart))
        assertEquals(14.5, CartCalculator.total(cart), 0.0001)
        assertEquals(2.0, cart[0].subtotal, 0.0001)
        assertEquals("￥14.50", CartCalculator.money(CartCalculator.total(cart)))
    }
}
