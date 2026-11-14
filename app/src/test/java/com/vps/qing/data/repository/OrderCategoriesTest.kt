package com.vps.qing.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4.14：订单 / 交易记录分类对齐站点 finance.js 字典。
 *
 * 最重要的回归：每个展示出来的分类胶囊都必须能筛出对应记录，且胶囊上的计数
 * 与筛选结果数量一致（用户明确要求「确保分类筛选有效」）。
 */
class OrderCategoriesTest {

    private fun order(id: String, type: String, status: String) = OrderLine(
        id = id,
        amount = "10.00",
        status = status,
        gateway = "支付宝",
        createTime = "2026-11-01 10:00:00",
        payTime = "",
        hostName = "",
        productNames = emptyList(),
        description = "",
        type = type
    )

    private fun txn(id: String, type: String, gateway: String) = TxnLine(
        id = id,
        amount = "10.00",
        gateway = gateway,
        number = "NO$id",
        orderId = id,
        createTime = "2026-11-01 10:00:00",
        type = type
    )

    private val orders = listOf(
        order("1", "new", "Unpaid"),
        order("2", "new", "Paid"),
        order("3", "renew", "Paid"),
        order("4", "upgrade", "Refunded"),
        order("5", "weird_type", "Paid") // 站点字典未收录的新类型
    )

    private val txns = listOf(
        txn("1", "new", "支付宝"),
        txn("2", "renew", "微信支付"),
        txn("3", "new", "支付宝"),
        txn("4", "balance", "余额支付")
    )

    @Test
    fun `订单类型字典对齐网站`() {
        assertEquals("新订单", OrderCategories.typeText("new"))
        assertEquals("续费订单", OrderCategories.typeText("renew"))
        assertEquals("还款订单", OrderCategories.typeText("credit_limit"))
        assertEquals("weird", OrderCategories.typeText("weird"))
        assertEquals("--", OrderCategories.typeText(""))
    }

    @Test
    fun `订单状态字典与未支付判定`() {
        assertEquals("未付款", OrderCategories.statusText("Unpaid"))
        assertEquals("已付款", OrderCategories.statusText("Paid"))
        assertEquals("已退款", OrderCategories.statusText("Refunded"))
        assertEquals("custom", OrderCategories.statusText("custom"))
        assertTrue(OrderCategories.isUnpaid("unpaid"))
        assertFalse(OrderCategories.isUnpaid("Paid"))
    }

    @Test
    fun `订单分类只显示有数据的项且按网站字典顺序`() {
        val opts = OrderCategories.orderOptions(orders)
        assertEquals(null, opts.first().first)
        assertEquals("全部 5", opts.first().second)
        assertEquals(
            listOf(
                "type:new", "type:renew", "type:upgrade", "type:weird_type",
                "status:Unpaid", "status:Paid", "status:Refunded"
            ),
            opts.mapNotNull { it.first }
        )
    }

    @Test
    fun `每个订单分类都能筛出对应记录且计数一致`() {
        OrderCategories.orderOptions(orders).forEach { (key, label) ->
            val shown = OrderCategories.filterOrders(orders, key)
            assertTrue("订单分类 $key 筛选结果为空", shown.isNotEmpty())
            val count = label.substringAfterLast(' ').toInt()
            assertEquals("订单分类 $key 计数与筛选结果不一致", count, shown.size)
        }
    }

    @Test
    fun `订单筛选按类型与状态精确命中且忽略大小写`() {
        assertEquals(2, OrderCategories.filterOrders(orders, "type:new").size)
        assertEquals(1, OrderCategories.filterOrders(orders, "status:Unpaid").size)
        assertEquals(3, OrderCategories.filterOrders(orders, "status:Paid").size)
        assertEquals(2, OrderCategories.filterOrders(orders, "type:NEW").size)
        assertEquals(orders, OrderCategories.filterOrders(orders, null))
        // 未知筛选键不误杀数据
        assertEquals(orders, OrderCategories.filterOrders(orders, "unknown:xx"))
    }

    @Test
    fun `交易分类包含网站订单类型与支付渠道`() {
        val opts = OrderCategories.txnOptions(txns)
        assertEquals("全部 4", opts.first().second)
        val keys = opts.mapNotNull { it.first }
        assertTrue(keys.contains("type:new"))
        assertTrue(keys.contains("type:renew"))
        assertTrue(keys.contains("type:balance"))
        assertTrue(keys.contains("gw:支付宝"))
        assertTrue(keys.contains("gw:微信支付"))
        assertTrue(keys.contains("gw:余额支付"))
    }

    @Test
    fun `每个交易分类都能筛出对应记录且计数一致`() {
        OrderCategories.txnOptions(txns).forEach { (key, label) ->
            val shown = OrderCategories.filterTxns(txns, key)
            assertTrue("交易分类 $key 筛选结果为空", shown.isNotEmpty())
            val count = label.substringAfterLast(' ').toInt()
            assertEquals("交易分类 $key 计数与筛选结果不一致", count, shown.size)
        }
    }

    @Test
    fun `交易筛选按类型与渠道精确命中`() {
        assertEquals(2, OrderCategories.filterTxns(txns, "type:new").size)
        assertEquals(2, OrderCategories.filterTxns(txns, "gw:支付宝").size)
        assertEquals(1, OrderCategories.filterTxns(txns, "gw:微信支付").size)
        assertEquals(txns, OrderCategories.filterTxns(txns, null))
        assertEquals(txns, OrderCategories.filterTxns(txns, "bad:xx"))
    }

    @Test
    fun `空数据只保留全部且为空`() {
        val opts = OrderCategories.orderOptions(emptyList())
        assertEquals(1, opts.size)
        assertEquals("全部 0", opts[0].second)
        assertEquals(1, OrderCategories.txnOptions(emptyList()).size)
        assertTrue(OrderCategories.filterOrders(emptyList(), "type:new").isEmpty())
    }
}
